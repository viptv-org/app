package org.viptv.app.features.details

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.DetailFocus
import org.viptv.app.connection.LocalFailure
import org.viptv.app.state.sourceTarget
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot

internal data class DetailResume(val season: Long?, val episode: Long, val progress: Double?, val card: Card) {
    fun matches(card: Card): Boolean = card.identity.episode?.let { it.season == season && it.episode == episode } == true

    fun mark(card: Card): Card {
        val value = progress?.takeIf { it.isFinite() && it > 0.0 && it < 1.0 } ?: return card
        return if (matches(card) && (card.progress ?: 0.0) <= 0.0) card.copy(progress = value) else card
    }
}

internal fun DetailResume?.shown(card: Card): Card = this?.mark(card) ?: card

internal fun DetailResume?.indexIn(episodes: List<Card>?): Int? =
    this?.let { target -> episodes?.indexOfFirst(target::matches)?.takeIf { it >= 0 } }

internal fun detailResume(detail: DetailSnapshot?, continuing: List<Card>): DetailResume? {
    val item = detail?.item ?: return null
    if (item.identity.kind != "series" || item.identity.episode != null) return null
    val card = continuing.firstOrNull { continues(it, item) } ?: seriesResumeEpisode(detail) ?: return null
    val coordinates = card.identity.episode ?: return null
    return DetailResume(coordinates.season, coordinates.episode, card.progress, card)
}

internal fun DetailResume?.heroEpisode(detail: DetailSnapshot): Card? {
    val resume = this ?: return null
    detail.episodes.firstOrNull(resume::matches)?.let { return resume.mark(it) }
    return try { sourceTarget(resume.card, detail) } catch (_: LocalFailure) { null }
}

internal fun resuming(card: Card?): Boolean = card?.progress?.let { it > 0.0 && it < 1.0 } == true

internal fun heroPlayLabel(card: Card?): String =
    (if (resuming(card)) "Resume" else "Play") + card?.identity?.episode?.let { " S${it.season ?: 1L} E${it.episode}" }.orEmpty()

private fun continues(card: Card, item: Card): Boolean {
    val coordinates = card.identity.episode ?: return false
    val parent = card.parent_identity ?: return false
    return card.identity.kind == "series" && coordinates.episode > 0L && parent.episode == null &&
        parent.kind == item.identity.kind && parent.id == item.identity.id
}

@Stable
internal class SeasonChoice(settled: Boolean = false, number: Long? = null, landed: Boolean = false) {
    private class Kept(val number: Long?)
    private var kept by mutableStateOf(if (settled) Kept(number) else null)
    private var landedState by mutableStateOf(landed)

    val settled: Boolean get() = kept != null
    val landed: Boolean get() = landedState

    fun selected(seasons: List<DetailSeason>, resume: DetailResume?): DetailSeason? =
        kept?.let { choice -> seasons.find { it.number == choice.number } }
            ?: resume?.let { target -> seasons.find { it.number == target.season } }
            ?: seasons.firstOrNull { (it.number ?: 0L) > 0L } ?: seasons.firstOrNull()

    fun pick(number: Long?) { kept = Kept(number); landedState = true }

    fun keep(season: DetailSeason?) {
        landedState = true
        if (kept == null && season != null) kept = Kept(season.number)
    }

    companion object {
        val Saver = listSaver<SeasonChoice, Any?>(save = { listOf(it.settled, it.kept?.number, it.landed) },
            restore = { SeasonChoice(it[0] as Boolean, it[1] as Long?, it[2] as Boolean) })
    }
}

@Composable
internal fun rememberSeasonChoice(detail: DetailSnapshot?, focus: DetailFocus? = null): SeasonChoice =
    rememberSaveable(detail?.item?.identity, saver = SeasonChoice.Saver) {
        SeasonChoice(settled = focus?.season != null, number = focus?.season)
    }

@Composable
internal fun LandOnResume(choice: SeasonChoice, list: LazyListState, index: Int?, season: DetailSeason?, clearance: Dp = 0.dp) {
    val clearancePx = with(LocalDensity.current) { clearance.toPx() }
    val shown by rememberUpdatedState(season)
    var landing by remember(choice) { mutableStateOf(false) }
    LaunchedEffect(choice, list, index) {
        if (index == null || choice.landed) return@LaunchedEffect
        landing = true
        try { list.scrollToItem(index, -clearancePx.toInt()) } finally { landing = false }
        choice.keep(shown)
    }
    LaunchedEffect(choice, list) {
        snapshotFlow { list.isScrollInProgress }.collect { scrolling -> if (scrolling && !landing) choice.keep(shown) }
    }
}
