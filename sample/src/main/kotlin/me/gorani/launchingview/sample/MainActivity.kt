package me.gorani.launchingview.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
  private val viewModel: SampleViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        SampleScreen(
          uiState = uiState,
          launchingService = viewModel.launchingService,
          onScenarioSelected = viewModel::selectScenario,
          onFinishedChanged = viewModel::setFinished,
        )
      }
    }
  }
}
