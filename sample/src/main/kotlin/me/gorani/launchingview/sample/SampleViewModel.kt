package me.gorani.launchingview.sample

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.gorani.launchingservice.LaunchingServiceClient

class SampleViewModel : ViewModel() {
  private val mutableUiState = MutableStateFlow(SampleUiState())
  val uiState: StateFlow<SampleUiState> = mutableUiState.asStateFlow()

  private val fakeLaunchingService = FakeLaunchingService({ mutableUiState.value.scenario })

  /** Stable service instance handed to `LaunchingView`; it reads the current scenario on every fetch. */
  val launchingService: LaunchingServiceClient = fakeLaunchingService

  fun selectScenario(scenario: LaunchScenario) {
    fakeLaunchingService.reset()
    mutableUiState.update { it.copy(scenario = scenario) }
  }

  fun setFinished(isFinished: Boolean) {
    mutableUiState.update { it.copy(isFinished = isFinished) }
  }
}
