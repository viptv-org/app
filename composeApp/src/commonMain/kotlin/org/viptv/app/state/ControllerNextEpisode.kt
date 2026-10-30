package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.*

internal data class NextEpisodeKey(
    val input: PlaybackInput, val profile: String, val identity: MediaIdentity,
    val parent: MediaIdentity, val provider: String,
    val remote: RemoteConnection?, val local: LocalConnection?, val homeScope: Scope?, val origin: MetadataOrigin? = null,
)

fun ApplicationController.loadNextEpisode(force: Boolean = false) {
    val state = mutable.value
    val input = state.playback
    val card = state.sourceTitle
    val profile = state.selectedProfile
    val parent = card?.parent_identity
    if (state.screen != Screen.Player || input == null || input.live || profile == null || input.profileId != profile || card == null ||
        card.identity.kind != "series" || card.identity.episode == null || parent == null) {
        cancelNextEpisode()
        return
    }
    val key = NextEpisodeKey(input, profile, card.identity, parent, card.provider_id, connection, local, state.home?.scope?.owner(), card.metadata_origin)
    if (!force && nextEpisodeKey == key) return
    cancelNextEpisode()
    nextEpisodeKey = key
    val operation = nextEpisodeGeneration
    val detail = state.detail
    fun current() = nextEpisodeGeneration == operation && nextEpisodeKey == key &&
        mutable.value.screen == Screen.Player && mutable.value.playback === input &&
        mutable.value.selectedProfile == profile && mutable.value.sourceTitle?.identity == card.identity &&
        mutable.value.sourceTitle?.metadata_origin == key.origin && mutable.value.sourceTitle?.parent_identity == parent && mutable.value.sourceTitle?.provider_id == card.provider_id &&
        mutable.value.home?.scope?.owner() == key.homeScope && connection === key.remote && local === key.local
    updateState { next -> next.copy(nextEpisode = null, nextEpisodeStatus = ContinuationStatus.PENDING, nextEpisodeLoading = true) }
    nextEpisodeJob = scope.launch {
        try {
            val resolved = withTimeoutOrNull(12_000) {
                if (key.remote != null) {
                    try { key.remote.nextEpisode(profile, card) }
                    catch (error: RemoteFailure) {
                        if (error.status != 404) throw error
                        loadedNextEpisode(card, detail)?.let { NextEpisodeResult(ContinuationStatus.NEXT, it) }
                            ?: NextEpisodeResult(ContinuationStatus.UNAVAILABLE)
                    }
                } else {
                    var snapshot = detail
                    var candidate = loadedNextEpisode(card, snapshot)
                    var cursor = snapshot?.next_cursor?.toIntOrNull()
                    val seen = mutableSetOf<Int>()
                    val device = key.local
                    while (candidate == null && device != null) {
                        val offset = cursor?.takeIf { it in 1..10_000 } ?: break
                        if (!seen.add(offset)) break
                        if (!current()) throw CancellationException("Playback changed")
                        val previous = snapshot ?: break
                        if (previous.item.provider_id != card.provider_id || previous.item.metadata_origin != card.metadata_origin || previous.item.identity != parent) break
                        val page = device.detailPage(profile, previous.item, offset, 96)
                        if (page.item.provider_id != card.provider_id || page.item.metadata_origin != card.metadata_origin || page.item.identity != parent) break
                        snapshot = previous.copy(episodes = (previous.episodes + page.episodes).distinctBy(::cardLookupKey), next_cursor = page.next_cursor)
                        candidate = loadedNextEpisode(card, snapshot)
                        cursor = page.next_cursor?.toIntOrNull()?.takeIf { it > offset }
                    }
                    candidate?.let { NextEpisodeResult(ContinuationStatus.NEXT, it) }
                        ?: NextEpisodeResult(ContinuationStatus.UNAVAILABLE)
                }
            }
            if (!current()) return@launch
            val next = resolved?.item?.takeIf { resolved.status == ContinuationStatus.NEXT && validNextEpisode(card, it) }
            updateState { current -> current.copy(nextEpisode = next,
                nextEpisodeStatus = if (resolved == null || (resolved.status == ContinuationStatus.NEXT && next == null)) ContinuationStatus.UNAVAILABLE else resolved.status) }
        } catch (error: CancellationException) {
            if (current()) updateState { next -> next.copy(nextEpisode = null, nextEpisodeStatus = ContinuationStatus.UNAVAILABLE) }
            throw error
        }
        catch (_: Exception) {
            if (current()) updateState { next -> next.copy(nextEpisode = null, nextEpisodeStatus = ContinuationStatus.UNAVAILABLE) }
        } finally {
            if (current()) updateState { next -> next.copy(nextEpisodeLoading = false) }
        }
    }
}

internal fun ApplicationController.cancelNextEpisode() {
    nextEpisodeGeneration++
    nextEpisodeJob?.cancel(); nextEpisodeJob = null; nextEpisodeKey = null
    updateState { next -> next.copy(nextEpisode = null, nextEpisodeStatus = null, nextEpisodeLoading = false) }
}

internal fun loadedNextEpisode(card: Card, detail: DetailSnapshot?): Card? {
    if (detail == null || detail.item.identity != card.parent_identity || detail.item.provider_id != card.provider_id || detail.item.metadata_origin != card.metadata_origin) return null
    val index = detail.episodes.indexOfFirst { it.provider_id == card.provider_id && it.metadata_origin == card.metadata_origin && it.identity == card.identity && it.parent_identity == card.parent_identity }
    return detail.episodes.getOrNull(index + 1)?.takeIf { index >= 0 && validNextEpisode(card, it) }
}

private fun validNextEpisode(current: Card, next: Card): Boolean {
    val here = current.identity.episode ?: return false
    val there = next.identity.episode ?: return false
    if (next.metadata_origin != current.metadata_origin || next.provider_id != current.provider_id || next.identity.kind != "series" ||
        next.parent_identity != current.parent_identity || next.identity.id == current.identity.id || here.order != there.order) return false
    val season = here.season ?: return false
    val nextSeason = there.season ?: return false
    if ((season == 0L) != (nextSeason == 0L)) return false
    return nextSeason > season || (nextSeason == season && there.episode > here.episode)
}
