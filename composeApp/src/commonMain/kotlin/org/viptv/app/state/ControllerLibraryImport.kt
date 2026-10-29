package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.LibraryImportStatus

/** Lightweight, bounded status reads; completed imports refresh Home once without changing routes. */
internal fun ApplicationController.startLibraryImportRefresh(initial: LibraryImportStatus?) {
    libraryImportRefresh?.cancel()
    if (initial?.running != true || local != null) return
    val remote = connection ?: return
    val profile = mutable.value.selectedProfile ?: return
    fun ownsScope() = connection === remote && local == null && mutable.value.selectedProfile == profile
    libraryImportRefresh = scope.launch {
        try {
            var status = initial
            repeat(12) { attempt ->
                val backoff = (4_000L * (1L shl attempt.coerceAtMost(3))).coerceAtMost(30_000)
                delay(maxOf(status?.refresh_after_millis ?: 0, backoff).coerceAtMost(30_000))
                if (!ownsScope()) return@launch
                val updated = remote.libraryImportStatus(profile)
                status = updated
                if (!updated.running) {
                    val fresh = remote.bootstrap()
                    if (!ownsScope()) return@launch
                    mutable.value = mutable.value.copy(home = fresh.home ?: mutable.value.home)
                    adoptContinuation(fresh.continuation)
                    fresh.library?.let { cards -> updateLibrary(cards, membership = fresh.libraryMembership) }
                    return@launch
                }
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* A later refresh retries; import status never blocks browsing. */ }
    }
}
