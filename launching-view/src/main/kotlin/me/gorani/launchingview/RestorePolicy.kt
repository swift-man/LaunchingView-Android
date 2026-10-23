package me.gorani.launchingview

import java.util.UUID

/**
 * Identifies the current process so a restored saved state can tell an Activity re-creation
 * (same process: rotation, locale or dark-mode change) apart from a restore after process death.
 */
internal object ProcessIdentity {
  @Volatile
  var token: String = UUID.randomUUID().toString()
}

/**
 * Decides whether the synthetic `ON_RESUME` delivered right after a restore should fetch again.
 *
 * iOS refetches only on a real foreground return (`scenePhase == .active`). The same process
 * re-creating the Activity is not a foreground return, so the first resume is skipped; a restore
 * after process death behaves like a cold launch and fetches.
 */
internal class ResumeGate(skipFirstResume: Boolean) {
  private var skipNext = skipFirstResume

  fun shouldFetch(): Boolean {
    if (skipNext) {
      skipNext = false
      return false
    }
    return true
  }

  companion object {
    fun forRestore(restoredStatus: Any?, savedProcessToken: String?, currentProcessToken: String): ResumeGate =
      ResumeGate(skipFirstResume = restoredStatus != null && savedProcessToken == currentProcessToken)
  }
}
