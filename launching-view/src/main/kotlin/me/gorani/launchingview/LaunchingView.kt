package me.gorani.launchingview

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.gorani.launchingservice.LaunchingServiceClient

/**
 * Launch gate that keeps [launchScreen] visible until the launch policy is known, then shows [content].
 *
 * Behavior mirrors the iOS `LaunchingView`:
 * - The policy is fetched through [service] on the first display and on every foreground return.
 *   Re-creating a foreground Activity (rotation, in-app theme change) restores the last policy and
 *   the dialogs that were open without fetching again, unless a fetch was running or had failed.
 *   An Activity re-created after being stopped, destroyed in the background, or after process
 *   death restores the policy and fetches again.
 * - `Valid` shows [content] once [isFinished] is also true; otherwise [launchScreen] stays.
 * - `OptionalUpdateRequired` shows [content] under a cancel/update dialog.
 * - `ForcedUpdateRequired` and terminating `Notice` replace everything with the blocking launch screen.
 * - A non-terminating `Notice` shows [content] under a notice dialog whose button opens `doneUri`.
 * - A thrown fetch shows an error dialog; dismissing it fetches again.
 *
 * @param content The app content shown when the policy allows it.
 * @param launchScreen The screen shown while the policy is unknown or [isFinished] is false.
 * @param isFinished Set to false to keep [launchScreen] until the app's own startup work is done.
 * @param service Supplies `AppUpdateStatus`; pass a stable instance.
 * @param defaultText Text used when Remote Config titles or messages are empty.
 * @param onOpenUri Opens update and notice links; defaults to an `ACTION_VIEW` intent.
 */
@Composable
public fun LaunchingView(
  content: @Composable () -> Unit,
  launchScreen: @Composable () -> Unit,
  isFinished: Boolean = true,
  service: LaunchingServiceClient,
  defaultText: LaunchingAlertDefaultText = LaunchingAlertDefaultText(),
  onOpenUri: (Uri) -> Unit = rememberDefaultUriOpener(),
) {
  LaunchingHost(
    service = service,
    defaultText = defaultText,
    onOpenUri = onOpenUri,
  ) { state, store ->
    val blockingAlert = state.blockingAlert
    when {
      blockingAlert != null -> BlockingLaunchView(
        title = blockingAlert.title,
        message = blockingAlert.message,
        buttonTitle = blockingAlert.buttonTitle,
        linkUri = blockingAlert.linkUri,
        onButtonTapped = { store.send(LaunchingAction.BlockingButtonTapped(it)) },
      )

      state.displayContentView && isFinished -> content()

      else -> launchScreen()
    }
  }
}

/**
 * Launch gate without a launch screen: [content] is shown immediately and only replaced by
 * the blocking launch screen when the policy blocks the app. Dialogs appear above [content].
 *
 * @param content The app content.
 * @param service Supplies `AppUpdateStatus`; pass a stable instance.
 * @param defaultText Text used when Remote Config titles or messages are empty.
 * @param onOpenUri Opens update and notice links; defaults to an `ACTION_VIEW` intent.
 */
@Composable
public fun AsyncLaunchingView(
  content: @Composable () -> Unit,
  service: LaunchingServiceClient,
  defaultText: LaunchingAlertDefaultText = LaunchingAlertDefaultText(),
  onOpenUri: (Uri) -> Unit = rememberDefaultUriOpener(),
) {
  LaunchingHost(
    service = service,
    defaultText = defaultText,
    onOpenUri = onOpenUri,
  ) { state, store ->
    val blockingAlert = state.blockingAlert
    if (blockingAlert != null) {
      BlockingLaunchView(
        title = blockingAlert.title,
        message = blockingAlert.message,
        buttonTitle = blockingAlert.buttonTitle,
        linkUri = blockingAlert.linkUri,
        onButtonTapped = { store.send(LaunchingAction.BlockingButtonTapped(it)) },
      )
    } else {
      content()
    }
  }
}

@Composable
private fun LaunchingHost(
  service: LaunchingServiceClient,
  defaultText: LaunchingAlertDefaultText,
  onOpenUri: (Uri) -> Unit,
  launchContent: @Composable (LaunchingState, LaunchingStore) -> Unit,
) {
  val context = LocalContext.current
  val appName by rememberUpdatedState(remember(context) { context.applicationLabel() })
  val scope = rememberCoroutineScope()
  val currentService by rememberUpdatedState(service)
  val currentDefaultText by rememberUpdatedState(defaultText)
  val currentOnOpenUri by rememberUpdatedState(onOpenUri)
  // The screen the user was looking at survives Activity re-creation (see LaunchingSnapshot). The
  // store writes the snapshot synchronously on every transition, so saved instance state is never
  // behind the store; ON_PAUSE adds whether the re-creation replaces a foreground Activity.
  val snapshotState = rememberSaveable(stateSaver = LaunchingSnapshotSaver) {
    mutableStateOf(LaunchingSnapshot())
  }
  val restored = remember { snapshotState.value }
  val resumeGate = remember { ResumeGate.forRestore(restored) }
  // Created once: changing defaultText or service must not reset an already fetched launch policy.
  val store = remember {
    val reducer = LaunchingReducer({ currentDefaultText }, { appName })
    LaunchingStore(
      reducer = reducer,
      scope = scope,
      service = { currentService },
      openUri = { uri -> currentOnOpenUri(Uri.parse(uri.toString())) },
      initialState = restored.status
        ?.let { reducer.restore(it, restored.optionalDialogVisible, restored.noticeDialogVisible) }
        ?: LaunchingState(),
      onStateChanged = { newState ->
        snapshotState.value = LaunchingSnapshot.of(newState, snapshotState.value.changingConfigurations)
      },
    )
  }
  val state by store.state.collectAsStateWithLifecycle()

  val activity = LocalActivity.current
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, store) {
    // The restored flag has been consumed by the gate; ON_PAUSE sets it again before any save.
    snapshotState.value = snapshotState.value.copy(changingConfigurations = false)
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> if (resumeGate.shouldFetch()) store.send(LaunchingAction.FetchRequested)
        // ON_PAUSE is the first event that sees isChangingConfigurations; androidx dispatches ON_STOP
        // before saving on every API level, so any capture up to ON_STOP lands in the saved state.
        Lifecycle.Event.ON_PAUSE -> snapshotState.value = snapshotState.value.copy(
          changingConfigurations = activity?.isChangingConfigurations == true,
        )
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  launchContent(state, store)

  state.optionalUpdateAlert?.let { alert ->
    OptionalUpdateDialog(
      alert = alert,
      onCancel = { store.send(LaunchingAction.OptionalUpdateCancelled) },
      onDone = { store.send(LaunchingAction.OptionalUpdateDoneTapped(alert.doneUri)) },
    )
  }
  state.noticeAlert?.let { alert ->
    NoticeDialog(alert = alert, onDismiss = { store.send(LaunchingAction.NoticeDismissed) })
  }
  state.fetchErrorAlert?.let { alert ->
    FetchErrorDialog(alert = alert, onDismiss = { store.send(LaunchingAction.FetchErrorDismissed) })
  }
}
