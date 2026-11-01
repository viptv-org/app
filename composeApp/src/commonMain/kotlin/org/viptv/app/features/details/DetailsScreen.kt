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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette
import org.viptv.app.connection.DetailFocus
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.app.components.Button
import org.viptv.app.components.IconButton
import org.viptv.app.components.listItemEntrance
import org.viptv.app.components.rememberListEntrance

@Composable
fun DetailsScreen(detail: DetailSnapshot?, busy: Boolean, error: String?, back: () -> Unit, sources: (Card) -> Unit, television: Boolean = false, tablet: Boolean = false, browser: Boolean = false, save: (Card) -> Unit = {}, saved: Boolean = false, open: (Card) -> Unit = sources, loadPage: (Long) -> Unit = {}, playFromStart: ((Card) -> Unit)? = null, defaultSourceLabel: String? = null,
    continuing: List<Card> = emptyList(),
    paging: Boolean = true,
    playTap: Boolean = false,
    retry: (() -> Unit)? = null,
    closeFocusOnOpen: Boolean = true,
    focus: DetailFocus? = null) {
    if (television) {
        TvDetailsScreen(detail, busy, error, sources, extras = remember(detail, continuing) { detailExtras(detail, continuing) }, open = open, save = save, saved = saved, loadPage = loadPage, playFromStart = playFromStart, back = back,
            continuing = continuing, shown = paging, playTap = playTap, retry = retry, focus = focus)
        return
    }
    if (detail == null && busy && !browser) { DetailLoadingLayout(false); return }
    if (detail == null && !busy) {
        DetailUnavailable(error, back, retry)
        return
    }
    if (tablet) { TabletDetailsScreen(detail, busy, error, back, sources, save, saved, open, loadPage, continuing, paging, focus); return }
    if (browser) { BrowserDetailsDialog(detail, busy, error, back, sources, save, saved, open, loadPage, continuing, paging, closeFocusOnOpen, focus); return }
    val seasons = remember(detail?.episodes, detail?.seasons) { detailSeasons(detail?.episodes.orEmpty(), detail?.seasons.orEmpty()) }
    val resume = remember(detail, continuing) { detailResume(detail, continuing) }
    val heroEpisode = remember(resume, detail) { detail?.let(resume::heroEpisode) }
    val seasonChoice = rememberSeasonChoice(detail, focus)
    var section by remember(detail?.item?.identity, detail?.episodes?.isNotEmpty() == true, detail?.recommendations?.isNotEmpty() == true) { mutableStateOf(
        when { detail?.episodes?.isNotEmpty() == true -> DetailSection.Episodes
            detail?.recommendations?.isNotEmpty() == true -> DetailSection.Related
            else -> DetailSection.Information }) }
    val selected = seasonChoice.selected(seasons, resume)
    val entrance = rememberListEntrance(detail?.episodes?.isNotEmpty() == true)
    AutomaticEpisodes(selected, detail?.next_cursor, busy, loadPage, enabled = paging && section == DetailSection.Episodes,
        scopeKey = detail?.item?.let { it.provider_id to it.identity })
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp
        val compact = maxWidth < 900.dp
        val gutter = if (phone) 16.dp else 40.dp
        val listState = rememberLazyListState()
        val desktop = !phone && !tablet
        val episodesShown = detail != null && (!desktop && section == DetailSection.Episodes || desktop && seasons.isNotEmpty())
        val landing = if (!episodesShown) null else (resume.indexIn(selected?.episodes) ?: focus.episodeIn(selected))
        val resumeIndex = landing?.let { episode ->
            (if (busy) 1 else 0) + (if (error != null) 1 else 0) + 1 + (if (desktop) 0 else 1) + 1 + episode }
        LandOnResume(seasonChoice, listState, resumeIndex, selected, clearance = if (phone) 56.dp else 0.dp)
        val reveal = rememberContentReveal(!busy)
        val scrolled by remember(phone) { derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 240 } }
        LazyColumn(Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(top = if (phone) 0.dp else 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(if (phone) 18.dp else 24.dp)) {
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = gutter), color = Palette.accent) }
            detail?.let { value ->
                item(key = "hero") {
                    if (phone) PhoneDetailHero(value.item, sources, save, saved, heroEpisode)
                    else if (desktop) Box(Modifier.padding(bottom = 20.dp)) {
                        DesktopDetailHero(value.item, sources, save, saved, playFromStart, chooseSource = sources,
                            sourceLabel = defaultSourceLabel, resumeEpisode = heroEpisode)
                    } else Box(Modifier.padding(horizontal = gutter)) { DetailHero(value.item, compact, sources, save, saved, playFromStart) }
                }
                when {
                    !busy && error != null -> item(key = "failure") {
                        DetailInlineFailure(error, retry, Modifier.padding(horizontal = gutter, vertical = if (phone) 64.dp else 0.dp))
                    }
                    busy -> item(key = "skeleton") {
                        Box(Modifier.padding(horizontal = gutter)) { DetailBelowSkeleton(phone, desktop) }
                    }
                    else -> {
                        if (!desktop) item(key = "sections") {
                            DetailSections(section, seasons.isNotEmpty(), value.recommendations.isNotEmpty(),
                                { section = it }, Modifier.padding(horizontal = gutter).graphicsLayer { alpha = reveal.value })
                        }
                        if (!desktop && section == DetailSection.Information) item(key = "information") {
                            Column(Modifier.padding(horizontal = gutter).graphicsLayer { alpha = reveal.value }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                org.viptv.app.components.MediaMetadata(value.item)
                                value.item.description?.let { Text(it, color = Palette.body, style = MaterialTheme.typography.bodyLarge) }
                            }
                        }
                        if ((!desktop && section == DetailSection.Episodes || desktop && seasons.isNotEmpty())) selected?.let { season ->
                            item {
                                Box(Modifier.padding(if (desktop) PaddingValues(start = 40.dp, end = 40.dp) else PaddingValues(horizontal = gutter)).graphicsLayer { alpha = reveal.value }) {
                                    DetailSeasonPicker(seasons, season, phone) { number ->
                                        seasonChoice.pick(number)
                                        seasons.find { it.number == number }?.nextOffset?.let(loadPage)
                                    }
                                }
                            }
                            if (phone) itemsIndexed(season.episodes, key = { _, episode -> episodeKey(episode) }) { index, episode ->
                                DetailEpisodeCard(resume.shown(episode), true, { sources(episode) }, Modifier.fillMaxWidth().padding(horizontal = gutter).listItemEntrance(index, entrance.animate).graphicsLayer { alpha = reveal.value })
                            } else itemsIndexed(season.episodes, key = { _, episode -> episodeKey(episode) }) { index, episode ->
                                DesktopEpisodeRow(resume.shown(episode), { sources(episode) }, modifier = Modifier.padding(if (desktop) PaddingValues(start = 40.dp, end = 40.dp) else PaddingValues(horizontal = gutter)).listItemEntrance(index, entrance.animate).graphicsLayer { alpha = reveal.value })
                            }
                        }
                        if (value.recommendations.isNotEmpty() && (!desktop && section == DetailSection.Related || desktop)) item(key = "related") {
                            Column(Modifier.padding(bottom = if (desktop) 24.dp else 0.dp).graphicsLayer { alpha = reveal.value },
                                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                if (desktop) Text("More like this", Modifier.padding(horizontal = gutter), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                RelatedTitles(value.recommendations, open, Modifier.fillMaxWidth(), showHeading = !desktop, gutter = gutter)
                            }
                        }
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


internal fun seriesResumeEpisode(detail: DetailSnapshot): Card? {
    if (detail.item.identity.kind != "series") return null
    return detail.episodes.firstOrNull {
        val coordinates = it.identity.episode
        coordinates != null && (coordinates.season ?: 1L) > 0L && coordinates.episode > 0L && resuming(it)
    }
}

internal fun remainingMinutesLabel(card: Card): String? {
    val progress = card.progress ?: 0.0
    val runtime = card.metadata?.runtime_minutes ?: return null
    val remaining = kotlin.math.round((1.0 - progress) * runtime)
    return if (remaining > 0) "${remaining}m left" else "Almost done"
}

internal fun detailExtras(detail: DetailSnapshot?, continuing: List<Card>): TvDetailExtras {
    if (detail == null) return TvDetailExtras()
    val resume = detailResume(detail, continuing).heroEpisode(detail) ?: detail.item.takeIf(::resuming)
    return TvDetailExtras(resume = resume, resumeLabel = resume?.let(::heroPlayLabel),
        remaining = resume?.takeIf(::resuming)?.let(::remainingMinutesLabel), recommendations = detail.recommendations)
}


@Composable
private fun DetailUnavailable(error: String?, back: () -> Unit, retry: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text("This title couldn’t load", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(error ?: "The provider didn’t return this title’s details.", color = Palette.secondary)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            retry?.let { again -> Button(onClick = again) { Text("Try again") } }
            Button(onClick = back) { Text("Go back") }
        }
    }
}
