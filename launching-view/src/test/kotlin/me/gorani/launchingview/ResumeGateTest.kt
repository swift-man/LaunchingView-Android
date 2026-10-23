package me.gorani.launchingview

import me.gorani.launchingservice.AppUpdateStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeGateTest {
  @Test
  fun `fresh start fetches on every resume`() {
    val gate = ResumeGate.forRestore(restoredStatus = null, savedProcessToken = "a", currentProcessToken = "a")

    assertTrue(gate.shouldFetch())
    assertTrue(gate.shouldFetch())
  }

  @Test
  fun `re-creation in the same process skips only the first resume`() {
    val gate = ResumeGate.forRestore(AppUpdateStatus.Valid, savedProcessToken = "a", currentProcessToken = "a")

    assertFalse(gate.shouldFetch())
    assertTrue(gate.shouldFetch())
    assertTrue(gate.shouldFetch())
  }

  @Test
  fun `restore after process death fetches like a cold launch`() {
    val gate = ResumeGate.forRestore(AppUpdateStatus.Valid, savedProcessToken = "old", currentProcessToken = "new")

    assertTrue(gate.shouldFetch())
  }

  @Test
  fun `saved token without a saved status fetches`() {
    val gate = ResumeGate.forRestore(restoredStatus = null, savedProcessToken = "a", currentProcessToken = "a")

    assertTrue(gate.shouldFetch())
  }
}
