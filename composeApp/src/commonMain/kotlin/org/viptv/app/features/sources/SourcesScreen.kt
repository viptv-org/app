package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.SourceCandidate

@Composable
fun SourcesScreen(title: Card?, sources: List<SourceCandidate>, busy: Boolean, error: String?,
    choose: (SourceCandidate) -> Unit, back: () -> Unit, detail: DetailSnapshot? = null, television: Boolean = false, tablet: Boolean = false, loadingMore: Boolean = false, selectedSource: String? = null, torrentOnlyProviders: Int = 0) {
    if (television) {
        TvSourcesScreen(title, sources, busy, error, choose, back, detail,
            info = sources.associate { it.id to TvSourceInfo(quality = it.qualityLabel(), format = it.formatLabel(), addon = it.providerLabel().takeUnless { name -> name == it.label }) },
            loadingMore = loadingMore, selectedSource = selectedSource, torrentOnlyProviders = torrentOnlyProviders)
        return
    }
    var quality by remember { mutableStateOf<String?>(null) }
    var provider by remember { mutableStateOf<String?>(null) }
    val visible = sources.filter { (quality == null || it.qualityLabel() == quality) && (provider == null || it.sourceFilterId() == provider) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp && !television
        if (detail != null) DetailsScreen(detail, false, null, back, {}, television, tablet)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = back))
        val panel = when {
            phone -> Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.84f)
            television -> Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.5f)
            else -> Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 461.dp).fillMaxWidth()
        }
        Column(panel.clip(RoundedCornerShape(topStart = if (phone) 24.dp else 0.dp,
            topEnd = if (phone) 24.dp else 0.dp)).background(Palette.surface)) {
            if (phone) Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 4.dp)
                .width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.secondary.copy(alpha = 0.4f)))
            Column(Modifier.padding(horizontal = if (television) 48.dp else 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Choose a source", Modifier.weight(1f), fontSize = if (television) 36.sp else 18.sp,
                        fontWeight = FontWeight.Bold)
                    IconButton(onClick = back, modifier = Modifier.size(32.dp)) { Icon(Lucide.X, "Close", Modifier.size(18.dp)) }
                }
                Text(listOfNotNull(sourceHeading(title, detail), if (busy) { if (sources.isEmpty()) "Loading sources…" else "Opening stream…" } else sourceSummary(sources)).joinToString(" · "),
                    color = Palette.secondary, fontSize = if (television) 20.sp else 13.sp)
            }
            SourceFilters(sources, quality, provider, { quality = it }, { provider = it })
            if (busy || loadingMore) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = if (television) 48.dp else 0.dp,
                vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(if (television) 10.dp else 0.dp)) {
                error?.let { item { Text(it, Modifier.padding(20.dp), color = Palette.danger) } }
                if (!busy && !loadingMore && visible.isEmpty() && error == null) item {
                    val empty = if (torrentOnlyProviders > 0)
                        "${torrentOnlyProviders} ${if (torrentOnlyProviders == 1) "provider returned torrent links this device cannot stream directly" else "providers returned torrent links this device cannot stream directly"}. Add a debrid service (for example TorBox) to that add-on’s address, or enable a provider with direct streams."
                    else "No streams found. Enable a provider that supplies sources."
                    Text(empty, Modifier.padding(20.dp), color = Palette.secondary)
                }
                items(visible, key = { it.id }) { SourceRow(it, television, choose, enabled = !busy, selected = it.id == selectedSource) }
            }
            HorizontalDivider(color = Palette.secondary.copy(alpha = .12f))
            Text("Select a source to start playback.", Modifier.padding(horizontal = 20.dp, vertical = 16.dp), color = Palette.secondary, fontSize = 12.sp)
        }
    }
}

internal fun sourceSummary(sources: List<SourceCandidate>): String {
    val providers = sources.map { it.sourceFilterId() }.distinct().size
    return "${sources.size} ${if (sources.size == 1) "source" else "sources"} from $providers ${if (providers == 1) "provider" else "providers"}"
}
