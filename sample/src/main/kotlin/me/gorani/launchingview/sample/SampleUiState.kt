package me.gorani.launchingview.sample

data class SampleUiState(
  val scenario: LaunchScenario = LaunchScenario.VALID,
  val isFinished: Boolean = true,
)
