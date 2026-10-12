package org.viptv.app.features.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

@Composable
fun SourcesScreen(title: Card?, sources: List<SourceCandidate>, busy: Boolean, error: String?, choose: (SourceCandidate) -> Unit, back: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = back) { Text("Back") } }
        item { Text("Choose a source", style = MaterialTheme.typography.headlineLarge) }
        title?.let { item { Text(it.title, color = Palette.secondary) } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
        error?.let { item { Text(it, color = Palette.danger) } }
        if (!busy && sources.isEmpty() && error == null) item {
            Text("No direct streams found. Enable an add-on that supplies sources.", color = Palette.secondary)
        }
        items(sources, key = { it.id }) { source ->
            Column(Modifier.fillMaxWidth().clickable { choose(source) }.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(source.label, style = MaterialTheme.typography.titleMedium)
                source.detail?.let { Text(it, color = Palette.secondary, maxLines = 2) }
                HorizontalDivider(color = Palette.raised)
            }
        }
    }
}
