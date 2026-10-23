package me.gorani.launchingview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.net.URI

/**
 * Full-screen state for forced updates and terminating notices.
 *
 * The screen replaces the content, ignores the system back gesture, and keeps the
 * app running; the only way forward is the single button that opens [linkUri].
 */
@Composable
internal fun BlockingLaunchView(
  title: String,
  message: String,
  buttonTitle: String,
  linkUri: URI?,
  onButtonTapped: (URI?) -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(enabled = true) {
    // Blocking states are not dismissible, matching the iOS screen that has no back navigation.
  }

  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      WarningBadge()
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
      )
      if (message.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = message,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
      if (linkUri != null) {
        Spacer(modifier = Modifier.height(28.dp))
        Button(
          onClick = { onButtonTapped(linkUri) },
          modifier = Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth(),
        ) {
          Text(text = buttonTitle, style = MaterialTheme.typography.titleMedium)
        }
      }
    }
  }
}

@Composable
private fun WarningBadge() {
  Box(
    modifier = Modifier
      .size(64.dp)
      .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
      .clearAndSetSemantics { },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "!",
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
  }
}
