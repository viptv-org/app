package org.viptv.app.features.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.DetailSnapshot
import coil3.compose.AsyncImage

@Composable
fun DetailsScreen(detail: DetailSnapshot?, busy: Boolean, error: String?, back: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { TextButton(onClick = back) { Text("Back") } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
        error?.let { item { Text(it, color = Palette.danger) } }
        detail?.let { value ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AsyncImage(value.item.landscape, null, Modifier.fillMaxWidth().heightIn(max = 400.dp))
                    Text(value.item.title, style = MaterialTheme.typography.headlineLarge)
                    value.item.subtitle?.let { Text(it, color = Palette.secondary) }
                    value.item.description?.let { Text(it, color = Palette.secondary) }
                }
            }
            if (value.episodes.isNotEmpty()) item { Text("Episodes", style = MaterialTheme.typography.titleLarge) }
            items(value.episodes, key = { it.identity.id }) { episode ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    AsyncImage(episode.landscape, null, Modifier.width(200.dp).aspectRatio(16f / 9))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(episode.title, style = MaterialTheme.typography.titleMedium)
                        episode.description?.let { Text(it, color = Palette.secondary, maxLines = 3) }
                    }
                }
            }
        }
    }
}
