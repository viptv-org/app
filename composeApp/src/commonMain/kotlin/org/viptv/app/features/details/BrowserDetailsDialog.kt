package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.*
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
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot

@Composable
internal fun BrowserDetailsDialog(detail: DetailSnapshot?, busy: Boolean, error: String?, back: () -> Unit, play: (Card) -> Unit, save: (Card) -> Unit = {}, saved: Boolean = false) {
    InitialBrowserEscape(back)
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { closeFocus.requestFocus() }
    val seasons = remember(detail?.episodes) { detailSeasons(detail?.episodes.orEmpty()) }
    var selectedKey by remember(detail?.item?.identity) { mutableStateOf<String?>(null) }
    val selected = seasons.find { (it.number?.toString() ?: "unknown") == selectedKey }
        ?: seasons.firstOrNull { (it.number ?: 0) > 0 } ?: seasons.firstOrNull()
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.widthIn(max = 832.dp).fillMaxWidth().fillMaxHeight().padding(vertical = 32.dp, horizontal = 16.dp), contentAlignment = Alignment.TopCenter) {
            val compact = maxWidth < 640.dp
            val gutter = if (compact) 20.dp else 44.dp
            Surface(Modifier.widthIn(max = 800.dp).fillMaxWidth().fillMaxHeight().onPreviewKeyEvent { event ->
                    if (event.key == Key.Escape && event.type == KeyEventType.KeyUp) { back(); true } else false
                }.semantics { paneTitle = detail?.item?.title ?: "Title details" },
                color = Color(0xFF181818), shape = RoundedCornerShape(14.dp), shadowElevation = 24.dp) {
                Box {
                    LazyColumn(contentPadding = PaddingValues(bottom = 30.dp)) {
                        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
                        error?.let { item { Text(it, Modifier.padding(gutter), color = Palette.danger) } }
                        detail?.item?.let { card ->
                            item { BrowserDetailHero(card, gutter, play, save, saved) }
                            item { Column(Modifier.padding(horizontal = gutter, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                MediaMetadata(card)
                                card.description?.let { Text(it, fontSize = 15.sp, lineHeight = 22.sp, color = Palette.body) }
                            } }
                        }
                        selected?.let { season ->
                            item { Box(Modifier.padding(horizontal = gutter, vertical = 10.dp)) {
                                DetailSeasonPicker(seasons, season, compact) { selectedKey = it?.toString() ?: "unknown" }
                            } }
                            items(season.episodes, key = ::episodeKey) { episode ->
                                if (compact) DetailEpisodeCard(episode, true, play, Modifier.padding(horizontal = gutter, vertical = 12.dp))
                                else DesktopEpisodeRow(episode, play, browser = true, modifier = Modifier.padding(horizontal = 32.dp))
                            }
                        }
                    }
                    IconButton(onClick = back, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(38.dp).focusRequester(closeFocus)
                        .background(Color.White.copy(alpha = .12f), CircleShape)) { Icon(Lucide.X, "Close details (Esc)", Modifier.size(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BrowserDetailHero(card: Card, gutter: androidx.compose.ui.unit.Dp, play: (Card) -> Unit, save: (Card) -> Unit, saved: Boolean) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 8.2f).heightIn(min = 260.dp)) {
        AsyncImage(card.landscape ?: card.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.45f to Color.Transparent, 1f to Color(0xFF181818))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = gutter, end = gutter, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (card.logo != null) AsyncImage(card.logo, card.title, Modifier.widthIn(max = 340.dp).fillMaxWidth().height(96.dp), alignment = Alignment.CenterStart, contentScale = ContentScale.Fit)
            else Text(card.title, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { play(card) }, modifier = Modifier.width(230.dp).height(44.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp)); Text("Play", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            FavoriteAction(card, saved, save)
        }
    }
}
