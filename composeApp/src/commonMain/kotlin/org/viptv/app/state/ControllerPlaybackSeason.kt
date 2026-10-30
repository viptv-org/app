package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.*

internal data class SeasonPage(val offset: Int, val limit: Int)

internal data class PlaybackSeasonKey(
    val input: PlaybackInput, val profile: String, val title: String, val page: SeasonPage,
    val remote: RemoteConnection?, val local: LocalConnection?,
)

fun ApplicationController.loadPlaybackSeason() {
    val state = mutable.value
    val input = state.playback
    val card = state.sourceTitle
    val profile = state.selectedProfile
    val detail = state.detail
    if (state.screen != Screen.Player || input == null || input.live || profile == null || input.profileId != profile ||
        card == null || detail == null) {
        cancelPlaybackSeason()
        return
    }
    val page = missingSeasonPage(card, detail, state.playbackSeason) ?: return
    val remote = connection
    val device = local
    val key = PlaybackSeasonKey(input, profile, cardLookupKey(card), page, remote, device)
    if (playbackSeasonKey == key) return
    cancelPlaybackSeason()
    playbackSeasonKey = key
    val operation = playbackSeasonGeneration
    val item = detail.item
    fun current() = playbackSeasonGeneration == operation && mutable.value.screen == Screen.Player &&
        mutable.value.playback === input && mutable.value.selectedProfile == profile &&
        mutable.value.sourceTitle?.let(::cardLookupKey) == key.title &&
        mutable.value.detail?.item?.let(::cardLookupKey) == cardLookupKey(item) && connection === remote && local === device
    playbackSeasonJob = scope.launch {
        val read = try {
            withTimeoutOrNull(12_000) {
                device?.detailPage(profile, item, page.offset, page.limit) ?: requireNotNull(remote).detail(item, page.offset, page.limit)
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { null } ?: return@launch
        if (!current() || cardLookupKey(read.item) != cardLookupKey(item)) return@launch
        val season = card.identity.episode?.season
        updateState { next -> next.copy(playbackSeason = read.episodes.filter {
            it.identity.episode?.season == season && sameEpisodeScope(card, it)
        }) }
    }
}

internal fun ApplicationController.cancelPlaybackSeason() {
    playbackSeasonGeneration++
    playbackSeasonJob?.cancel(); playbackSeasonJob = null; playbackSeasonKey = null
    if (mutable.value.playbackSeason.isNotEmpty()) updateState { next -> next.copy(playbackSeason = emptyList()) }
}

private const val SEASON_PAGE_LIMIT = 128L

internal fun missingSeasonPage(card: Card, detail: DetailSnapshot, playbackSeason: List<Card> = emptyList()): SeasonPage? {
    val coordinates = card.identity.episode ?: return null
    val parent = card.parent_identity ?: return null
    if (card.identity.kind != "series" || !sameLookupIdentity(detail.item.identity, parent) ||
        detail.item.provider_id != card.provider_id || detail.item.metadata_origin != card.metadata_origin) return null
    val season = detail.seasons.find { it.season == coordinates.season }?.takeIf { it.episode_count > 0 && it.offset >= 0 } ?: return null
    val loaded = (detail.episodes + playbackSeason).filter { it.identity.episode?.season == coordinates.season && sameEpisodeScope(card, it) }
        .distinctBy(::cardLookupKey).size
    if (loaded >= season.episode_count) return null
    val limit = minOf(season.episode_count, SEASON_PAGE_LIMIT)
    val start = if (season.episode_count <= limit) 0L
        else (coordinates.episode - 1 - limit / 2).coerceIn(0L, season.episode_count - limit)
    val offset = season.offset + start
    return if (offset in 0..10_000) SeasonPage(offset.toInt(), limit.toInt()) else null
}

private fun sameEpisodeScope(card: Card, other: Card): Boolean {
    val parent = card.parent_identity ?: return false
    return other.identity.kind == "series" && other.identity.episode != null &&
        other.provider_id == card.provider_id && other.metadata_origin == card.metadata_origin &&
        other.parent_identity?.let { sameLookupIdentity(it, parent) } == true
}
