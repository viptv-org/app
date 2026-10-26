package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.*

internal data class PlaybackSeasonKey(
    val input: PlaybackInput, val profile: String, val title: String, val offset: Int, val limit: Int,
    val remote: RemoteConnection?, val local: LocalConnection?,
)

/**
 * The player lists the playing episode's whole season. Playback can start before
 * that season is loaded (from Continue Watching, or a next episode that opens a new
 * season), so its page is read in the background. Like the next-episode lookup,
 * this never stops, delays or replaces the current media.
 */
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
    val (offset, limit) = missingSeasonPage(card, detail) ?: run { cancelPlaybackSeason(); return }
    val remote = connection
    val device = local
    val key = PlaybackSeasonKey(input, profile, cardLookupKey(card), offset, limit, remote, device)
    if (playbackSeasonKey == key) return
    cancelPlaybackSeason()
    playbackSeasonKey = key
    val item = detail.item
    fun current() = playbackSeasonKey == key && mutable.value.screen == Screen.Player && mutable.value.playback === input &&
        mutable.value.selectedProfile == profile && mutable.value.sourceTitle?.let(::cardLookupKey) == key.title &&
        mutable.value.detail?.item?.let(::cardLookupKey) == cardLookupKey(item) && connection === remote && local === device
    playbackSeasonJob = scope.launch {
        val page = try {
            device?.detailPage(profile, item, offset, limit) ?: requireNotNull(remote).detail(item, offset, limit)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { return@launch }
        if (!current() || cardLookupKey(page.item) != cardLookupKey(item)) return@launch
        val loaded = requireNotNull(mutable.value.detail)
        mutable.value = mutable.value.copy(detail = loaded.copy(
            episodes = (loaded.episodes + page.episodes).distinctBy(::cardLookupKey),
            seasons = loaded.seasons.ifEmpty { page.seasons }))
    }
}

internal fun ApplicationController.cancelPlaybackSeason() {
    playbackSeasonJob?.cancel(); playbackSeasonJob = null; playbackSeasonKey = null
}

/** At most this many episodes of a long season are read around the playing one. */
private const val SEASON_PAGE = 128

/**
 * The details page (offset, limit) holding the playing episode's season, or null
 * when that season is already loaded or the details index does not describe it.
 */
internal fun missingSeasonPage(card: Card, detail: DetailSnapshot): Pair<Int, Int>? {
    val coordinates = card.identity.episode ?: return null
    val parent = card.parent_identity ?: return null
    if (card.identity.kind != "series" || !sameLookupIdentity(detail.item.identity, parent) ||
        detail.item.provider_id != card.provider_id || detail.item.metadata_origin != card.metadata_origin) return null
    val season = detail.seasons.find { it.season == coordinates.season }?.takeIf { it.episode_count > 0 && it.offset >= 0 } ?: return null
    val loaded = detail.episodes.count { it.identity.episode?.season == coordinates.season &&
        it.provider_id == card.provider_id && it.metadata_origin == card.metadata_origin &&
        it.parent_identity?.let { identity -> sameLookupIdentity(identity, parent) } == true }
    if (loaded >= season.episode_count) return null
    val limit = minOf(season.episode_count, SEASON_PAGE.toLong())
    val start = if (season.episode_count <= limit) 0L
        else (coordinates.episode - 1 - limit / 2).coerceIn(0L, season.episode_count - limit)
    val offset = season.offset + start
    return if (offset in 0..10_000) offset.toInt() to limit.toInt() else null
}
