package org.viptv.app.features.details

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.MediaMetadata
import org.viptv.app.components.TitleLogo
import org.viptv.app.components.requestFocusWhenPlaced
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.app.theme.Scrim
import org.viptv.app.connection.DetailFocus
import org.viptv.engine.wire.AddonFailure
import org.viptv.app.connection.epochMillis
import org.viptv.app.format.providerText
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.app.components.Button
import org.viptv.app.components.AddonFailureNotice
import org.viptv.app.components.AccessibleIconButton
import org.viptv.app.components.Motion
import org.viptv.app.components.motionSpec
import org.viptv.app.components.rememberEnterLeaveMotion

@Composable
internal fun BrowserDetailsDialog(detail: DetailSnapshot?, busy: Boolean, error: String?, back: () -> Unit, play: (Card) -> Unit, save: (Card) -> Unit = {}, saved: Boolean = false, open: (Card) -> Unit = play, loadPage: (Long) -> Unit = {},
    continuing: List<Card> = emptyList(), paging: Boolean = true, closeFocusOnOpen: Boolean = true, focus: DetailFocus? = null, pageError: String? = null, retryPage: (() -> Unit)? = null,
    manageAddons: () -> Unit = {}, failures: List<AddonFailure> = emptyList(), playFromStart: ((Card) -> Unit)? = null, chooseSource: (Card) -> Unit = play) {
    var dismissing by remember(detail?.item?.let { it.provider_id to it.identity }) { mutableStateOf(false) }
    val requestDismiss = { if (!dismissing) dismissing = true }
    InitialBrowserEscape(requestDismiss)
    val closeFocus = remember { FocusRequester() }
    val seasons = remember(detail?.episodes, detail?.seasons) { detailSeasons(detail?.episodes.orEmpty(), detail?.seasons.orEmpty()) }
    val airDateNow = remember { epochMillis() }
    val latestReleased = remember(detail?.episodes) { lastReleasedOrder(detail?.episodes.orEmpty(), airDateNow) }
    val resume = remember(detail, continuing) { detailResume(detail, continuing) }
    val heroEpisode = remember(resume, detail) { detail?.let(resume::heroEpisode) }
    val seasonChoice = rememberSeasonChoice(detail, focus)
    val selected = seasonChoice.selected(seasons, resume)
    AutomaticEpisodes(selected, detail?.next_cursor, busy, loadPage, enabled = paging, scopeKey = detail?.item?.let { it.provider_id to it.identity })
    val list = rememberDetailListState(detail?.item)
    val landing = if (detail != null) (if (focus != null) resume.indexIn(selected?.episodes) else null) ?: focus.episodeIn(selected) else null
    LandOnResume(seasonChoice, list, landing?.let { episode ->
        (if (busy) 1 else 0) + (if (error != null) 1 else 0) + 2 +
            (if (failures.isNotEmpty() && !busy && error == null) 1 else 0) + 1 + episode }, selected)
    Dialog(onDismissRequest = requestDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focusManager = LocalFocusManager.current
        LaunchedEffect(closeFocusOnOpen) {
            if (!closeFocusOnOpen) focusManager.clearFocus(force = true)
        }
        BoxWithConstraints(Modifier.widthIn(max = 832.dp).fillMaxWidth().fillMaxHeight().padding(vertical = 32.dp, horizontal = 16.dp), contentAlignment = Alignment.TopCenter) {
            val compact = maxWidth < 640.dp
            val gutter = if (compact) 20.dp else 44.dp
            val motion = rememberEnterLeaveMotion(!dismissing,
                enter = motionSpec(tween(Motion.arrive, easing = Motion.standard)),
                leave = motionSpec(tween(Motion.leave, easing = Motion.exit)))
            LaunchedEffect(motion.presented) { if (!motion.presented) back() }
            Surface(Modifier.graphicsLayer {
                    alpha = motion.progress
                    val s = 0.98f + 0.02f * motion.progress
                    scaleX = s; scaleY = s
                }.widthIn(max = 800.dp).fillMaxWidth().fillMaxHeight().onPreviewKeyEvent { event ->
                    // One step per press: the key-down dismisses and neither half reaches the page's Back as well.
                    if (event.key == Key.Escape) { if (event.type == KeyEventType.KeyDown) requestDismiss(); true } else false
                }.semantics { paneTitle = detail?.item?.title ?: "Title details" },
                color = Palette.dialog, shape = RoundedCornerShape(14.dp), shadowElevation = 24.dp) {
                Box {
                    LazyColumn(state = list, contentPadding = PaddingValues(bottom = 30.dp), topEdgeFade = false) {
                        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
                        detail?.item?.let { card ->
                            item(key = "hero") { BrowserDetailHero(card, gutter, play, save, saved, heroEpisode, playFromStart) }
                            when {
                                !busy && error != null -> item(key = "failure") { DetailInlineFailure(error, null, Modifier.padding(gutter)) }
                                busy -> item(key = "skeleton") { Box(Modifier.padding(gutter)) { DetailBelowSkeleton(compact, !compact) } }
                                else -> {
                                    item(key = "information") { Column(Modifier.padding(horizontal = gutter, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        MediaMetadata(card)
                                        card.description.providerText()?.let { Text(it, fontSize = 15.sp, lineHeight = 22.sp, color = Palette.body) }
                                        HeroStarring(card, 14.sp, 20.sp)
                                    } }
                                    if (failures.isNotEmpty()) item(key = "addon-failure") {
                                        AddonFailureNotice(failures, manageAddons, Modifier.padding(horizontal = gutter))
                                    }
                                    selected?.let { season ->
                                        item { Box(Modifier.padding(horizontal = gutter, vertical = 10.dp)) {
                                            DetailSeasonPicker(seasons, season, compact) {
                                                seasonChoice.pick(it)
                                                seasons.find { group -> group.number == it }?.nextOffset?.let(loadPage)
                                            }
                                        } }
                                        items(season.episodes, key = ::episodeKey) { episode ->
                                            val shown = resume.shown(episode)
                                            if (compact) DetailEpisodeCard(shown, true, { chooseSource(episode) }, Modifier.padding(horizontal = gutter, vertical = 12.dp), comingUp = episodeUpcoming(episode, latestReleased, airDateNow))
                                            else DesktopEpisodeRow(shown, { chooseSource(episode) }, browser = true, modifier = Modifier.padding(horizontal = 32.dp), comingUp = episodeUpcoming(episode, latestReleased, airDateNow))
                                        }
                                    }
                                    if (pageError != null) item(key = "page-failure") {
                                        DetailInlineFailure(pageError, retryPage, Modifier.padding(horizontal = gutter))
                                    }
                                    if (detail?.recommendations?.isNotEmpty() == true) item(key = "related") { RelatedTitles(detail.recommendations, open, Modifier.padding(vertical = gutter), gutter = gutter) }
                                }
                            }
                        }
                    }
                    AccessibleIconButton(Lucide.X, "Close details (Esc)", requestDismiss,
                        Modifier.align(Alignment.TopEnd).padding(16.dp).then(closeFocus.requestFocusWhenPlaced(closeFocusOnOpen)),
                        iconSize = 20.dp, wash = Color.White.copy(alpha = .12f))
                }
            }
        }
    }
}

@Composable
private fun BrowserDetailHero(card: Card, gutter: androidx.compose.ui.unit.Dp, play: (Card) -> Unit, save: (Card) -> Unit, saved: Boolean,
    resumeEpisode: Card?, playFromStart: ((Card) -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(maxOf(260.dp, maxWidth * (8.2f / 16f)))) {
            AsyncImage(card.landscape ?: card.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Scrim.webDetailHero))
            Column(Modifier.align(Alignment.BottomStart).padding(start = gutter, end = gutter, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TitleLogo(card.logo, card.title, logoModifier = Modifier.widthIn(max = 340.dp).fillMaxWidth().height(96.dp)) {
                    Text(card.title, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { play(resumeEpisode ?: card) }, modifier = Modifier.width(230.dp).height(44.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                    Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp)); Text(heroPlayLabel(resumeEpisode), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                HeroRestart(card, resumeEpisode, playFromStart, fontSize = 15.sp)
                FavoriteAction(card, saved, save)
            }
        }
    }
}
