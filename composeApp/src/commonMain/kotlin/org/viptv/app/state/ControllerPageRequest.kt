package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.viptv.app.connection.RemoteFailure

/** Page navigation cancels only page-bound work; configuration refreshes may finish in the background. */
internal fun ApplicationController.runPage(operation: suspend () -> Unit) {
    run(operation)
    val request = job
    pageRequestJob = request
    request?.invokeOnCompletion { if (pageRequestJob === request) pageRequestJob = null }
}

/** A page read must not restore an abandoned route or start a retired Play action. */
internal suspend fun <T : Any> ApplicationController.readCurrentPage(navigation: Long, read: suspend () -> T): T? {
    val owner = generation
    val device = local
    val remote = connection
    val profile = mutable.value.selectedProfile
    fun sameSession() = generation == owner && local === device && connection === remote && mutable.value.selectedProfile == profile
    fun currentPage() = sameSession() && navigationGeneration == navigation
    currentCoroutineContext().ensureActive()
    if (!currentPage()) return null
    try {
        val result = read()
        currentCoroutineContext().ensureActive()
        return result.takeIf { currentPage() }
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        // An actually expired current session still retires through run's central
        // handler even if the user left the page that discovered the expiry.
        if (currentPage() || error is RemoteFailure && error.status == 401 && sameSession()) throw error
        return null
    }
}
