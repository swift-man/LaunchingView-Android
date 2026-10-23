package me.gorani.launchingview.sample

import kotlinx.coroutines.delay
import me.gorani.launchingservice.AppUpdateStatus
import me.gorani.launchingservice.LaunchingServiceClient

/** Stands in for `LaunchingService` so the sample runs without Firebase credentials. */
class FakeLaunchingService(
  private val scenario: () -> LaunchScenario,
  private val latencyMillis: Long = 800L,
) : LaunchingServiceClient {
  override suspend fun fetchAppUpdateStatus(): AppUpdateStatus {
    delay(latencyMillis)
    return scenario().toStatus()
  }
}
