package org.viptv.app.state

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.viptv.app.connection.FillerKind
import org.viptv.app.connection.animeFiller
import org.viptv.app.connection.cardLookupKey
import org.viptv.app.connection.sameLookupIdentity
import org.viptv.app.connection.seasonNumber
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.EpisodeSeason
import org.viptv.engine.wire.MediaIdentity

@Immutable
data class EpisodeFiller(val series: String, val kinds: Map<Long, FillerKind>)

@Immutable
class EpisodeFillerLabels(private val series: MediaIdentity?, private val kinds: Map<Long, FillerKind>,
    private val seasons: List<EpisodeSeason>) {
    fun of(card: Card): FillerKind? {
        if (kinds.isEmpty() || series == null) return null
        if (card.parent_identity?.let { sameLookupIdentity(it, series) } == false) return null
        return absoluteEpisode(card, seasons)?.let(kinds::get)
    }

    companion object {
        val Empty = EpisodeFillerLabels(null, emptyMap(), emptyList())

        fun of(detail: DetailSnapshot?, filler: EpisodeFiller?): EpisodeFillerLabels =
            if (detail == null || filler == null || filler.series != cardLookupKey(detail.item) || filler.kinds.isEmpty()) Empty
            else EpisodeFillerLabels(detail.item.identity, filler.kinds, detail.seasons)
    }
}

internal fun absoluteEpisode(card: Card, seasons: List<EpisodeSeason>): Long? {
    val coordinates = card.identity.episode ?: return null
    val season = coordinates.seasonNumber().takeIf { it > 0 } ?: return null
    val episode = coordinates.episode.takeIf { it > 0 } ?: return null
    val earlier = seasons.filter { (it.season ?: 1L) in 1 until season }.sumOf { it.episode_count }
    val count = seasons.find { (it.season ?: 1L) == season }?.episode_count
    return if (season > 1 && count != null && episode > count && episode > earlier) episode else earlier + episode
}

internal fun fillerTitles(item: Card): List<String> =
    (listOf(item.title) + item.metadata?.alternate_titles.orEmpty()).map(String::trim).filter(String::isNotEmpty).distinct().take(16)

fun ApplicationController.loadEpisodeFiller() {
    val item = mutable.value.detail?.item?.takeIf { it.identity.kind == "series" } ?: return
    val remote = connection ?: return
    val key = cardLookupKey(item)
    if (mutable.value.episodeFiller?.series == key) return
    fillerCache[key]?.let { kinds -> updateState { it.copy(episodeFiller = EpisodeFiller(key, kinds)) }; return }
    episodeFillerJob?.cancel()
    episodeFillerJob = scope.launch {
        val kinds = try { remote.animeFiller(fillerTitles(item)) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { return@launch }
        if (fillerCache.size >= 64) fillerCache.clear()
        fillerCache[key] = kinds
        updateState { state ->
            if (state.detail?.item?.let(::cardLookupKey) == key && connection === remote) state.copy(episodeFiller = EpisodeFiller(key, kinds))
            else state
        }
    }
}
