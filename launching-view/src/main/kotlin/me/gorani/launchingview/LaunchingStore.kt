package me.gorani.launchingview

import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.gorani.launchingservice.LaunchingServiceClient

/**
 * Runs [LaunchingReducer] transitions and executes the returned effects.
 *
 * [send] must be called from a single thread (the main thread in Compose).
 * [initialState] lets a host restore an already fetched policy, see [AppUpdateStatusSaver].
 */
internal class LaunchingStore(
  private val reducer: LaunchingReducer,
  private val scope: CoroutineScope,
  private val service: () -> LaunchingServiceClient,
  private val openUri: (URI) -> Unit,
  initialState: LaunchingState = LaunchingState(),
) {
  private val mutableState = MutableStateFlow(initialState)
  val state: StateFlow<LaunchingState> = mutableState.asStateFlow()

  fun send(action: LaunchingAction) {
    val transition = reducer.reduce(mutableState.value, action)
    mutableState.value = transition.state
    transition.effects.forEach(::perform)
  }

  private fun perform(effect: LaunchingEffect) {
    when (effect) {
      LaunchingEffect.Fetch -> scope.launch { fetch() }
      is LaunchingEffect.OpenUri -> openUri(effect.uri)
    }
  }

  private suspend fun fetch() {
    val action = try {
      LaunchingAction.StatusReceived(service().fetchAppUpdateStatus())
    } catch (error: CancellationException) {
      // Only the iOS `fetchAppUpdateStatusCancelled` case (the effect itself was cancelled) is silent.
      // A CancellationException thrown by the service while this coroutine is still active, such as
      // withTimeout or a cancelled Firebase Task, is a failed fetch the user must be able to retry.
      if (currentCoroutineContext().isActive) {
        LaunchingAction.FetchFailed(error.message ?: error.toString())
      } else {
        LaunchingAction.FetchCancelled
      }
    } catch (error: Exception) {
      LaunchingAction.FetchFailed(error.message ?: error.toString())
    }
    send(action)
  }
}
