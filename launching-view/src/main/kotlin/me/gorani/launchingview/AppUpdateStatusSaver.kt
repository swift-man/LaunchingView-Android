package me.gorani.launchingview

import java.net.URI
import me.gorani.launchingservice.AppUpdateStatus
import me.gorani.launchingservice.NoticeAlert
import me.gorani.launchingservice.UpdateAlert

/**
 * Bundle-friendly encoding of an [AppUpdateStatus] used by [LaunchingSnapshotSaver].
 *
 * The result always has [SAVED_STATUS_SIZE] entries (kind, title, message, URI, terminateOnDismiss)
 * so it can be concatenated with other saved values; a `null` status is encoded with an empty kind.
 */
internal fun AppUpdateStatus?.toSavedValues(): List<Any> = when (this) {
  null -> listOf(KIND_NONE, "", "", "", false)
  AppUpdateStatus.Valid -> listOf(KIND_VALID, "", "", "", false)
  is AppUpdateStatus.ForcedUpdateRequired ->
    listOf(KIND_FORCED, alert.title, alert.message, alert.doneUri.toString(), false)
  is AppUpdateStatus.OptionalUpdateRequired ->
    listOf(KIND_OPTIONAL, alert.title, alert.message, alert.doneUri.toString(), false)
  is AppUpdateStatus.Notice ->
    listOf(KIND_NOTICE, alert.title, alert.message, alert.doneUri?.toString() ?: "", alert.terminateOnDismiss)
}

/** Inverse of [toSavedValues]; returns `null` for no status and for unknown or malformed values. */
internal fun restoreAppUpdateStatus(values: List<Any?>): AppUpdateStatus? {
  if (values.size != SAVED_STATUS_SIZE) return null
  val kind = values[0] as? String ?: return null
  val title = values[1] as? String ?: return null
  val message = values[2] as? String ?: return null
  val uri = values[3] as? String ?: return null
  val terminateOnDismiss = values[4] as? Boolean ?: return null

  return when (kind) {
    KIND_VALID -> AppUpdateStatus.Valid
    KIND_FORCED -> AppUpdateStatus.ForcedUpdateRequired(UpdateAlert(title, message, parseUri(uri) ?: return null))
    KIND_OPTIONAL -> AppUpdateStatus.OptionalUpdateRequired(UpdateAlert(title, message, parseUri(uri) ?: return null))
    KIND_NOTICE -> AppUpdateStatus.Notice(NoticeAlert(title, message, terminateOnDismiss, parseUri(uri)))
    else -> null
  }
}

internal const val SAVED_STATUS_SIZE = 5

private fun parseUri(value: String): URI? =
  if (value.isEmpty()) null else runCatching { URI(value) }.getOrNull()

private const val KIND_NONE = ""
private const val KIND_VALID = "valid"
private const val KIND_FORCED = "forced"
private const val KIND_OPTIONAL = "optional"
private const val KIND_NOTICE = "notice"
