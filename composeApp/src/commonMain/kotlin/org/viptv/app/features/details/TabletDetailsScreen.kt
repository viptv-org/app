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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.MediaMetadata
import org.viptv.app.components.TitleLogo
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.app.theme.Scrim
import org.viptv.app.connection.DetailFocus
import org.viptv.engine.wire.AddonFailure
import org.viptv.app.connection.epochMillis
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.app.components.Button
import org.viptv.app.components.AddonFailureNotice
import org.viptv.app.components.BackControl
import org.viptv.app.components.IconButton
import org.viptv.app.format.providerText

@Composable
internal fun TabletDetailsScreen(detail: DetailSnapshot?, busy: Boolean, error: String?, back: () -> Unit, play: (Card) -> Unit, save: (Card) -> Unit = {}, saved: Boolean = false, open: (Card) -> Unit = play, loadPage: (Long) -> Unit = {},
    continuing: List<Card> = emptyList(), paging: Boolean = true, focus: DetailFocus? = null, pageError: String? = null, retryPage: (() -> Unit)? = null,
    manageAddons: () -> Unit = {}, failures: List<AddonFailure> = emptyList(), playFromStart: ((Card) -> Unit)? = null) {
    val seasons = remember(detail?.episodes, detail?.seasons) { detailSeasons(detail?.episodes.orEmpty(), detail?.seasons.orEmpty()) }
    val airDateNow = remember { epochMillis() }
    val latestReleased = remember(detail?.episodes) { lastReleasedOrder(detail?.episodes.orEmpty(), airDateNow) }
    val resume = remember(detail, continuing) { detailResume(detail, continuing) }
    val heroEpisode = remember(resume, detail) { detail?.let(resume::heroEpisode) }
    val seasonChoice = rememberSeasonChoice(detail, focus)
    val selected = seasonChoice.selected(seasons, resume)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < 800.dp) {
            DetailsScreen(detail, busy, error, back, play, save = save, saved = saved, open = open, loadPage = loadPage,
                continuing = continuing, paging = paging, focus = focus, pageError = pageError, retryPage = retryPage,
                playFromStart = playFromStart)
            return@BoxWithConstraints
        }
        AutomaticEpisodes(selected, detail?.next_cursor, busy, loadPage, enabled = paging, scopeKey = detail?.item?.let { it.provider_id to it.identity })
        val episodesList = rememberDetailListState(detail?.item)
        val landing = (if (focus != null) resume.indexIn(selected?.episodes) else null) ?: focus.episodeIn(selected)
        LandOnResume(seasonChoice, episodesList, landing?.plus(1), selected)
        val paneWidth = maxWidth
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxHeight().then(if (selected == null) Modifier.fillMaxWidth()
                else Modifier.width(if (paneWidth >= 1100.dp) 560.dp else paneWidth * .5f))) {
                detail?.item?.let { card ->
                    AsyncImage(card.landscape ?: card.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Scrim.tabletDetailPane))
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.Bottom),
                        contentPadding = PaddingValues(top = 72.dp, bottom = 36.dp)) {
                        item {
                            Column(Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                TitleLogo(card.logo, card.title, logoModifier = Modifier.width(300.dp).height(92.dp)) {
                                    Text(card.title, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                                }
                                MediaMetadata(card, kindFirst = true)
                                card.description.providerText()?.let { Text(it, fontSize = 15.sp, lineHeight = 22.sp, color = Palette.body) }
                                Button(onClick = { play(heroEpisode ?: card) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                                    Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp)); Text(heroPlayLabel(heroEpisode), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                HeroRestart(card, heroEpisode, playFromStart, fontSize = 15.sp)
                                FavoriteAction(card, saved, save)
                            }
                        }
                        if (failures.isNotEmpty() && !busy) item(key = "addon-failure") {
                            AddonFailureNotice(failures, manageAddons, Modifier.padding(horizontal = 32.dp))
                        }
                        if (detail.recommendations.isNotEmpty()) item(key = "related") { RelatedTitles(detail.recommendations, open, gutter = 32.dp) }
                    }
                }
                BackControl(back, Modifier.offset(x = 24.dp, y = 36.dp), glass = true)
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
                        DetailEpisodeCard(resume.shown(episode), true, { play(episode) }, Modifier.fillMaxWidth(), tablet = true, comingUp = episodeUpcoming(episode, latestReleased, airDateNow))
                    }
                    if (pageError != null) item(key = "page-failure") {
                        DetailInlineFailure(pageError, retryPage, Modifier.padding(top = 16.dp))
                    }
                }
            }
            if (selected == null && busy) {
                Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 28.dp, vertical = 40.dp)) {
                    DetailBelowSkeleton(phone = false, desktop = false)
                }
            }
        }
    }
}
