package me.gorani.launchingview

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import me.gorani.launchingservice.AppUpdateStatus

/**
 * What `LaunchingView` keeps in saved instance state across Activity re-creation.
 *
 * - [status], [optionalDialogVisible] and [noticeDialogVisible] rebuild the screen the user was
 *   looking at: a blocked user never sees the content, a valid user never sees the launch screen
 *   again, and a dialog the user had not dismissed stays while a dismissed one stays dismissed.
 * - [refetchNeeded] records a fetch that was in flight, queued, or had failed when the state was
 *   saved. Re-creation cancels that fetch, so the restored host must fetch again.
 * - [changingConfigurations] is `Activity.isChangingConfigurations` captured at `ON_PAUSE`, the
 *   first event that sees it, before the state is saved on every API level. It is `true` only for a
 *   re-creation that replaces a foreground Activity (rotation, in-app theme change); an Activity
 *   that was stopped in the background, destroyed to save memory, or lost to process death leaves
 *   `false`, so returning to it fetches like a foreground return on iOS.
 *
 * Saved state is written at `onSaveInstanceState`; a policy that arrives later is not persisted.
 */
internal data class LaunchingSnapshot(
  val status: AppUpdateStatus? = null,
  val optionalDialogVisible: Boolean = false,
  val noticeDialogVisible: Boolean = false,
  val refetchNeeded: Boolean = false,
  val changingConfigurations: Boolean = false,
) {
  fun toSavedValues(): List<Any> =
    status.toSavedValues() + listOf(optionalDialogVisible, noticeDialogVisible, refetchNeeded, changingConfigurations)

  companion object {
    private const val FLAG_COUNT = 4

    fun of(state: LaunchingState, changingConfigurations: Boolean): LaunchingSnapshot = LaunchingSnapshot(
      status = state.appUpdateStatus,
      optionalDialogVisible = state.optionalUpdateAlert != null,
      noticeDialogVisible = state.noticeAlert != null,
      refetchNeeded = state.isFetching || state.hasPendingFetch || state.fetchErrorAlert != null,
      changingConfigurations = changingConfigurations,
    )

    /** Returns `null` for values written by a different version or otherwise malformed. */
    fun fromSavedValues(values: List<Any?>): LaunchingSnapshot? {
      if (values.size != SAVED_STATUS_SIZE + FLAG_COUNT) return null
      val flags = values.subList(SAVED_STATUS_SIZE, values.size).map { it as? Boolean ?: return null }
      return LaunchingSnapshot(
        status = restoreAppUpdateStatus(values.subList(0, SAVED_STATUS_SIZE)),
        optionalDialogVisible = flags[0],
        noticeDialogVisible = flags[1],
        refetchNeeded = flags[2],
        changingConfigurations = flags[3],
      )
    }
  }
}

/**
 * Never restores `null`: `rememberSaveable(stateSaver = ...)` wraps this saver in a `MutableState`
 * and does not fall back to its initial value when the inner restore returns `null`, so values from
 * a different version restore a fresh [LaunchingSnapshot] that fetches like a first display.
 */
internal val LaunchingSnapshotSaver: Saver<LaunchingSnapshot, Any> = listSaver(
  save = { snapshot -> snapshot.toSavedValues() },
  restore = { values -> LaunchingSnapshot.fromSavedValues(values) ?: LaunchingSnapshot() },
)
