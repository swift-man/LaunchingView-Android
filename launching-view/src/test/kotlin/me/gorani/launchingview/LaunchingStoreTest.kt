package me.gorani.launchingview

import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.gorani.launchingservice.AppUpdateStatus
import me.gorani.launchingservice.LaunchingServiceClient
import me.gorani.launchingservice.UpdateAlert
import org.junit.Assert.assertEquals
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
  fun `cancelled fetch clears fetching without an alert`() = runTest {
    val store = store(LaunchingServiceClient { throw CancellationException("cancelled") })

    store.send(LaunchingAction.FetchRequested)
    advanceUntilIdle()

    assertEquals(LaunchingState(), store.state.value)
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
