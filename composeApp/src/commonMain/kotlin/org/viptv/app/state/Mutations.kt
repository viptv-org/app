package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import org.viptv.app.connection.LocalConnection
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteFailure

internal class MutationRetry(
    val optimistic: (ApplicationState) -> ApplicationState,
    val revert: (ApplicationState) -> ApplicationState,
    val message: (Exception) -> String?,
    val operation: suspend () -> Unit,
)

/**
 * Runs one write for one target: [optimistic] draws the predicted result in the frame of the press,
 * a press on a target already running is dropped, and a failure puts [revert] back and keeps its message
 * beside the control so [retryMutation] can run the same write again.
 */
internal fun ApplicationController.mutate(
    target: MutationTarget,
    optimistic: (ApplicationState) -> ApplicationState = { it },
    revert: (ApplicationState) -> ApplicationState = { it },
    message: (Exception) -> String? = ::failureMessage,
    ownsControllerJob: Boolean = false,
    operation: suspend () -> Unit,
) {
    if (mutable.value.mutations.isPending(target)) return
    val retry = MutationRetry(optimistic, revert, message, operation)
    mutationRetries[target] = retry
    val profile = mutable.value.selectedProfile
    val remote = connection
    val device = local
    updateState { optimistic(it).copy(mutations = it.mutations.starting(target)) }
    val task = scope.launch(start = CoroutineStart.LAZY) {
        try {
            operation()
            if (ownsMutation(profile, remote, device)) {
                mutationRetries.remove(target)
                updateState { it.copy(mutations = it.mutations.settled(target)) }
            }
        } catch (error: CancellationException) {
            if (ownsMutation(profile, remote, device)) updateState { it.copy(mutations = it.mutations.settled(target)) }
            throw error
        } catch (error: Exception) {
            if (ownsMutation(profile, remote, device)) {
                if (error is RemoteFailure && error.sessionExpired && device == null) run { throw error }
                else updateState { revert(it).copy(mutations = it.mutations.failed(target, message(error) ?: COULD_NOT_SAVE)) }
            }
        } finally {
            if (mutationJobs[target] === currentCoroutineContext()[Job]) mutationJobs.remove(target)
        }
    }
    mutationJobs[target] = task
    if (ownsControllerJob) claimOperation(task)
    task.start()
}

fun ApplicationController.retryMutation(target: MutationTarget) {
    if (mutable.value.mutations.failure(target) == null) return
    val retry = mutationRetries[target] ?: return
    mutate(target, retry.optimistic, retry.revert, retry.message, retry.operation)
}

fun ApplicationController.dismissMutationFailure(target: MutationTarget) = updateState {
    if (it.mutations.failure(target) == null) it else it.copy(mutations = it.mutations.settled(target))
}

internal const val COULD_NOT_SAVE = "Could not save this change. Try again."

private fun ApplicationController.ownsMutation(profile: String?, remote: RemoteConnection?, device: LocalConnection?): Boolean =
    mutable.value.selectedProfile == profile && connection === remote && local === device
