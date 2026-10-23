package me.gorani.launchingview

import java.net.URI
import me.gorani.launchingservice.AppUpdateStatus

/**
 * Pure launch state mirrored from the iOS `Launching.State` reducer state.
 */
internal data class LaunchingState(
  val appUpdateStatus: AppUpdateStatus? = null,
  val isFetching: Boolean = false,
  val hasPendingFetch: Boolean = false,
  val displayContentView: Boolean = false,
  val fetchErrorAlert: FetchErrorAlert? = null,
  val optionalUpdateAlert: OptionalUpdateAlert? = null,
  val noticeAlert: NoticeDialogAlert? = null,
  val blockingAlert: BlockingAlert? = null,
) {
  internal data class FetchErrorAlert(
    val title: String,
    val message: String,
    val done: String,
  )

  internal data class OptionalUpdateAlert(
    val title: String,
    val message: String,
    val cancel: String,
    val done: String,
    val doneUri: URI,
  )

  internal data class NoticeDialogAlert(
    val title: String,
    val message: String,
    val done: String,
  )

  internal data class BlockingAlert(
    val title: String,
    val message: String,
    val buttonTitle: String,
    val linkUri: URI?,
  )
}

internal sealed interface LaunchingAction {
  /** `fetchAppUpdateStatus` on iOS: sent on display and on every foreground return. */
  data object FetchRequested : LaunchingAction

  /** `fetchAppUpdateStatusCancelled` on iOS. */
  data object FetchCancelled : LaunchingAction

  /** `setAppUpdateStatus` on iOS. */
  data class StatusReceived(val status: AppUpdateStatus) : LaunchingAction

  /** `showFetchErrorAlert` on iOS. */
  data class FetchFailed(val message: String) : LaunchingAction

  /** `appUpdateFetchErrorAlert(.dismiss)` on iOS. */
  data object FetchErrorDismissed : LaunchingAction

  /** `optionalUpdateAlert(.dismiss)` on iOS. */
  data object OptionalUpdateCancelled : LaunchingAction

  /** `optionalUpdateAlert(.presented(.doneTapped))` on iOS. */
  data class OptionalUpdateDoneTapped(val doneUri: URI?) : LaunchingAction

  /** `noticeAlert(.dismiss)` on iOS. */
  data object NoticeDismissed : LaunchingAction

  /** `blockingAlertButtonTapped` on iOS. */
  data class BlockingButtonTapped(val linkUri: URI?) : LaunchingAction
}

internal sealed interface LaunchingEffect {
  data object Fetch : LaunchingEffect

  data class OpenUri(val uri: URI) : LaunchingEffect
}

internal data class LaunchingTransition(
  val state: LaunchingState,
  val effects: List<LaunchingEffect> = emptyList(),
)
