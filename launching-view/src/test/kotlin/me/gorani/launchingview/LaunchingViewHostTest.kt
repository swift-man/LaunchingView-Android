package me.gorani.launchingview

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import me.gorani.launchingservice.AppUpdateStatus
import me.gorani.launchingservice.LaunchingServiceClient
import me.gorani.launchingservice.NoticeAlert
import me.gorani.launchingservice.UpdateAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose-layer wiring of `LaunchingHost`: lifecycle driven fetches and saved-state restoration.
 * The reducer itself is covered by [LaunchingReducerTest].
 *
 * [rotate] re-creates the Activity through `ActivityScenario.recreate()`, which sets
 * `isChangingConfigurations` before pausing like a real configuration change. [StateRestorationTester]
 * saves and restores without lifecycle callbacks, which is what a restore after process death or
 * after the Activity was destroyed in the background looks like to the host.
 */
@RunWith(RobolectricTestRunner::class)
class LaunchingViewHostTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val forced = AppUpdateStatus.ForcedUpdateRequired(
    UpdateAlert("Force update", "Please update now", URI("https://example.com/force")),
  )
  private val optional = AppUpdateStatus.OptionalUpdateRequired(
    UpdateAlert("Optional update", "A new version is available", URI("https://example.com/optional")),
  )
  private val notice = AppUpdateStatus.Notice(
    NoticeAlert("Notice", "Please read", terminateOnDismiss = false, doneUri = null),
  )

  @Test
  fun `first display fetches once and shows content for a valid policy`() {
    val service = CountingService { AppUpdateStatus.Valid }

    composeRule.setContent { launchingView(service) }

    composeRule.waitUntil { service.fetchCount.get() == 1 }
    composeRule.onNodeWithText("content").assertIsDisplayed()
    assertEquals(1, service.fetchCount.get())
  }

  @Test
  fun `returning to the foreground fetches again`() {
    val service = CountingService { AppUpdateStatus.Valid }
    composeRule.setContent { launchingView(service) }
    composeRule.waitUntil { service.fetchCount.get() == 1 }

    composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

    composeRule.waitUntil { service.fetchCount.get() == 2 }
    composeRule.onNodeWithText("content").assertIsDisplayed()
  }

  @Test
  fun `rotation keeps the blocking screen without fetching again`() {
    val service = CountingService { forced }
    val ui: @Composable () -> Unit = { AsyncLaunchingView(content = { Text("content") }, service = service) }
    composeRule.setContent(ui)
    composeRule.waitUntil { service.fetchCount.get() == 1 }
    composeRule.onNodeWithText("Force update").assertIsDisplayed()

    rotate(ui)

    composeRule.onNodeWithText("Force update").assertIsDisplayed()
    composeRule.onNodeWithText("content").assertDoesNotExist()
    composeRule.waitForIdle()
    assertEquals(1, service.fetchCount.get())
  }

  @Test
  fun `rotation keeps an optional update dialog the user has not dismissed`() {
    val service = CountingService { optional }
    val ui: @Composable () -> Unit = { launchingView(service) }
    composeRule.setContent(ui)
    composeRule.waitUntil { service.fetchCount.get() == 1 }
    composeRule.onNodeWithText("Optional update").assertIsDisplayed()

    rotate(ui)

    composeRule.onNodeWithText("Optional update").assertIsDisplayed()
    composeRule.onNodeWithText("content").assertIsDisplayed()
    composeRule.waitForIdle()
    assertEquals(1, service.fetchCount.get())
  }

  @Test
  fun `rotation does not reshow a dismissed optional update dialog`() {
    val service = CountingService { optional }
    val ui: @Composable () -> Unit = { launchingView(service) }
    composeRule.setContent(ui)
    composeRule.waitUntil { service.fetchCount.get() == 1 }
    composeRule.onNodeWithText("cancel").performClick()
    composeRule.onNodeWithText("Optional update").assertDoesNotExist()

    rotate(ui)

    composeRule.onNodeWithText("content").assertIsDisplayed()
    composeRule.onNodeWithText("Optional update").assertDoesNotExist()
    composeRule.onNodeWithText("launch").assertDoesNotExist()
    composeRule.waitForIdle()
    assertEquals(1, service.fetchCount.get())
  }

  @Test
  fun `rotation does not reshow a dismissed notice dialog`() {
    val service = CountingService { notice }
    val ui: @Composable () -> Unit = { launchingView(service) }
    composeRule.setContent(ui)
    composeRule.waitUntil { service.fetchCount.get() == 1 }
    composeRule.onNodeWithText("done").performClick()

    rotate(ui)

    composeRule.onNodeWithText("content").assertIsDisplayed()
    composeRule.onNodeWithText("Notice").assertDoesNotExist()
    composeRule.waitForIdle()
    assertEquals(1, service.fetchCount.get())
  }

  @Test
  fun `a foreground return that re-creates the activity fetches and applies the new policy`() {
    var status: AppUpdateStatus = AppUpdateStatus.Valid
    val service = CountingService { status }
    val restorationTester = StateRestorationTester(composeRule)
    restorationTester.setContent { launchingView(service) }
    composeRule.waitUntil { service.fetchCount.get() == 1 }

    composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED) // user leaves
    status = forced // a forced update is published while the user is away
    restorationTester.emulateSavedInstanceStateRestore() // locale or dark mode changed: re-created on return
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED) // user returns

    composeRule.waitUntil { service.fetchCount.get() == 2 }
    composeRule.onNodeWithText("Force update").assertIsDisplayed()
    composeRule.onNodeWithText("content").assertDoesNotExist()
  }

  @Test
  fun `rotation during an in-flight refetch fetches again`() {
    val gate = CompletableDeferred<AppUpdateStatus>()
    val service = CountingService { count -> if (count == 2) gate.await() else if (count == 1) AppUpdateStatus.Valid else forced }
    val ui: @Composable () -> Unit = { launchingView(service) }
    composeRule.setContent(ui)
    composeRule.waitUntil { service.fetchCount.get() == 1 }
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    composeRule.waitUntil { service.fetchCount.get() == 2 } // foreground refetch in flight

    rotate(ui)

    composeRule.waitUntil { service.fetchCount.get() == 3 }
    composeRule.onNodeWithText("Force update").assertIsDisplayed()
  }

  @Test
  fun `rotation while the fetch error dialog is shown fetches again instead of restoring the dialog`() {
    val service = CountingService { count -> if (count == 1) throw IllegalStateException("offline") else AppUpdateStatus.Valid }
    val ui: @Composable () -> Unit = { launchingView(service) }
    composeRule.setContent(ui)
    composeRule.waitUntil { service.fetchCount.get() == 1 }
    composeRule.onNodeWithText("offline").assertIsDisplayed()

    rotate(ui)

    composeRule.waitUntil { service.fetchCount.get() == 2 }
    composeRule.onNodeWithText("offline").assertDoesNotExist()
    composeRule.onNodeWithText("content").assertIsDisplayed()
  }

  @Test
  fun `rotation after being re-created in the background does not reshow the dismissed dialog`() {
    val service = CountingService { optional }
    val ui: @Composable () -> Unit = { launchingView(service) }
    composeRule.setContent(ui)
    composeRule.waitUntil { service.fetchCount.get() == 1 }

    // Destroyed while the user is away (memory pressure, "Don't keep activities"): the pause that
    // preceded it was not a configuration change, so the return fetches like a cold launch.
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
    composeRule.activityRule.scenario.recreate()
    composeRule.activityRule.scenario.onActivity { activity -> activity.setContent(content = ui) }
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    composeRule.waitUntil { service.fetchCount.get() == 2 }
    composeRule.onNodeWithText("cancel").performClick()
    composeRule.onNodeWithText("Optional update").assertDoesNotExist()

    rotate(ui) // plain rotation afterwards

    composeRule.onNodeWithText("content").assertIsDisplayed()
    composeRule.onNodeWithText("Optional update").assertDoesNotExist()
    composeRule.waitForIdle()
    assertEquals(2, service.fetchCount.get())
  }

  // Formats an earlier release wrote under the same key with AppUpdateStatusSaver. rememberSaveable
  // wraps the saver in a MutableState, so a null from the inner restore would surface as a null
  // snapshot instead of falling back to the initial value.
  @Test
  fun `an empty saved value from an earlier version starts fresh instead of crashing`() {
    assertStartsFreshFrom(emptyList())
  }

  @Test
  fun `a five-entry saved policy from an earlier version starts fresh instead of crashing`() {
    assertStartsFreshFrom(listOf("forced", "Force update", "Please update now", "https://example.com/force", false))
  }

  private fun assertStartsFreshFrom(stale: List<Any>) {
    val service = CountingService { AppUpdateStatus.Valid }
    val registry = StaleSaveableStateRegistry(stale)

    composeRule.setContent {
      CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { launchingView(service) }
    }

    composeRule.waitUntil { service.fetchCount.get() == 1 }
    composeRule.onNodeWithText("content").assertIsDisplayed()
    assertTrue("the host must consume the stale value", registry.consumedKeys.isNotEmpty())
  }

  @Composable
  private fun launchingView(service: LaunchingServiceClient) {
    LaunchingView(content = { Text("content") }, launchScreen = { Text("launch") }, service = service)
  }

  /** Re-creates the Activity like a configuration change and sets the same content on the new instance. */
  private fun rotate(ui: @Composable () -> Unit) {
    composeRule.activityRule.scenario.recreate()
    composeRule.activityRule.scenario.onActivity { activity -> activity.setContent(content = ui) }
    composeRule.waitForIdle()
  }

  private class CountingService(private val status: suspend (count: Int) -> AppUpdateStatus) : LaunchingServiceClient {
    val fetchCount = AtomicInteger()

    override suspend fun fetchAppUpdateStatus(): AppUpdateStatus = status(fetchCount.incrementAndGet())
  }

  /** Answers every restore with [stale], wrapped the way `rememberSaveable(stateSaver = ...)` saves it. */
  private class StaleSaveableStateRegistry(private val stale: List<Any>) : SaveableStateRegistry {
    val consumedKeys = mutableListOf<String>()

    override fun canBeSaved(value: Any): Boolean = true

    override fun consumeRestored(key: String): Any? {
      consumedKeys += key
      return mutableStateOf<Any?>(stale)
    }

    override fun registerProvider(key: String, valueProvider: () -> Any?): SaveableStateRegistry.Entry =
      object : SaveableStateRegistry.Entry {
        override fun unregister() = Unit
      }

    override fun performSave(): Map<String, List<Any?>> = emptyMap()
  }
}
