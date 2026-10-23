package me.gorani.launchingview

import java.net.URI
import me.gorani.launchingservice.AppUpdateStatus
import me.gorani.launchingservice.NoticeAlert
import me.gorani.launchingservice.UpdateAlert
import me.gorani.launchingview.LaunchingAction.BlockingButtonTapped
import me.gorani.launchingview.LaunchingAction.FetchCancelled
import me.gorani.launchingview.LaunchingAction.FetchErrorDismissed
import me.gorani.launchingview.LaunchingAction.FetchFailed
import me.gorani.launchingview.LaunchingAction.FetchRequested
import me.gorani.launchingview.LaunchingAction.NoticeDismissed
import me.gorani.launchingview.LaunchingAction.OptionalUpdateCancelled
import me.gorani.launchingview.LaunchingAction.OptionalUpdateDoneTapped
import me.gorani.launchingview.LaunchingAction.StatusReceived
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchingReducerTest {
  private val appName = "Sample App"
  private val reducer = LaunchingReducer(LaunchingAlertDefaultText(), appName)

  private val forceUri = URI("https://example.com/force")
  private val optionalUri = URI("https://example.com/optional")
  private val noticeUri = URI("https://example.com/notice")

  private val forced = AppUpdateStatus.ForcedUpdateRequired(
    UpdateAlert("Force update", "Please update now", forceUri),
  )
  private val optional = AppUpdateStatus.OptionalUpdateRequired(
    UpdateAlert("Optional update", "A new version is available", optionalUri),
  )
  private val notice = AppUpdateStatus.Notice(
    NoticeAlert("Notice", "Please read", terminateOnDismiss = false, doneUri = noticeUri),
  )
  private val terminatingNotice = AppUpdateStatus.Notice(
    NoticeAlert("Notice", "Please read", terminateOnDismiss = true, doneUri = noticeUri),
  )

  // MARK: fetchAppUpdateStatus

  @Test
  fun `fetch request starts fetching and emits the fetch effect`() {
    val transition = reducer.reduce(LaunchingState(), FetchRequested)

    assertEquals(LaunchingState(isFetching = true), transition.state)
    assertEquals(listOf(LaunchingEffect.Fetch), transition.effects)
  }

  @Test
  fun `fetch request while fetching is queued instead of started twice`() {
    val transition = reducer.reduce(LaunchingState(isFetching = true), FetchRequested)

    assertEquals(LaunchingState(isFetching = true, hasPendingFetch = true), transition.state)
    assertTrue(transition.effects.isEmpty())
  }

  // MARK: fetchAppUpdateStatusCancelled

  @Test
  fun `cancellation clears fetching without showing an alert`() {
    val transition = reducer.reduce(LaunchingState(isFetching = true), FetchCancelled)

    assertEquals(LaunchingState(), transition.state)
    assertTrue(transition.effects.isEmpty())
  }

  @Test
  fun `cancellation with a pending request fetches again`() {
    val transition = reducer.reduce(
      LaunchingState(isFetching = true, hasPendingFetch = true),
      FetchCancelled,
    )

    assertEquals(LaunchingState(isFetching = true), transition.state)
    assertEquals(listOf(LaunchingEffect.Fetch), transition.effects)
  }

  // MARK: setAppUpdateStatus

  @Test
  fun `valid status shows content and clears every alert`() {
    val before = LaunchingState(
      isFetching = true,
      blockingAlert = blockingAlert(),
      optionalUpdateAlert = optionalAlert(),
      noticeAlert = noticeAlert(),
      fetchErrorAlert = errorAlert(),
    )

    val transition = reducer.reduce(before, StatusReceived(AppUpdateStatus.Valid))

    assertEquals(
      LaunchingState(appUpdateStatus = AppUpdateStatus.Valid, displayContentView = true),
      transition.state,
    )
    assertTrue(transition.effects.isEmpty())
  }

  @Test
  fun `forced update hides content and shows the blocking screen`() {
    val before = LaunchingState(
      isFetching = true,
      displayContentView = true,
      optionalUpdateAlert = optionalAlert(),
      noticeAlert = noticeAlert(),
    )

    val transition = reducer.reduce(before, StatusReceived(forced))

    assertEquals(
      LaunchingState(
        appUpdateStatus = forced,
        displayContentView = false,
        blockingAlert = LaunchingState.BlockingAlert(
          title = "Force update",
          message = "Please update now",
          buttonTitle = "update",
          linkUri = forceUri,
        ),
      ),
      transition.state,
    )
  }

  @Test
  fun `forced update with empty remote text falls back to default text and app name`() {
    val reducer = LaunchingReducer(
      LaunchingAlertDefaultText(
        forceUpdate = LaunchingAlertDefaultText.ForceUpdate(message = "Default message", done = "Go"),
      ),
      appName,
    )
    val status = AppUpdateStatus.ForcedUpdateRequired(UpdateAlert("", "", forceUri))

    val transition = reducer.reduce(LaunchingState(), StatusReceived(status))

    assertEquals(
      LaunchingState.BlockingAlert(appName, "Default message", "Go", forceUri),
      transition.state.blockingAlert,
    )
  }

  @Test
  fun `custom default title replaces the app name`() {
    val reducer = LaunchingReducer(
      LaunchingAlertDefaultText(
        forceUpdate = LaunchingAlertDefaultText.ForceUpdate(title = "Update required"),
      ),
      appName,
    )
    val status = AppUpdateStatus.ForcedUpdateRequired(UpdateAlert("", "", forceUri))

    val transition = reducer.reduce(LaunchingState(), StatusReceived(status))

    assertEquals("Update required", transition.state.blockingAlert?.title)
  }

  @Test
  fun `optional update keeps content and shows the optional dialog`() {
    val before = LaunchingState(
      isFetching = true,
      blockingAlert = blockingAlert(),
      noticeAlert = noticeAlert(),
    )

    val transition = reducer.reduce(before, StatusReceived(optional))

    assertEquals(
      LaunchingState(
        appUpdateStatus = optional,
        displayContentView = true,
        optionalUpdateAlert = LaunchingState.OptionalUpdateAlert(
          title = "Optional update",
          message = "A new version is available",
          cancel = "cancel",
          done = "update",
          doneUri = optionalUri,
        ),
      ),
      transition.state,
    )
  }

  @Test
  fun `optional update with empty remote text uses defaults`() {
    val status = AppUpdateStatus.OptionalUpdateRequired(UpdateAlert("", "", optionalUri))

    val transition = reducer.reduce(LaunchingState(), StatusReceived(status))

    assertEquals(appName, transition.state.optionalUpdateAlert?.title)
    assertEquals("", transition.state.optionalUpdateAlert?.message)
  }

  @Test
  fun `non-terminating notice keeps content and shows the notice dialog`() {
    val before = LaunchingState(
      isFetching = true,
      blockingAlert = blockingAlert(),
      optionalUpdateAlert = optionalAlert(),
    )

    val transition = reducer.reduce(before, StatusReceived(notice))

    assertEquals(
      LaunchingState(
        appUpdateStatus = notice,
        displayContentView = true,
        noticeAlert = LaunchingState.NoticeDialogAlert("Notice", "Please read", "done"),
      ),
      transition.state,
    )
  }

  @Test
  fun `terminating notice hides content and shows the blocking screen`() {
    val before = LaunchingState(
      isFetching = true,
      displayContentView = true,
      optionalUpdateAlert = optionalAlert(),
      noticeAlert = noticeAlert(),
    )

    val transition = reducer.reduce(before, StatusReceived(terminatingNotice))

    assertEquals(
      LaunchingState(
        appUpdateStatus = terminatingNotice,
        displayContentView = false,
        blockingAlert = LaunchingState.BlockingAlert("Notice", "Please read", "done", noticeUri),
      ),
      transition.state,
    )
  }

  @Test
  fun `notice with empty remote text uses defaults`() {
    val reducer = LaunchingReducer(
      LaunchingAlertDefaultText(
        notice = LaunchingAlertDefaultText.Notice(title = "Maintenance", message = "Back soon", done = "OK"),
      ),
      appName,
    )
    val status = AppUpdateStatus.Notice(NoticeAlert("", "", terminateOnDismiss = false, doneUri = null))

    val transition = reducer.reduce(LaunchingState(), StatusReceived(status))

    assertEquals(
      LaunchingState.NoticeDialogAlert("Maintenance", "Back soon", "OK"),
      transition.state.noticeAlert,
    )
  }

  @Test
  fun `status clears a visible fetch error alert`() {
    val before = LaunchingState(isFetching = true, fetchErrorAlert = errorAlert())

    val transition = reducer.reduce(before, StatusReceived(forced))

    assertNull(transition.state.fetchErrorAlert)
  }

  @Test
  fun `status received while a fetch is pending fetches again after applying the status`() {
    val before = LaunchingState(isFetching = true, hasPendingFetch = true)

    val transition = reducer.reduce(before, StatusReceived(forced))

    assertTrue(transition.state.isFetching)
    assertFalse(transition.state.hasPendingFetch)
    assertEquals(forced, transition.state.appUpdateStatus)
    assertEquals(listOf(LaunchingEffect.Fetch), transition.effects)
  }

  // MARK: showFetchErrorAlert

  @Test
  fun `fetch failure shows the error alert and clears other dialogs`() {
    val before = LaunchingState(
      isFetching = true,
      displayContentView = true,
      blockingAlert = blockingAlert(),
      optionalUpdateAlert = optionalAlert(),
      noticeAlert = noticeAlert(),
    )

    val transition = reducer.reduce(before, FetchFailed("Network failed"))

    assertEquals(
      LaunchingState(
        displayContentView = true,
        blockingAlert = blockingAlert(),
        fetchErrorAlert = LaunchingState.FetchErrorAlert(appName, "Network failed", "done"),
      ),
      transition.state,
    )
    assertTrue(transition.effects.isEmpty())
  }

  @Test
  fun `fetch failure with a pending request fetches again without an alert`() {
    val before = LaunchingState(isFetching = true, hasPendingFetch = true, optionalUpdateAlert = optionalAlert())

    val transition = reducer.reduce(before, FetchFailed("Network failed"))

    assertEquals(
      LaunchingState(isFetching = true, optionalUpdateAlert = optionalAlert()),
      transition.state,
    )
    assertEquals(listOf(LaunchingEffect.Fetch), transition.effects)
  }

  @Test
  fun `custom fetch error text is used`() {
    val reducer = LaunchingReducer(
      LaunchingAlertDefaultText(fetchError = LaunchingAlertDefaultText.FetchError("Oops", "Retry")),
      appName,
    )

    val transition = reducer.reduce(LaunchingState(isFetching = true), FetchFailed("Network failed"))

    assertEquals(
      LaunchingState.FetchErrorAlert("Oops", "Network failed", "Retry"),
      transition.state.fetchErrorAlert,
    )
  }

  // MARK: appUpdateFetchErrorAlert(.dismiss)

  @Test
  fun `dismissing the error alert fetches again`() {
    val transition = reducer.reduce(LaunchingState(fetchErrorAlert = errorAlert()), FetchErrorDismissed)

    assertEquals(LaunchingState(isFetching = true), transition.state)
    assertEquals(listOf(LaunchingEffect.Fetch), transition.effects)
  }

  @Test
  fun `dismissing the error alert while fetching queues the refetch`() {
    val transition = reducer.reduce(
      LaunchingState(isFetching = true, fetchErrorAlert = errorAlert()),
      FetchErrorDismissed,
    )

    assertEquals(LaunchingState(isFetching = true, hasPendingFetch = true), transition.state)
    assertTrue(transition.effects.isEmpty())
  }

  // MARK: optionalUpdateAlert

  @Test
  fun `optional update cancel closes the dialog without opening a link`() {
    val before = LaunchingState(appUpdateStatus = optional, displayContentView = true, optionalUpdateAlert = optionalAlert())

    val transition = reducer.reduce(before, OptionalUpdateCancelled)

    assertEquals(before.copy(optionalUpdateAlert = null), transition.state)
    assertTrue(transition.effects.isEmpty())
  }

  @Test
  fun `optional update done opens the link from the dialog`() {
    val before = LaunchingState(appUpdateStatus = optional, displayContentView = true, optionalUpdateAlert = optionalAlert())

    val transition = reducer.reduce(before, OptionalUpdateDoneTapped(optionalUri))

    assertEquals(before.copy(optionalUpdateAlert = null), transition.state)
    assertEquals(listOf(LaunchingEffect.OpenUri(optionalUri)), transition.effects)
  }

  @Test
  fun `optional update done ignores the link when the status is no longer optional`() {
    listOf(AppUpdateStatus.Valid, forced, notice, null).forEach { status ->
      val before = LaunchingState(appUpdateStatus = status, optionalUpdateAlert = optionalAlert())

      val transition = reducer.reduce(before, OptionalUpdateDoneTapped(optionalUri))

      assertNull(transition.state.optionalUpdateAlert)
      assertTrue("status=$status", transition.effects.isEmpty())
    }
  }

  @Test
  fun `optional update done without a link opens nothing`() {
    val before = LaunchingState(appUpdateStatus = optional, optionalUpdateAlert = optionalAlert())

    val transition = reducer.reduce(before, OptionalUpdateDoneTapped(null))

    assertTrue(transition.effects.isEmpty())
  }

  // MARK: noticeAlert(.dismiss)

  @Test
  fun `notice dismiss opens the done link`() {
    val before = LaunchingState(appUpdateStatus = notice, displayContentView = true, noticeAlert = noticeAlert())

    val transition = reducer.reduce(before, NoticeDismissed)

    assertEquals(before.copy(noticeAlert = null), transition.state)
    assertEquals(listOf(LaunchingEffect.OpenUri(noticeUri)), transition.effects)
  }

  @Test
  fun `notice dismiss without a done link opens nothing`() {
    val status = AppUpdateStatus.Notice(NoticeAlert("Notice", "Please read", terminateOnDismiss = false, doneUri = null))
    val before = LaunchingState(appUpdateStatus = status, noticeAlert = noticeAlert())

    val transition = reducer.reduce(before, NoticeDismissed)

    assertNull(transition.state.noticeAlert)
    assertTrue(transition.effects.isEmpty())
  }

  @Test
  fun `notice dismiss for a terminating notice opens nothing`() {
    val before = LaunchingState(appUpdateStatus = terminatingNotice, noticeAlert = noticeAlert())

    val transition = reducer.reduce(before, NoticeDismissed)

    assertTrue(transition.effects.isEmpty())
  }

  @Test
  fun `notice dismiss ignores non-notice statuses`() {
    listOf(AppUpdateStatus.Valid, forced, optional, null).forEach { status ->
      val before = LaunchingState(appUpdateStatus = status, noticeAlert = noticeAlert())

      val transition = reducer.reduce(before, NoticeDismissed)

      assertNull(transition.state.noticeAlert)
      assertTrue("status=$status", transition.effects.isEmpty())
    }
  }

  // MARK: blockingAlertButtonTapped

  @Test
  fun `blocking button opens the link and keeps the state`() {
    val before = LaunchingState(appUpdateStatus = forced, blockingAlert = blockingAlert())

    val transition = reducer.reduce(before, BlockingButtonTapped(forceUri))

    assertEquals(before, transition.state)
    assertEquals(listOf(LaunchingEffect.OpenUri(forceUri)), transition.effects)
  }

  @Test
  fun `blocking button without a link opens nothing`() {
    val transition = reducer.reduce(LaunchingState(blockingAlert = blockingAlert()), BlockingButtonTapped(null))

    assertTrue(transition.effects.isEmpty())
  }

  // MARK: Fixtures

  private fun blockingAlert() = LaunchingState.BlockingAlert("Blocked", "Message", "update", forceUri)

  private fun optionalAlert() = LaunchingState.OptionalUpdateAlert("Optional", "Message", "cancel", "update", optionalUri)

  private fun noticeAlert() = LaunchingState.NoticeDialogAlert("Notice", "Message", "done")

  private fun errorAlert() = LaunchingState.FetchErrorAlert(appName, "Network failed", "done")
}
