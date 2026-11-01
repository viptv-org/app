package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*

private const val VodMatchesSearchDelay = 300L

fun ApplicationController.openVodMatches() {
    if (mutable.value.localMode) return
    updateState { it.copy(vodMatches = VodMatchesState()) }
    loadVodMatches(providers = true)
}

fun ApplicationController.closeVodMatches() {
    vodMatchesJob?.cancel(); vodMatchesJob = null
    vodMatchesRequest++
    updateState { it.copy(vodMatches = null) }
}

fun ApplicationController.refreshVodMatches() = loadVodMatches(providers = true)

fun ApplicationController.updateVodMatchProvider(providerId: Long?) {
    val state = mutable.value.vodMatches ?: return
    if (state.providerId == providerId) return
    updateState { it.copy(vodMatches = it.vodMatches?.copy(providerId = providerId, form = null)) }
    loadVodMatches()
}

fun ApplicationController.updateVodMatchType(type: String?) {
    val state = mutable.value.vodMatches ?: return
    if (state.type == type) return
    updateState { it.copy(vodMatches = it.vodMatches?.copy(type = type, form = null)) }
    loadVodMatches()
}

fun ApplicationController.searchVodMatches(query: String) {
    val state = mutable.value.vodMatches ?: return
    val text = query.take(128)
    if (state.query == text) return
    updateState { it.copy(vodMatches = it.vodMatches?.copy(query = text, form = null)) }
    loadVodMatches(debounce = true)
}

fun ApplicationController.loadMoreVodMatches() {
    val cursor = mutable.value.vodMatches?.nextCursor ?: return
    loadVodMatches(cursor = cursor, append = true)
}

fun ApplicationController.openVodMatchForm(row: VodMatchRow) {
    updateState { next ->
        val state = next.vodMatches ?: return@updateState next
        next.copy(vodMatches = state.copy(
            form = VodMatchForm(row.vodId, row.type, row.name, state.providerName(row.providerId), row.metadataId.orEmpty()),
            error = null, notice = null))
    }
}

fun ApplicationController.changeVodMatchMetadata(value: String) {
    val metadataId = value.filterNot { it.isWhitespace() }.take(256)
    updateState { next ->
        val state = next.vodMatches ?: return@updateState next
        val form = state.form ?: return@updateState next
        if (metadataId == form.metadataId) return@updateState next
        next.copy(vodMatches = state.copy(form = form.copy(metadataId = metadataId), error = null))
    }
}

fun ApplicationController.closeVodMatchForm() {
    updateState { next -> next.copy(vodMatches = next.vodMatches?.copy(form = null, error = null)) }
}

fun ApplicationController.saveVodMatch() {
    val remote = connection ?: return
    val state = mutable.value.vodMatches ?: return
    val form = state.form ?: return
    if (state.saving) return
    val metadataId = form.metadataId.trim()
    if (metadataId.isEmpty()) {
        updateState { next -> next.copy(vodMatches = next.vodMatches?.copy(error = "Enter the metadata ID to match this title to.")) }
        return
    }
    vodMatchesJob?.cancel()
    val request = ++vodMatchesRequest
    vodMatchesJob = scope.launch {
        updateState { next -> next.copy(vodMatches = next.vodMatches?.copy(saving = true, error = null, notice = null)) }
        try {
            remote.overrideVodMatch(form.vodId, metadataId, form.type)
            if (request != vodMatchesRequest || connection !== remote) return@launch
            updateState { next ->
                val current = next.vodMatches ?: return@updateState next
                next.copy(vodMatches = current.copy(
                    rows = current.rows.map { row -> if (row.vodId == form.vodId) row.copy(metadataId = metadataId) else row },
                    form = null, saving = false, notice = "Match saved."))
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (request != vodMatchesRequest || connection !== remote) return@launch
            updateState { next -> next.copy(vodMatches = next.vodMatches?.copy(saving = false, error = when {
                error is RemoteFailure && error.status == 403 -> "Only the account owner can change matches."
                error is RemoteFailure && error.status == 404 -> "That title is no longer in the provider catalog."
                error is RemoteFailure && error.status == 422 -> "This title cannot be matched to that metadata ID."
                error is RemoteFailure -> error.message
                else -> "Could not save the match. Try again."
            })) }
        }
    }
}

private fun ApplicationController.loadVodMatches(providers: Boolean = false, cursor: String? = null,
    append: Boolean = false, debounce: Boolean = false) {
    val remote = connection ?: return
    vodMatchesJob?.cancel()
    val request = ++vodMatchesRequest
    vodMatchesJob = scope.launch {
        try {
            if (debounce) delay(VodMatchesSearchDelay)
            updateState { next -> next.copy(vodMatches = next.vodMatches?.copy(loading = !append, error = null, notice = null)) }
            val current = mutable.value.vodMatches ?: return@launch
            val known = if (providers || current.providers.isEmpty())
                remote.iptvConnections().map { VodMatchProvider(it.id, it.name) } else current.providers
            val page = remote.vodMatches(VodMatchFilter(current.providerId, current.type, current.query.trim()), cursor)
            if (request != vodMatchesRequest || connection !== remote) return@launch
            updateState { next ->
                val state = next.vodMatches ?: return@updateState next
                val rows = page.items.map { VodMatchRow(it.vod_id, it.provider_id, it.type, it.name, it.year, it.poster) }
                next.copy(vodMatches = state.copy(providers = known,
                    rows = if (append) state.rows + rows else rows, nextCursor = page.next_cursor,
                    loaded = true, loading = false, error = null))
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (request != vodMatchesRequest || connection !== remote) return@launch
            updateState { next -> next.copy(vodMatches = next.vodMatches?.copy(loading = false, loaded = true, error = when {
                error is RemoteFailure && error.status == 403 -> "VOD matches aren’t available for this profile. Unlock parental controls or use the account owner’s profile."
                error is RemoteFailure && error.status == 404 -> "This server doesn’t support VOD matches. Update the server and try again."
                error is RemoteFailure -> error.message
                else -> "Could not load VOD matches. Try again."
            })) }
        }
    }
}