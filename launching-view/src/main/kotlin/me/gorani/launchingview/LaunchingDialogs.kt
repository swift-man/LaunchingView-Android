package me.gorani.launchingview

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

private val modalProperties = DialogProperties(
  dismissOnBackPress = false,
  dismissOnClickOutside = false,
)

@Composable
internal fun OptionalUpdateDialog(
  alert: LaunchingState.OptionalUpdateAlert,
  onCancel: () -> Unit,
  onDone: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onCancel,
    confirmButton = {
      TextButton(onClick = onDone) { Text(alert.done) }
    },
    dismissButton = {
      TextButton(onClick = onCancel) { Text(alert.cancel) }
    },
    title = { Text(alert.title) },
    text = messageText(alert.message),
    properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
  )
}

@Composable
internal fun NoticeDialog(
  alert: LaunchingState.NoticeDialogAlert,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(alert.done) }
    },
    title = { Text(alert.title) },
    text = messageText(alert.message),
    properties = modalProperties,
  )
}

@Composable
internal fun FetchErrorDialog(
  alert: LaunchingState.FetchErrorAlert,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(alert.done) }
    },
    title = { Text(alert.title) },
    text = messageText(alert.message),
    properties = modalProperties,
  )
}

private fun messageText(message: String): (@Composable () -> Unit)? =
  if (message.isEmpty()) null else { { Text(message) } }
