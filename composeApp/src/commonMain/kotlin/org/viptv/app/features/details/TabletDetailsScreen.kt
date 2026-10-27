package org.viptv.app.features.details

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.MediaMetadata
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.app.components.Button
import org.viptv.app.components.IconButton

@Composable
internal fun TabletDetailsScreen(detail: DetailSnapshot?, busy: Boolean, error: String?, back: () -> Unit, play: (Card) -> Unit, save: (Card) -> Unit = {}, saved: Boolean = false, open: (Card) -> Unit = play, loadPage: (Long) -> Unit = {},
    continuing: List<Card> = emptyList(), paging: Boolean = true) {
    val seasons = remember(detail?.episodes, detail?.seasons) { detailSeasons(detail?.episodes.orEmpty(), detail?.seasons.orEmpty()) }
    val resume = remember(detail, continuing) { detailResume(detail, continuing) }
    val seasonChoice = rememberSeasonChoice(detail)
    val selected = seasonChoice.selected(seasons, resume)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < 800.dp) {
            DetailsScreen(detail, busy, error, back, play, save = save, saved = saved, open = open, loadPage = loadPage,
                continuing = continuing, paging = paging)
            return@BoxWithConstraints
        }
        AutomaticEpisodes(selected, detail?.next_cursor, busy, loadPage, enabled = paging, scopeKey = detail?.item?.let { it.provider_id to it.identity })
        val episodesList = rememberLazyListState()
        // The episode pane lists the season picker, then the season's episodes.
        LandOnResume(seasonChoice, episodesList, resume.indexIn(selected?.episodes)?.plus(1), selected)
        val paneWidth = maxWidth
        Row(Modifier.fillMaxSize()) {
            // T3Title: fixed 560 px info pane at board width, proportional when narrower.
            Box(Modifier.fillMaxHeight().then(if (selected == null) Modifier.fillMaxWidth()
                else Modifier.width(if (paneWidth >= 1100.dp) 560.dp else paneWidth * .5f))) {
                detail?.item?.let { card ->
                    AsyncImage(card.landscape ?: card.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Palette.background.copy(alpha = .3f),
                        .3f to Color.Transparent, .7f to Palette.background.copy(alpha = .95f))))
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom,
                        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 72.dp, bottom = 36.dp)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (card.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(card.logo), card.title, Modifier.width(300.dp).height(92.dp),
                                    alignment = Alignment.CenterStart, contentScale = ContentScale.Fit)
                                else Text(card.title, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                                MediaMetadata(card)
                                card.description?.let { Text(it, fontSize = 15.sp, lineHeight = 22.sp, color = Palette.body) }
                                Button(onClick = { play(card) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                                    Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp)); Text("Play", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                FavoriteAction(card, saved, save)
                            }
                        }
                        if (detail.recommendations.isNotEmpty()) item(key = "related") { RelatedTitles(detail.recommendations, open) }
                    }
                }
                IconButton(onClick = back, modifier = Modifier.padding(start = 24.dp, top = 36.dp).size(44.dp)
                    .background(Color.White.copy(alpha = .12f), CircleShape)) { Icon(Lucide.ChevronLeft, "Back", Modifier.size(22.dp)) }
                if (busy) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Palette.accent)
                error?.let { Text(it, Modifier.align(Alignment.TopCenter).padding(top = 72.dp, start = 32.dp, end = 32.dp), color = Palette.danger) }
            }
            selected?.let { season ->
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = episodesList, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { DetailSeasonPicker(seasons, season, false) {
                        seasonChoice.pick(it)
                        seasons.find { group -> group.number == it }?.nextOffset?.let(loadPage)
                    } }
                    items(season.episodes, key = ::episodeKey) { episode ->
                        DetailEpisodeCard(resume.shown(episode), true, { play(episode) }, Modifier.fillMaxWidth(), tablet = true)
                    }
                }
            }
        }
    }
}
