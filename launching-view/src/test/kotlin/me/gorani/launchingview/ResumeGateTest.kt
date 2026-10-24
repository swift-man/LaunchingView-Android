package me.gorani.launchingview

import me.gorani.launchingservice.AppUpdateStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeGateTest {
  @Test
  fun `fresh start fetches on every resume`() {
    val gate = ResumeGate.forRestore(LaunchingSnapshot())

    assertTrue(gate.shouldFetch())
    assertTrue(gate.shouldFetch())
  }

  @Test
  fun `re-creation of a foreground activity skips only the first resume`() {
    val gate = ResumeGate.forRestore(
      LaunchingSnapshot(status = AppUpdateStatus.Valid, changingConfigurations = true),
    )

    assertFalse(gate.shouldFetch())
    assertTrue(gate.shouldFetch())
    assertTrue(gate.shouldFetch())
  }

  @Test
  fun `re-creation of a stopped activity or after process death fetches`() {
    val gate = ResumeGate.forRestore(
      LaunchingSnapshot(status = AppUpdateStatus.Valid, changingConfigurations = false),
    )

    assertTrue(gate.shouldFetch())
  }

  @Test
  fun `re-creation that cancelled a running queued or failed fetch fetches again`() {
    val gate = ResumeGate.forRestore(
      LaunchingSnapshot(status = AppUpdateStatus.Valid, refetchNeeded = true, changingConfigurations = true),
    )

    assertTrue(gate.shouldFetch())
  }

  @Test
  fun `re-creation before any policy arrived fetches`() {
    val gate = ResumeGate.forRestore(LaunchingSnapshot(status = null, changingConfigurations = true))

    assertTrue(gate.shouldFetch())
  }
}
