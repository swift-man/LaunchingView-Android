package me.gorani.launchingview

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import me.gorani.launchingservice.AppUpdateStatus
import me.gorani.launchingservice.LaunchingServiceClient
import me.gorani.launchingservice.NoticeAlert
import me.gorani.launchingservice.UpdateAlert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose-layer wiring of `LaunchingHost`: lifecycle driven fetches and saved-state restoration.
 * The reducer itself is covered by [LaunchingReducerTest].
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

    composeRule.setContent { LaunchingView(content = { Text("content") }, launchScreen = { Text("launch") }, service = service) }

    composeRule.waitUntil { service.fetchCount.get() == 1 }
    composeRule.onNodeWithText("content").assertIsDisplayed()
    assertEquals(1, service.fetchCount.get())
  }

  @Test
  fun `returning to the foreground fetches again`() {
    val service = CountingService { AppUpdateStatus.Valid }
    composeRule.setContent { LaunchingView(content = { Text("content") }, launchScreen = { Text("launch") }, service = service) }
    composeRule.waitUntil { service.fetchCount.get() == 1 }

    composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

    composeRule.waitUntil { service.fetchCount.get() == 2 }
    composeRule.onNodeWithText("content").assertIsDisplayed()
  }

  @Test
  fun `re-creation in the same process keeps the blocking screen without fetching again`() {
    val service = CountingService { forced }
    val restorationTester = StateRestorationTester(composeRule)
    restorationTester.setContent { AsyncLaunchingView(content = { Text("content") }, service = service) }
    composeRule.waitUntil { service.fetchCount.get() == 1 }
    composeRule.onNodeWithText("Force update").assertIsDisplayed()

    restorationTester.emulateSavedInstanceStateRestore()

    composeRule.onNodeWithText("Force update").assertIsDisplayed()
    composeRule.onNodeWithText("content").assertDoesNotExist()
    composeRule.waitForIdle()
    assertEquals(1, service.fetchCount.get())
  }

  @Test
  fun `re-creation does not reshow a dismissed optional update dialog`() {
    val service = CountingService { optional }
    val restorationTester = StateRestorationTester(composeRule)
    restorationTester.setContent { LaunchingView(content = { Text("content") }, launchScreen = { Text("launch") }, service = service) }
    composeRule.waitUntil { service.fetchCount.get() == 1 }
    composeRule.onNodeWithText("Optional update").assertIsDisplayed()
    composeRule.onNodeWithText("cancel").performClick()
    composeRule.onNodeWithText("Optional update").assertDoesNotExist()

    restorationTester.emulateSavedInstanceStateRestore()

    composeRule.onNodeWithText("content").assertIsDisplayed()
    composeRule.onNodeWithText("Optional update").assertDoesNotExist()
    composeRule.onNodeWithText("launch").assertDoesNotExist()
    composeRule.waitForIdle()
    assertEquals(1, service.fetchCount.get())
  }

  @Test
  fun `re-creation does not reshow a dismissed notice dialog`() {
    val service = CountingService { notice }
    val restorationTester = StateRestorationTester(composeRule)
    restorationTester.setContent { LaunchingView(content = { Text("content") }, launchScreen = { Text("launch") }, service = service) }
    composeRule.waitUntil { service.fetchCount.get() == 1 }
    composeRule.onNodeWithText("done").performClick()

    restorationTester.emulateSavedInstanceStateRestore()

    composeRule.onNodeWithText("content").assertIsDisplayed()
    composeRule.onNodeWithText("Notice").assertDoesNotExist()
    composeRule.waitForIdle()
    assertEquals(1, service.fetchCount.get())
  }

  @Test
  fun `restore after process death keeps the policy and fetches like a cold launch`() {
    val service = CountingService { forced }
    val restorationTester = StateRestorationTester(composeRule)
    restorationTester.setContent { AsyncLaunchingView(content = { Text("content") }, service = service) }
    composeRule.waitUntil { service.fetchCount.get() == 1 }

    ProcessIdentity.token = "next-process"
    restorationTester.emulateSavedInstanceStateRestore()

    composeRule.onNodeWithText("Force update").assertIsDisplayed()
    composeRule.onNodeWithText("content").assertDoesNotExist()
    composeRule.waitUntil { service.fetchCount.get() == 2 }
  }

  private class CountingService(private val status: () -> AppUpdateStatus) : LaunchingServiceClient {
    val fetchCount = AtomicInteger()

    override suspend fun fetchAppUpdateStatus(): AppUpdateStatus {
      fetchCount.incrementAndGet()
      return status()
    }
  }
}
