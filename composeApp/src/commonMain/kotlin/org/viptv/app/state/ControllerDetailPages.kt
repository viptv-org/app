package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*

fun ApplicationController.detailPage(offset: Long) {
    require(offset in 0..10_000)
    val previous = mutable.value.detail ?: return
    val profile = mutable.value.selectedProfile ?: return
    if (mutable.value.screen != Screen.Details || mutable.value.busy) return
    if (detailPagingJob?.isActive == true && mutable.value.detailLoadingOffset == offset) return
    detailPagingJob?.cancel()
    val operation = generation
    val remote = connection
    val device = local
    val homeScope = mutable.value.home?.scope?.owner()
    fun current() = generation == operation && mutable.value.screen == Screen.Details &&
        mutable.value.selectedProfile == profile && mutable.value.home?.scope?.owner() == homeScope &&
        mutable.value.detail?.item?.let(::cardLookupKey) == cardLookupKey(previous.item) && connection === remote && local === device
    updateState { next -> next.copy(detailLoadingOffset = offset, detailPageError = null) }
    detailPagingJob = scope.launch {
        try {
            val page = device?.detailPage(profile, previous.item, offset.toInt(), 96)
                ?: requireNotNull(remote).detail(previous.item, offset.toInt(), 96)
            if (!current()) return@launch
            require(cardLookupKey(page.item) == cardLookupKey(previous.item))
            updateState { next ->
                val loaded = requireNotNull(next.detail)
                next.copy(detail = loaded.copy(
                    episodes = (loaded.episodes + page.episodes).distinctBy(::cardLookupKey),
                    next_cursor = page.next_cursor,
                    recommendations = page.recommendations.ifEmpty { loaded.recommendations },
                    seasons = page.seasons.ifEmpty { loaded.seasons }))
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (current()) {
                if (error is RemoteFailure && error.status == 401 && device == null) {
                    releaseRemotePlayback(); discardStremioImport(); remote?.close(); connection = null
                    mutable.value = ApplicationState(error = "Session expired. Sign in again.")
                } else updateState { next -> next.copy(detailPageError = "Could not load the remaining episodes. Try selecting the season again.") }
            }
        } finally {
            if (current() && mutable.value.detailLoadingOffset == offset)
                updateState { next -> next.copy(detailLoadingOffset = null) }
        }
    }
}

internal fun ApplicationController.cancelDetailPaging() {
    detailPagingJob?.cancel(); detailPagingJob = null
    updateState { next -> next.copy(detailLoadingOffset = null, detailPageError = null) }
}
