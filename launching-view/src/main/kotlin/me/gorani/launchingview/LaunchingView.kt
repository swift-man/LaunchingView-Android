package me.gorani.launchingview

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.gorani.launchingservice.AppUpdateStatus
import me.gorani.launchingservice.LaunchingServiceClient

/**
 * Launch gate that keeps [launchScreen] visible until the launch policy is known, then shows [content].
 *
 * Behavior mirrors the iOS `LaunchingView`:
 * - The policy is fetched through [service] on the first display and on every foreground return.
 *   An Activity re-creation in the same process (rotation, locale or dark-mode change) restores the
 *   last policy without fetching again; a restore after process death fetches like a cold launch.
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
  // The last fetched policy survives Activity re-creation so a blocked user never sees the content
  // and a valid user never sees the launch screen again. The process token tells a re-creation in
  // the same process (no refetch, like iOS which never re-creates) from a restore after process
  // death (fetches like a cold launch).
  var savedStatus by rememberSaveable(stateSaver = AppUpdateStatusSaver) {
    mutableStateOf<AppUpdateStatus?>(null)
  }
  val savedProcessToken = rememberSaveable { ProcessIdentity.token }
  val restoredStatus = remember { savedStatus }
  val resumeGate = remember {
    ResumeGate.forRestore(restoredStatus, savedProcessToken, ProcessIdentity.token)
  }
  // Created once: changing defaultText or service must not reset an already fetched launch policy.
  val store = remember {
    val reducer = LaunchingReducer({ currentDefaultText }, { appName })
    LaunchingStore(
      reducer = reducer,
      scope = scope,
      service = { currentService },
      openUri = { uri -> currentOnOpenUri(Uri.parse(uri.toString())) },
      initialState = restoredStatus?.let(reducer::restore) ?: LaunchingState(),
    )
  }
  val state by store.state.collectAsStateWithLifecycle()
  // Mirrors the store into saved state while composed, independent of the lifecycle-gated `state`.
  // Values that change after onSaveInstanceState are not persisted: after process death the last
  // saved policy is shown until the ON_RESUME refetch corrects it.
  LaunchedEffect(store) {
    store.state.map { it.appUpdateStatus }.distinctUntilChanged().collect { savedStatus = it }
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, store) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME && resumeGate.shouldFetch()) {
        store.send(LaunchingAction.FetchRequested)
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
