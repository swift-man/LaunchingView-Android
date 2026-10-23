package me.gorani.launchingview.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.gorani.launchingservice.LaunchingServiceClient
import me.gorani.launchingview.LaunchingAlertDefaultText
import me.gorani.launchingview.LaunchingView

private val sampleDefaultText = LaunchingAlertDefaultText(
  forceUpdate = LaunchingAlertDefaultText.ForceUpdate(done = "업데이트"),
  optionalUpdate = LaunchingAlertDefaultText.OptionalUpdate(cancel = "나중에", done = "업데이트"),
  notice = LaunchingAlertDefaultText.Notice(done = "확인"),
  fetchError = LaunchingAlertDefaultText.FetchError(done = "다시 시도"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleScreen(
  uiState: SampleUiState,
  launchingService: LaunchingServiceClient,
  onScenarioSelected: (LaunchScenario) -> Unit,
  onFinishedChanged: (Boolean) -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("LaunchingView Sample") },
        actions = {
          ScenarioMenu(
            selected = uiState.scenario,
            isFinished = uiState.isFinished,
            onScenarioSelected = onScenarioSelected,
            onFinishedChanged = onFinishedChanged,
          )
        },
      )
    },
  ) { padding ->
    Box(modifier = Modifier.padding(padding)) {
      // Re-keying restarts the gate so a new scenario is fetched from the launch screen.
      key(uiState.scenario) {
        LaunchingView(
          content = { SampleContent(uiState.scenario) },
          launchScreen = { SampleLaunchScreen(uiState.isFinished, onFinishedChanged) },
          isFinished = uiState.isFinished,
          service = launchingService,
          defaultText = sampleDefaultText,
        )
      }
    }
  }
}

@Composable
private fun ScenarioMenu(
  selected: LaunchScenario,
  isFinished: Boolean,
  onScenarioSelected: (LaunchScenario) -> Unit,
  onFinishedChanged: (Boolean) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    TextButton(onClick = { expanded = true }) { Text(selected.label) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      LaunchScenario.entries.forEach { scenario ->
        DropdownMenuItem(
          text = { Text(scenario.label) },
          onClick = {
            expanded = false
            onScenarioSelected(scenario)
          },
        )
      }
      HorizontalDivider()
      DropdownMenuItem(
        text = { Text(if (isFinished) "초기화 미완료로 전환" else "초기화 완료로 전환") },
        onClick = {
          expanded = false
          onFinishedChanged(!isFinished)
        },
      )
    }
  }
}

@Composable
private fun SampleLaunchScreen(
  isFinished: Boolean,
  onFinishedChanged: (Boolean) -> Unit,
) {
  Surface(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
      CircularProgressIndicator()
      Text("불러오는 중", style = MaterialTheme.typography.bodyMedium)
      if (!isFinished) {
        Button(onClick = { onFinishedChanged(true) }) { Text("초기화 완료") }
      }
    }
  }
}

@Composable
private fun SampleContent(scenario: LaunchScenario) {
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(modifier = Modifier.padding(24.dp)) {
      Text("본문", style = MaterialTheme.typography.titleLarge)
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        "현재 시나리오: ${scenario.label}. 앱을 백그라운드로 보냈다가 돌아오면 정책을 다시 조회합니다.",
        style = MaterialTheme.typography.bodyMedium,
      )
      Spacer(modifier = Modifier.height(24.dp))
      repeat(3) { index ->
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
          Text(
            "콘텐츠 ${index + 1}",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
          )
        }
      }
    }
  }
}
