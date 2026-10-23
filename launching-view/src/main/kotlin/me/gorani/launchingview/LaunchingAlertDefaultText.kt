package me.gorani.launchingview

/**
 * Fallback alert text used when the Remote Config title or message is empty.
 *
 * Blank titles resolve to the application label (the Android counterpart of
 * `Bundle.main.displayName` on iOS) at display time.
 */
public data class LaunchingAlertDefaultText(
  val forceUpdate: ForceUpdate = ForceUpdate(),
  val optionalUpdate: OptionalUpdate = OptionalUpdate(),
  val notice: Notice = Notice(),
  val fetchError: FetchError = FetchError(),
) {
  /** Text for the blocking forced-update screen. */
  public data class ForceUpdate(
    val title: String = "",
    val message: String = "",
    val done: String = "update",
  )

  /** Text for the optional-update dialog shown above the content. */
  public data class OptionalUpdate(
    val title: String = "",
    val message: String = "",
    val cancel: String = "cancel",
    val done: String = "update",
  )

  /**
   * Text for the notice dialog and the terminating notice screen.
   *
   * Both have a single button, so unlike iOS there is no `cancel` text.
   */
  public data class Notice(
    val title: String = "",
    val message: String = "",
    val done: String = "done",
  )

  /**
   * Text for the dialog shown when `LaunchingServiceClient.fetchAppUpdateStatus` throws.
   *
   * iOS shows the system alert button here; Android has no system default, so the button text is configurable.
   */
  public data class FetchError(
    val title: String = "",
    val done: String = "done",
  )
}
