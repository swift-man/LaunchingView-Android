package me.gorani.launchingview

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import java.net.URI
import me.gorani.launchingservice.AppUpdateStatus
import me.gorani.launchingservice.NoticeAlert
import me.gorani.launchingservice.UpdateAlert

/**
 * Keeps the last fetched launch policy across Activity re-creation (rotation, locale or dark-mode
 * changes) so a blocked user is not shown the content, and a valid user is not shown the launch
 * screen again, while the `ON_RESUME` refetch is still running.
 */
internal val AppUpdateStatusSaver: Saver<AppUpdateStatus?, Any> = listSaver(
  save = { status -> status?.toSavedValues() ?: emptyList() },
  restore = { values -> restoreAppUpdateStatus(values) },
)

internal fun AppUpdateStatus.toSavedValues(): List<Any> = when (this) {
  AppUpdateStatus.Valid -> listOf(KIND_VALID)
  is AppUpdateStatus.ForcedUpdateRequired ->
    listOf(KIND_FORCED, alert.title, alert.message, alert.doneUri.toString(), false)
  is AppUpdateStatus.OptionalUpdateRequired ->
    listOf(KIND_OPTIONAL, alert.title, alert.message, alert.doneUri.toString(), false)
  is AppUpdateStatus.Notice ->
    listOf(KIND_NOTICE, alert.title, alert.message, alert.doneUri?.toString() ?: "", alert.terminateOnDismiss)
}

internal fun restoreAppUpdateStatus(values: List<Any?>): AppUpdateStatus? {
  val kind = values.getOrNull(0) as? String ?: return null
  if (kind == KIND_VALID) return AppUpdateStatus.Valid

  val title = values.getOrNull(1) as? String ?: return null
  val message = values.getOrNull(2) as? String ?: return null
  val uri = values.getOrNull(3) as? String ?: return null
  val terminateOnDismiss = values.getOrNull(4) as? Boolean ?: return null

  return when (kind) {
    KIND_FORCED -> AppUpdateStatus.ForcedUpdateRequired(UpdateAlert(title, message, parseUri(uri) ?: return null))
    KIND_OPTIONAL -> AppUpdateStatus.OptionalUpdateRequired(UpdateAlert(title, message, parseUri(uri) ?: return null))
    KIND_NOTICE -> AppUpdateStatus.Notice(NoticeAlert(title, message, terminateOnDismiss, parseUri(uri)))
    else -> null
  }
}

private fun parseUri(value: String): URI? =
  if (value.isEmpty()) null else runCatching { URI(value) }.getOrNull()

private const val KIND_VALID = "valid"
private const val KIND_FORCED = "forced"
private const val KIND_OPTIONAL = "optional"
private const val KIND_NOTICE = "notice"
