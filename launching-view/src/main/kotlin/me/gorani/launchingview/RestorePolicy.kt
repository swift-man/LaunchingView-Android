package me.gorani.launchingview

/**
 * Decides whether the synthetic `ON_RESUME` delivered right after a restore should fetch again.
 *
 * iOS refetches whenever the scene becomes active and never re-creates the view, so the only
 * resume that must not fetch is the one caused by re-creating a *foreground* Activity (rotation,
 * in-app theme change) while nothing was in flight. Every other resume fetches: a first display,
 * a foreground return, an Activity re-created after being stopped or after process death, and a
 * re-creation that cancelled a running, queued, or failed fetch.
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
    fun forRestore(snapshot: LaunchingSnapshot): ResumeGate = ResumeGate(
      skipFirstResume = snapshot.status != null && snapshot.changingConfigurations && !snapshot.refetchNeeded,
    )
  }
}
