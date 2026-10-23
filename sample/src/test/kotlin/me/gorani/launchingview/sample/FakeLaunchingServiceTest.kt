package me.gorani.launchingview.sample

import kotlinx.coroutines.test.runTest
import me.gorani.launchingservice.AppUpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeLaunchingServiceTest {
  @Test
  fun `each scenario maps to its launch status`() = runTest {
    var scenario = LaunchScenario.VALID
    val service = FakeLaunchingService({ scenario }, latencyMillis = 0L)

    assertEquals(AppUpdateStatus.Valid, service.fetchAppUpdateStatus())

    scenario = LaunchScenario.FORCED_UPDATE
    assertTrue(service.fetchAppUpdateStatus() is AppUpdateStatus.ForcedUpdateRequired)

    scenario = LaunchScenario.TERMINATING_NOTICE
    val notice = service.fetchAppUpdateStatus() as AppUpdateStatus.Notice
    assertTrue(notice.alert.terminateOnDismiss)
  }

  @Test
  fun `fetch error scenario throws once and then recovers`() = runTest {
    val service = FakeLaunchingService({ LaunchScenario.FETCH_ERROR }, latencyMillis = 0L)

    try {
      service.fetchAppUpdateStatus()
      fail("Expected IllegalStateException")
    } catch (_: IllegalStateException) {
      // Expected.
    }

    assertEquals(AppUpdateStatus.Valid, service.fetchAppUpdateStatus())
  }

  @Test
  fun `reset arms the fetch error scenario again`() = runTest {
    val service = FakeLaunchingService({ LaunchScenario.FETCH_ERROR }, latencyMillis = 0L)
    runCatching { service.fetchAppUpdateStatus() }
    assertEquals(AppUpdateStatus.Valid, service.fetchAppUpdateStatus())

    service.reset()

    try {
      service.fetchAppUpdateStatus()
      fail("Expected IllegalStateException")
    } catch (_: IllegalStateException) {
      // Expected.
    }
  }
}
