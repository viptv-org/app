package org.viptv.app.features.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import coil3.compose.AsyncImage

@Composable
fun DetailsScreen(detail: DetailSnapshot?, busy: Boolean, error: String?, back: () -> Unit, sources: (Card) -> Unit, television: Boolean = false) {
    if (television) {
        TvDetailsScreen(detail, busy, error, sources)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp
        val compact = maxWidth < 900.dp
        val gutter = if (phone) 16.dp else 40.dp
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = gutter, end = gutter, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                IconButton(onClick = back) { Icon(Lucide.ChevronLeft, "Back", Modifier.size(22.dp)) }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
            error?.let { item { Text(it, color = Palette.danger) } }
            detail?.let { value ->
                item { DetailHero(value.item, compact, sources) }
                if (value.episodes.isNotEmpty()) item { Text("Episodes", style = MaterialTheme.typography.titleLarge) }
                items(value.episodes, key = { it.identity.id }) { episode ->
                    Row(Modifier.fillMaxWidth().clickable { sources(episode) }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AsyncImage(episode.landscape ?: episode.poster, episode.title,
                            Modifier.width(if (phone) 120.dp else 200.dp).aspectRatio(16f / 9)
                                .clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(episode.title, style = MaterialTheme.typography.titleMedium)
                            episode.subtitle?.let { Text(it, color = Palette.secondary) }
                            episode.description?.let { Text(it, color = Palette.secondary, maxLines = if (compact) 2 else 3) }
                        }
                    }
                }
            }
        }
    }
}
