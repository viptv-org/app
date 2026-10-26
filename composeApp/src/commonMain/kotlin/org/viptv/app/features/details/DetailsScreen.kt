package org.viptv.app.features.details

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot

@Composable
fun DetailsScreen(detail: DetailSnapshot?, busy: Boolean, error: String?, back: () -> Unit, sources: (Card) -> Unit, television: Boolean = false, tablet: Boolean = false, browser: Boolean = false, save: (Card) -> Unit = {}, saved: Boolean = false, open: (Card) -> Unit = sources, loadPage: (Long) -> Unit = {}, playFromStart: ((Card) -> Unit)? = null, defaultSourceLabel: String? = null) {
    if (television) {
        TvDetailsScreen(detail, busy, error, sources, extras = detailExtras(detail), open = open, save = save, saved = saved, loadPage = loadPage, playFromStart = playFromStart, back = back)
        return
    }
    if (detail == null && busy && !browser) { DetailLoadingLayout(false); return }
    if (detail == null && !busy) {
        // NAV-04: an initial-load failure used to compose nothing at all.
        DetailUnavailable(error, back)
        return
    }
    if (tablet) { TabletDetailsScreen(detail, busy, error, back, sources, save, saved, open, loadPage); return }
    if (browser) { BrowserDetailsDialog(detail, busy, error, back, sources, save, saved, open, loadPage); return }
    val seasons = remember(detail?.episodes, detail?.seasons) { detailSeasons(detail?.episodes.orEmpty(), detail?.seasons.orEmpty()) }
    var seasonKey by remember(detail?.item?.identity) { mutableStateOf<String?>(null) }
    var section by remember(detail?.item?.identity) { mutableStateOf(
        when { detail?.episodes?.isNotEmpty() == true -> DetailSection.Episodes
            detail?.recommendations?.isNotEmpty() == true -> DetailSection.Related
            else -> DetailSection.Information }) }
    val selected = seasons.find { (it.number?.toString() ?: "unknown") == seasonKey } ?: seasons.firstOrNull { (it.number ?: 0L) > 0L } ?: seasons.firstOrNull()
    AutomaticEpisodes(selected, detail?.next_cursor, busy, loadPage, enabled = section == DetailSection.Episodes,
        scopeKey = detail?.item?.let { it.provider_id to it.identity })
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp
        val compact = maxWidth < 900.dp
        val gutter = if (phone) 16.dp else 40.dp
        val listState = rememberLazyListState()
        val scrolled by remember(phone) { derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 240 } }
        LazyColumn(Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(top = if (phone) 0.dp else 16.dp, bottom = if (phone) 120.dp else 96.dp),
            verticalArrangement = Arrangement.spacedBy(if (phone) 18.dp else 24.dp)) {
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = gutter), color = Palette.accent) }
            error?.let { item { Text(it, Modifier.padding(horizontal = gutter, vertical = if (phone) 64.dp else 0.dp), color = Palette.danger) } }
            detail?.let { value ->
                // Desktop chrome renders its own back/forward in the title bar;
                // phone/tablet keep the in-content affordances.
                val desktop = !phone && !tablet
                item {
                    if (phone) PhoneDetailHero(value.item, sources, save, saved, seriesResumeEpisode(value))
                    else if (desktop) Box(Modifier.padding(bottom = 20.dp)) {
                        DesktopDetailHero(value.item, sources, save, saved, playFromStart, chooseSource = sources,
                            sourceLabel = defaultSourceLabel, resumeEpisode = seriesResumeEpisode(value))
                    } else Box(Modifier.padding(horizontal = gutter)) { DetailHero(value.item, compact, sources, save, saved, playFromStart) }
                }
                if (!desktop) item(key = "sections") {
                    DetailSections(section, seasons.isNotEmpty(), value.recommendations.isNotEmpty(),
                        { section = it }, Modifier.padding(horizontal = gutter))
                }
                if (!desktop && section == DetailSection.Information) item(key = "information") {
                    Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        org.viptv.app.components.MediaMetadata(value.item)
                        value.item.description?.let { Text(it, color = Palette.body, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
                if ((!desktop && section == DetailSection.Episodes || desktop && seasons.isNotEmpty())) selected?.let { season ->
                    item {
                        Box(Modifier.padding(if (desktop) PaddingValues(start = 40.dp, end = 40.dp) else PaddingValues(horizontal = gutter))) {
                            DetailSeasonPicker(seasons, season, phone) { number ->
                                seasonKey = number?.toString() ?: "unknown"
                                seasons.find { it.number == number }?.nextOffset?.let(loadPage)
                            }
                        }
                    }
                    if (phone) items(season.episodes, key = ::episodeKey) { episode ->
                        DetailEpisodeCard(episode, true, sources, Modifier.fillMaxWidth().padding(horizontal = gutter))
                    } else items(season.episodes, key = ::episodeKey) { episode ->
                        DesktopEpisodeRow(episode, sources, modifier = Modifier.padding(if (desktop) PaddingValues(start = 40.dp, end = 40.dp) else PaddingValues(horizontal = gutter)))
                    }
                }
                if (value.recommendations.isNotEmpty() && (!desktop && section == DetailSection.Related || desktop)) item(key = "related") {
                    Column(Modifier.padding(if (desktop) PaddingValues(start = 40.dp, end = 40.dp, bottom = 24.dp) else PaddingValues(horizontal = gutter)),
                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (desktop) Text("More like this", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        RelatedTitles(value.recommendations, open, Modifier.fillMaxWidth(), showHeading = !desktop)
                    }
                }
            }
        }
        if (phone) Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(48.dp)
            .background(if (scrolled) Palette.background.copy(alpha = .88f) else Color.Transparent)) {
            IconButton(onClick = back, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp)
                .size(40.dp).background(Color.White.copy(alpha = .12f), CircleShape)) {
                Icon(Lucide.ChevronLeft, "Back", Modifier.size(22.dp))
            }
            if (scrolled) detail?.item?.title?.let { Text(it, Modifier.align(Alignment.Center).widthIn(max = 220.dp),
                fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}


/** The partially watched episode whose coordinates, runtime and progress drive the hero Resume pill. */
internal fun seriesResumeEpisode(detail: DetailSnapshot): Card? {
    if (detail.item.identity.kind != "series") return null
    return detail.episodes.firstOrNull {
        val coordinates = it.identity.episode
        coordinates != null && (coordinates.season ?: 1L) > 0L && coordinates.episode > 0L &&
            it.progress != null && it.progress > 0.0 && it.progress < 1.0
    }
}

internal fun remainingMinutesLabel(card: Card): String? {
    val progress = card.progress ?: 0.0
    val runtime = card.metadata?.runtime_minutes ?: return null
    val remaining = kotlin.math.round((1.0 - progress) * runtime)
    return if (remaining > 0) "${remaining}m left" else "Almost done"
}

/** Real resume state for the TV details hero: the episode being watched, its label and time left. */
internal fun detailExtras(detail: DetailSnapshot?): TvDetailExtras {
    if (detail == null) return TvDetailExtras()
    val resume = seriesResumeEpisode(detail)
        ?: detail.item.takeIf { it.progress != null && it.progress > 0.0 && it.progress < 1.0 }
    val label = when {
        resume?.identity?.episode != null -> "Resume S${resume.identity.episode?.season ?: 1L} E${resume.identity.episode?.episode}"
        resume != null -> "Resume"
        else -> null
    }
    return TvDetailExtras(resume = resume, resumeLabel = label, remaining = resume?.let(::remainingMinutesLabel),
        recommendations = detail.recommendations)
}


@Composable
private fun DetailUnavailable(error: String?, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text("This title couldn’t load", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(error ?: "The provider didn’t return this title’s details.", color = Palette.secondary)
        Spacer(Modifier.height(20.dp))
        Button(onClick = back) { Text("Go back") }
    }
}
