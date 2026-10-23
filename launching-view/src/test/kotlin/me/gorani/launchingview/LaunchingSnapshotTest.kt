package me.gorani.launchingview

import java.net.URI
import me.gorani.launchingservice.AppUpdateStatus
import me.gorani.launchingservice.UpdateAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchingSnapshotTest {
  private val optional = AppUpdateStatus.OptionalUpdateRequired(UpdateAlert("Optional", "New", URI("https://example.com/o")))
  private val optionalAlert = LaunchingState.OptionalUpdateAlert("Optional", "New", "cancel", "update", URI("https://example.com/o"))
  private val noticeAlert = LaunchingState.NoticeDialogAlert("Notice", "Read", "done")

  @Test
  fun `snapshot records the policy and which dialogs are open`() {
    val snapshot = LaunchingSnapshot.of(
      LaunchingState(appUpdateStatus = optional, displayContentView = true, optionalUpdateAlert = optionalAlert),
      changingConfigurations = true,
    )

    assertEquals(optional, snapshot.status)
    assertTrue(snapshot.optionalDialogVisible)
    assertFalse(snapshot.noticeDialogVisible)
    assertFalse(snapshot.refetchNeeded)
    assertTrue(snapshot.changingConfigurations)
  }

  @Test
  fun `a dismissed dialog is not recorded as open`() {
    val snapshot = LaunchingSnapshot.of(LaunchingState(appUpdateStatus = optional, displayContentView = true), false)

    assertFalse(snapshot.optionalDialogVisible)
  }

  @Test
  fun `a running queued or failed fetch needs a refetch after restore`() {
    assertTrue(LaunchingSnapshot.of(LaunchingState(isFetching = true), false).refetchNeeded)
    assertTrue(LaunchingSnapshot.of(LaunchingState(isFetching = true, hasPendingFetch = true), false).refetchNeeded)
    assertTrue(
      LaunchingSnapshot.of(
        LaunchingState(fetchErrorAlert = LaunchingState.FetchErrorAlert("App", "failed", "done")),
        false,
      ).refetchNeeded,
    )
    assertFalse(LaunchingSnapshot.of(LaunchingState(appUpdateStatus = AppUpdateStatus.Valid, displayContentView = true), false).refetchNeeded)
  }

  @Test
  fun `snapshot survives a save and restore round trip`() {
    val snapshots = listOf(
      LaunchingSnapshot(),
      LaunchingSnapshot(status = AppUpdateStatus.Valid, changingConfigurations = true),
      LaunchingSnapshot(status = optional, optionalDialogVisible = true, refetchNeeded = true),
      LaunchingSnapshot.of(LaunchingState(appUpdateStatus = optional, noticeAlert = noticeAlert), changingConfigurations = false),
    )

    snapshots.forEach { snapshot ->
      assertEquals(snapshot, LaunchingSnapshot.fromSavedValues(snapshot.toSavedValues()))
      snapshot.toSavedValues().forEach { value -> assertTrue(value is String || value is Boolean) }
    }
  }

  @Test
  fun `values saved by a different version restore nothing`() {
    assertNull(LaunchingSnapshot.fromSavedValues(emptyList()))
    assertNull(LaunchingSnapshot.fromSavedValues(listOf("valid")))
    assertNull(LaunchingSnapshot.fromSavedValues(listOf("valid", "", "", "", false, true, true, true, "x")))
  }
}
