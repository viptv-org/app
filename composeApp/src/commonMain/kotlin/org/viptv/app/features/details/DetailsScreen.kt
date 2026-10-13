package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot

@Composable
fun DetailsScreen(detail: DetailSnapshot?, busy: Boolean, error: String?, back: () -> Unit, sources: (Card) -> Unit, television: Boolean = false, tablet: Boolean = false, browser: Boolean = false, save: (Card) -> Unit = {}, saved: Boolean = false) {
    if (television) {
        TvDetailsScreen(detail, busy, error, sources, save = save, saved = saved)
        return
    }
    if (tablet) { TabletDetailsScreen(detail, busy, error, back, sources, save, saved); return }
    if (browser) { BrowserDetailsDialog(detail, busy, error, back, sources, save, saved); return }
    val seasons = remember(detail?.episodes) { detailSeasons(detail?.episodes.orEmpty()) }
    var seasonKey by remember(detail?.item?.identity) { mutableStateOf<String?>(null) }
    val selected = seasons.find { (it.number?.toString() ?: "unknown") == seasonKey } ?: seasons.firstOrNull { (it.number ?: 0L) > 0L } ?: seasons.firstOrNull()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp
        val compact = maxWidth < 900.dp
        val gutter = if (phone) 16.dp else 40.dp
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = if (phone) 0.dp else 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(if (phone) 18.dp else 24.dp)) {
            if (!phone) item {
                IconButton(onClick = back, modifier = Modifier.padding(horizontal = gutter)) {
                    Icon(Lucide.ChevronLeft, "Back", Modifier.size(22.dp))
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = gutter), color = Palette.accent) }
            error?.let { item { Text(it, Modifier.padding(horizontal = gutter, vertical = if (phone) 64.dp else 0.dp), color = Palette.danger) } }
            detail?.let { value ->
                item {
                    if (phone) PhoneDetailHero(value.item, sources, save, saved)
                    else Box(Modifier.padding(horizontal = gutter)) { DetailHero(value.item, compact, sources, save, saved) }
                }
                selected?.let { season ->
                    item {
                        Box(Modifier.padding(horizontal = gutter)) {
                            DetailSeasonPicker(seasons, season, phone) { seasonKey = it?.toString() ?: "unknown" }
                        }
                    }
                    if (phone) items(season.episodes, key = ::episodeKey) { episode ->
                        DetailEpisodeCard(episode, true, sources, Modifier.fillMaxWidth().padding(horizontal = gutter))
                    } else items(season.episodes, key = ::episodeKey) { episode ->
                        DesktopEpisodeRow(episode, sources, modifier = Modifier.padding(horizontal = gutter))
                    }
                }
            }
        }
        if (phone) IconButton(onClick = back, modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
            .size(40.dp).background(Color.White.copy(alpha = .12f), CircleShape)) {
            Icon(Lucide.ChevronLeft, "Back", Modifier.size(22.dp))
        }
    }
}
