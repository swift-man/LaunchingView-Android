package me.gorani.launchingview

import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.gorani.launchingservice.AppUpdateStatus
import me.gorani.launchingservice.LaunchingServiceClient
import me.gorani.launchingservice.UpdateAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchingStoreTest {
  private val reducer = LaunchingReducer(LaunchingAlertDefaultText(), "Sample App")

  @Test
  fun `successful fetch applies the received status`() = runTest {
    val store = store(LaunchingServiceClient { AppUpdateStatus.Valid })

    store.send(LaunchingAction.FetchRequested)
    advanceUntilIdle()

    assertEquals(
      LaunchingState(appUpdateStatus = AppUpdateStatus.Valid, displayContentView = true),
      store.state.value,
    )
  }

  @Test
  fun `failed fetch shows the error alert`() = runTest {
    val store = store(LaunchingServiceClient { error("Firebase is not configured") })

    store.send(LaunchingAction.FetchRequested)
    advanceUntilIdle()

    assertEquals(
      LaunchingState.FetchErrorAlert("Sample App", "Firebase is not configured", "done"),
      store.state.value.fetchErrorAlert,
    )
  }

  @Test
  fun `cancelled scope clears fetching without an alert`() = runTest {
    val scope = CoroutineScope(coroutineContext + Job())
    val store = LaunchingStore(
      reducer = reducer,
      scope = scope,
      service = { LaunchingServiceClient { awaitCancellation() } },
      openUri = {},
    )

    store.send(LaunchingAction.FetchRequested)
    runCurrent()
    assertTrue(store.state.value.isFetching)

    scope.cancel()
    advanceUntilIdle()

    assertEquals(LaunchingState(), store.state.value)
  }

  @Test
  fun `cancellation thrown by the service while the scope is active is a failed fetch`() = runTest {
    val store = store(LaunchingServiceClient { throw CancellationException("Firebase task cancelled") })

    store.send(LaunchingAction.FetchRequested)
    advanceUntilIdle()

    assertEquals(
      LaunchingState.FetchErrorAlert("Sample App", "Firebase task cancelled", "done"),
      store.state.value.fetchErrorAlert,
    )
    assertFalse(store.state.value.isFetching)
  }

  @Test
  fun `changing default text keeps the fetched policy and applies to the next fetch`() = runTest {
    var defaultText = LaunchingAlertDefaultText(forceUpdate = LaunchingAlertDefaultText.ForceUpdate(done = "Update"))
    val forced = AppUpdateStatus.ForcedUpdateRequired(UpdateAlert("", "", URI("https://example.com/force")))
    val store = LaunchingStore(
      reducer = LaunchingReducer({ defaultText }, { "Sample App" }),
      scope = this,
      service = { LaunchingServiceClient { forced } },
      openUri = {},
    )

    store.send(LaunchingAction.FetchRequested)
    advanceUntilIdle()
    defaultText = LaunchingAlertDefaultText(forceUpdate = LaunchingAlertDefaultText.ForceUpdate(done = "업데이트"))

    assertEquals("Update", store.state.value.blockingAlert?.buttonTitle)
    assertFalse(store.state.value.displayContentView)

    store.send(LaunchingAction.FetchRequested)
    advanceUntilIdle()

    assertEquals("업데이트", store.state.value.blockingAlert?.buttonTitle)
    assertEquals(forced, store.state.value.appUpdateStatus)
  }

  @Test
  fun `fetch requested during a fetch runs once more after it finishes`() = runTest {
    val gate = CompletableDeferred<AppUpdateStatus>()
    var fetchCount = 0
    val store = store(
      LaunchingServiceClient {
        fetchCount += 1
        if (fetchCount == 1) gate.await() else AppUpdateStatus.Valid
      },
    )

    store.send(LaunchingAction.FetchRequested)
    store.send(LaunchingAction.FetchRequested)
    gate.complete(AppUpdateStatus.ForcedUpdateRequired(UpdateAlert("", "", URI("https://example.com"))))
    advanceUntilIdle()

    assertEquals(2, fetchCount)
    assertEquals(AppUpdateStatus.Valid, store.state.value.appUpdateStatus)
    assertTrue(store.state.value.displayContentView)
  }

  @Test
  fun `restored forced status keeps the blocking screen while the refetch runs`() = runTest {
    val forced = AppUpdateStatus.ForcedUpdateRequired(UpdateAlert("Force", "Now", URI("https://example.com/force")))
    val gate = CompletableDeferred<AppUpdateStatus>()
    val restored = reducer.reduce(LaunchingState(), LaunchingAction.StatusReceived(forced)).state
    val store = LaunchingStore(
      reducer = reducer,
      scope = this,
      service = { LaunchingServiceClient { gate.await() } },
      openUri = {},
      initialState = restored,
    )

    assertFalse(store.state.value.displayContentView)
    store.send(LaunchingAction.FetchRequested)
    runCurrent()

    assertTrue(store.state.value.isFetching)
    assertEquals(restored.blockingAlert, store.state.value.blockingAlert)
    assertFalse(store.state.value.displayContentView)

    gate.complete(AppUpdateStatus.Valid)
    advanceUntilIdle()

    assertTrue(store.state.value.displayContentView)
    assertEquals(null, store.state.value.blockingAlert)
  }

  @Test
  fun `state listener is invoked synchronously after every transition`() = runTest {
    val seen = mutableListOf<LaunchingState>()
    val store = LaunchingStore(
      reducer = reducer,
      scope = this,
      service = { LaunchingServiceClient { AppUpdateStatus.Valid } },
      openUri = {},
      onStateChanged = seen::add,
    )

    store.send(LaunchingAction.FetchRequested)
    assertEquals(listOf(LaunchingState(isFetching = true)), seen)

    advanceUntilIdle()
    assertEquals(
      LaunchingState(appUpdateStatus = AppUpdateStatus.Valid, displayContentView = true),
      seen.last(),
    )
  }

  @Test
  fun `open uri effects reach the opener`() = runTest {
    val opened = mutableListOf<URI>()
    val uri = URI("https://example.com/force")
    val store = store(LaunchingServiceClient { AppUpdateStatus.Valid }, opened::add)

    store.send(LaunchingAction.BlockingButtonTapped(uri))

    assertEquals(listOf(uri), opened)
  }

  private fun kotlinx.coroutines.test.TestScope.store(
    service: LaunchingServiceClient,
    openUri: (URI) -> Unit = {},
  ) = LaunchingStore(
    reducer = reducer,
    scope = this,
    service = { service },
    openUri = openUri,
  )
}
