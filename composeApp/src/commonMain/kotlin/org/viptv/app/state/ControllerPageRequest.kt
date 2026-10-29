package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.viptv.app.connection.RemoteFailure

internal fun ApplicationController.runPage(operation: suspend () -> Unit) {
    run(operation)
    val request = job
    pageRequestJob = request
    request?.invokeOnCompletion { if (pageRequestJob === request) pageRequestJob = null }
}

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
        if (currentPage() || error is RemoteFailure && error.status == 401 && sameSession()) throw error
        return null
    }
}
