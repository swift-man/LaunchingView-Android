package me.gorani.launchingview

import java.net.URI
import me.gorani.launchingservice.AppUpdateStatus
import me.gorani.launchingview.LaunchingAction.BlockingButtonTapped
import me.gorani.launchingview.LaunchingAction.FetchCancelled
import me.gorani.launchingview.LaunchingAction.FetchErrorDismissed
import me.gorani.launchingview.LaunchingAction.FetchFailed
import me.gorani.launchingview.LaunchingAction.FetchRequested
import me.gorani.launchingview.LaunchingAction.NoticeDismissed
import me.gorani.launchingview.LaunchingAction.OptionalUpdateCancelled
import me.gorani.launchingview.LaunchingAction.OptionalUpdateDoneTapped
import me.gorani.launchingview.LaunchingAction.StatusReceived

/**
 * Pure state machine ported branch by branch from the iOS `Launching` TCA reducer.
 *
 * Effects are returned instead of executed so the transitions can be verified with JVM unit tests.
 * [defaultTextProvider] and [appNameProvider] are read on every action, so the owning store can
 * live across default-text changes without resetting the launch policy it already fetched.
 */
internal class LaunchingReducer(
  private val defaultTextProvider: () -> LaunchingAlertDefaultText,
  private val appNameProvider: () -> String,
) {
  constructor(defaultText: LaunchingAlertDefaultText, appName: String) :
    this({ defaultText }, { appName })

  private val defaultText: LaunchingAlertDefaultText
    get() = defaultTextProvider()

  private val appName: String
    get() = appNameProvider()

  fun reduce(state: LaunchingState, action: LaunchingAction): LaunchingTransition = when (action) {
    FetchRequested -> requestFetch(state)
    FetchCancelled -> finishFetch(state) { it }
    is StatusReceived -> finishFetch(state) { apply(it, action.status) }
    is FetchFailed -> fail(state, action.message)
    FetchErrorDismissed -> requestFetch(state.copy(fetchErrorAlert = null))
    OptionalUpdateCancelled -> LaunchingTransition(state.copy(optionalUpdateAlert = null))
    is OptionalUpdateDoneTapped -> optionalUpdateDone(state, action.doneUri)
    NoticeDismissed -> noticeDismissed(state)
    is BlockingButtonTapped -> LaunchingTransition(state, openUri(action.linkUri))
  }

  /**
   * State to start from when [status] was restored from saved state after Activity re-creation.
   *
   * The blocking screen and content visibility always come back. The optional-update and notice
   * dialogs come back only when they were still visible when the state was saved, so a dialog the
   * user had dismissed stays dismissed and one they had not stays open, as it does on iOS.
   */
  fun restore(status: AppUpdateStatus, optionalDialogVisible: Boolean, noticeDialogVisible: Boolean): LaunchingState {
    val state = apply(LaunchingState(), status)
    return state.copy(
      optionalUpdateAlert = state.optionalUpdateAlert.takeIf { optionalDialogVisible },
      noticeAlert = state.noticeAlert.takeIf { noticeDialogVisible },
    )
  }

  private fun requestFetch(state: LaunchingState): LaunchingTransition =
    if (state.isFetching) {
      LaunchingTransition(state.copy(hasPendingFetch = true))
    } else {
      LaunchingTransition(state.copy(isFetching = true), listOf(LaunchingEffect.Fetch))
    }

  private fun finishFetch(
    state: LaunchingState,
    transform: (LaunchingState) -> LaunchingState,
  ): LaunchingTransition {
    val hasPendingFetch = state.hasPendingFetch
    val next = transform(state.copy(isFetching = false, hasPendingFetch = false))
    return fetchAgainIfNeeded(next, hasPendingFetch)
  }

  private fun apply(state: LaunchingState, status: AppUpdateStatus): LaunchingState {
    val base = state.copy(appUpdateStatus = status, fetchErrorAlert = null)
    return when (status) {
      AppUpdateStatus.Valid -> base.copy(
        displayContentView = true,
        blockingAlert = null,
        optionalUpdateAlert = null,
        noticeAlert = null,
      )

      is AppUpdateStatus.ForcedUpdateRequired -> base.copy(
        displayContentView = false,
        optionalUpdateAlert = null,
        noticeAlert = null,
        blockingAlert = LaunchingState.BlockingAlert(
          title = status.alert.title.ifEmpty { titleOrAppName(defaultText.forceUpdate.title) },
          message = status.alert.message.ifEmpty { defaultText.forceUpdate.message },
          buttonTitle = defaultText.forceUpdate.done,
          linkUri = status.alert.doneUri,
        ),
      )

      is AppUpdateStatus.OptionalUpdateRequired -> base.copy(
        displayContentView = true,
        blockingAlert = null,
        noticeAlert = null,
        optionalUpdateAlert = LaunchingState.OptionalUpdateAlert(
          title = status.alert.title.ifEmpty { titleOrAppName(defaultText.optionalUpdate.title) },
          message = status.alert.message.ifEmpty { defaultText.optionalUpdate.message },
          cancel = defaultText.optionalUpdate.cancel,
          done = defaultText.optionalUpdate.done,
          doneUri = status.alert.doneUri,
        ),
      )

      is AppUpdateStatus.Notice -> {
        val title = status.alert.title.ifEmpty { titleOrAppName(defaultText.notice.title) }
        val message = status.alert.message.ifEmpty { defaultText.notice.message }
        if (status.alert.terminateOnDismiss) {
          base.copy(
            displayContentView = false,
            optionalUpdateAlert = null,
            noticeAlert = null,
            blockingAlert = LaunchingState.BlockingAlert(
              title = title,
              message = message,
              buttonTitle = defaultText.notice.done,
              linkUri = status.alert.doneUri,
            ),
          )
        } else {
          base.copy(
            displayContentView = true,
            optionalUpdateAlert = null,
            blockingAlert = null,
            noticeAlert = LaunchingState.NoticeDialogAlert(
              title = title,
              message = message,
              done = defaultText.notice.done,
            ),
          )
        }
      }
    }
  }

  private fun fail(state: LaunchingState, message: String): LaunchingTransition {
    val hasPendingFetch = state.hasPendingFetch
    val next = state.copy(isFetching = false, hasPendingFetch = false)
    if (hasPendingFetch) return requestFetch(next)

    return LaunchingTransition(
      next.copy(
        optionalUpdateAlert = null,
        noticeAlert = null,
        fetchErrorAlert = LaunchingState.FetchErrorAlert(
          title = titleOrAppName(defaultText.fetchError.title),
          message = message,
          done = defaultText.fetchError.done,
        ),
      ),
    )
  }

  private fun optionalUpdateDone(state: LaunchingState, doneUri: URI?): LaunchingTransition {
    val next = state.copy(optionalUpdateAlert = null)
    val effects = when (state.appUpdateStatus) {
      is AppUpdateStatus.OptionalUpdateRequired -> openUri(doneUri)
      else -> emptyList()
    }
    return LaunchingTransition(next, effects)
  }

  private fun noticeDismissed(state: LaunchingState): LaunchingTransition {
    val next = state.copy(noticeAlert = null)
    val status = state.appUpdateStatus as? AppUpdateStatus.Notice ?: return LaunchingTransition(next)
    if (status.alert.terminateOnDismiss) return LaunchingTransition(next)
    return LaunchingTransition(next, openUri(status.alert.doneUri))
  }

  private fun fetchAgainIfNeeded(state: LaunchingState, hasPendingFetch: Boolean): LaunchingTransition =
    if (hasPendingFetch) requestFetch(state) else LaunchingTransition(state)

  private fun openUri(uri: URI?): List<LaunchingEffect> =
    if (uri == null) emptyList() else listOf(LaunchingEffect.OpenUri(uri))

  private fun titleOrAppName(title: String): String = title.ifBlank { appName }
}
