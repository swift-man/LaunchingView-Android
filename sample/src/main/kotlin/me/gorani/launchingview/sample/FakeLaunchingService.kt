package me.gorani.launchingview.sample

import kotlinx.coroutines.delay
import me.gorani.launchingservice.AppUpdateStatus
import me.gorani.launchingservice.LaunchingServiceClient

/**
 * Stands in for `LaunchingService` so the sample runs without Firebase credentials.
 *
 * [LaunchScenario.FETCH_ERROR] throws once per [reset] and then succeeds, so the error dialog's
 * retry recovers and the scenario menu never becomes unreachable.
 */
class FakeLaunchingService(
  private val scenario: () -> LaunchScenario,
  private val latencyMillis: Long = 800L,
) : LaunchingServiceClient {
  private var hasFailed = false

  /** Arms the next [LaunchScenario.FETCH_ERROR] fetch to fail again. */
  fun reset() {
    hasFailed = false
  }

  override suspend fun fetchAppUpdateStatus(): AppUpdateStatus {
    delay(latencyMillis)
    val scenario = scenario()
    if (scenario == LaunchScenario.FETCH_ERROR && !hasFailed) {
      hasFailed = true
      throw IllegalStateException("Remote Config 를 불러오지 못했습니다. 다시 시도하면 성공합니다.")
    }
    return scenario.toStatus()
  }
}
