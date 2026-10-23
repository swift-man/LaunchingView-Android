package me.gorani.launchingview

import java.net.URI
import me.gorani.launchingservice.AppUpdateStatus
import me.gorani.launchingservice.NoticeAlert
import me.gorani.launchingservice.UpdateAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateStatusSaverTest {
  @Test
  fun `every status survives a save and restore round trip`() {
    val statuses = listOf(
      null,
      AppUpdateStatus.Valid,
      AppUpdateStatus.ForcedUpdateRequired(UpdateAlert("Force", "Now", URI("https://example.com/force"))),
      AppUpdateStatus.OptionalUpdateRequired(UpdateAlert("", "", URI("market://details?id=app"))),
      AppUpdateStatus.Notice(NoticeAlert("Notice", "Read", terminateOnDismiss = true, doneUri = URI("https://example.com"))),
      AppUpdateStatus.Notice(NoticeAlert("Notice", "Read", terminateOnDismiss = false, doneUri = null)),
    )

    statuses.forEach { status ->
      assertEquals(SAVED_STATUS_SIZE, status.toSavedValues().size)
      assertEquals(status, restoreAppUpdateStatus(status.toSavedValues()))
    }
  }

  @Test
  fun `saved values use only bundle-friendly types`() {
    val values = AppUpdateStatus.Notice(
      NoticeAlert("Notice", "Read", terminateOnDismiss = true, doneUri = URI("https://example.com")),
    ).toSavedValues()

    values.forEach { value ->
      assertEquals(true, value is String || value is Boolean)
    }
  }

  @Test
  fun `unknown or malformed values restore nothing`() {
    assertNull(restoreAppUpdateStatus(emptyList()))
    assertNull(restoreAppUpdateStatus(listOf("unknown", "t", "m", "", false)))
    assertNull(restoreAppUpdateStatus(listOf("forced", "t", "m", "", false)))
    assertNull(restoreAppUpdateStatus(listOf("forced", "t")))
    assertNull(restoreAppUpdateStatus(listOf("valid", "", "", "", "not-a-boolean")))
  }
}
