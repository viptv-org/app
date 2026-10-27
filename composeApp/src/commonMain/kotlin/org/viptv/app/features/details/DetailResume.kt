package org.viptv.app.features.details

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot

/** The numbered episode a series' Details opens on, and the progress that marks it (app#40). */
internal data class DetailResume(val season: Long?, val episode: Long, val progress: Double?) {
    fun matches(card: Card): Boolean = card.identity.episode?.let { it.season == season && it.episode == episode } == true

    /** [card] as the list shows it: the continued episode carries its progress even when its page has none. */
    fun mark(card: Card): Card {
        val value = progress?.takeIf { it.isFinite() && it > 0.0 && it < 1.0 } ?: return card
        return if (matches(card) && (card.progress ?: 0.0) <= 0.0) card.copy(progress = value) else card
    }
}

/** [card] as an episode list shows it, marked when it is the continued episode. */
internal fun DetailResume?.shown(card: Card): Card = this?.mark(card) ?: card

/** The continued episode's position among [episodes], when they hold it. */
internal fun DetailResume?.indexIn(episodes: List<Card>?): Int? =
    this?.let { target -> episodes?.indexOfFirst(target::matches)?.takeIf { it >= 0 } }

/**
 * The episode the viewer is continuing in [detail]'s series. A Continue Watching card
 * for this title names it even when its season is past the first Details page;
 * otherwise it is the loaded episode with partial progress (the hero's resume episode).
 */
internal fun detailResume(detail: DetailSnapshot?, continuing: List<Card>): DetailResume? {
    val item = detail?.item ?: return null
    if (item.identity.kind != "series" || item.identity.episode != null) return null
    val card = continuing.firstOrNull { continues(it, item) } ?: seriesResumeEpisode(detail) ?: return null
    val coordinates = card.identity.episode ?: return null
    return DetailResume(coordinates.season, coordinates.episode, card.progress)
}

/** [card] is a numbered episode of exactly the series [item] describes. */
private fun continues(card: Card, item: Card): Boolean {
    val coordinates = card.identity.episode ?: return false
    val parent = card.parent_identity ?: return false
    return card.identity.kind == "series" && coordinates.episode > 0L && parent.episode == null &&
        parent.kind == item.identity.kind && parent.id == item.identity.id
}

/**
 * Which season a Details episode list shows. The viewer's pick wins. Until they pick a
 * season or move the list, it is the continued episode's season as soon as Details know
 * that season, else the first numbered season.
 */
@Stable
internal class SeasonChoice(settled: Boolean = false, number: Long? = null) {
    /** The season kept by the viewer or by landing; null until then. A one-field holder, as a season number can be null. */
    private class Kept(val number: Long?)
    private var kept by mutableStateOf(if (settled) Kept(number) else null)

    /** The list has reached the continued episode, or the viewer acted first: nothing moves it any more. */
    val settled: Boolean get() = kept != null

    fun selected(seasons: List<DetailSeason>, resume: DetailResume?): DetailSeason? =
        kept?.let { choice -> seasons.find { it.number == choice.number } }
            ?: resume?.let { target -> seasons.find { it.number == target.season } }
            ?: seasons.firstOrNull { (it.number ?: 0L) > 0L } ?: seasons.firstOrNull()

    /** The viewer picked [number]. */
    fun pick(number: Long?) { kept = Kept(number) }

    /** Keep showing [season] from now on (the list reached the episode, or the viewer moved it). */
    fun keep(season: DetailSeason?) { if (kept == null && season != null) kept = Kept(season.number) }

    companion object {
        val Saver = listSaver<SeasonChoice, Any?>(save = { listOf(it.settled, it.kept?.number) },
            restore = { SeasonChoice(it[0] as Boolean, it[1] as Long?) })
    }
}

/**
 * Saveable, so a page that leaves composition (the browser dialog while Sources show, any
 * layout during playback) comes back on the season it showed instead of landing again.
 */
@Composable
internal fun rememberSeasonChoice(detail: DetailSnapshot?): SeasonChoice =
    rememberSaveable(detail?.item?.identity, saver = SeasonChoice.Saver) { SeasonChoice() }

/**
 * Bring [list] to the continued episode once, at item [index], with [clearance] left
 * above it for chrome drawn over the list. A viewer who scrolls first keeps the list
 * (and its season) where they put it.
 */
@Composable
internal fun LandOnResume(choice: SeasonChoice, list: LazyListState, index: Int?, season: DetailSeason?, clearance: Dp = 0.dp) {
    val clearancePx = with(LocalDensity.current) { clearance.toPx() }
    val shown by rememberUpdatedState(season)
    var landing by remember(choice) { mutableStateOf(false) }
    LaunchedEffect(choice, list, index) {
        if (index == null || choice.settled) return@LaunchedEffect
        landing = true
        // A negative offset leaves the clearance above the item (the item before it shows there).
        try { list.scrollToItem(index, -clearancePx.toInt()) } finally { landing = false }
        choice.keep(shown)
    }
    LaunchedEffect(choice, list) {
        snapshotFlow { list.isScrollInProgress }.collect { scrolling -> if (scrolling && !landing) choice.keep(shown) }
    }
}
