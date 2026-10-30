package org.viptv.app.features.sources

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.viptv.app.connection.SourceGroups
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.app.features.details.TvDetailExtras
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.IconButton

@Composable
fun SourcesScreen(title: Card?, sources: List<SourceCandidate>, busy: Boolean, error: String?,
    choose: (SourceCandidate) -> Unit, back: () -> Unit, detail: DetailSnapshot? = null, television: Boolean = false, tablet: Boolean = false, loadingMore: Boolean = false, selectedSource: String? = null, torrentOnlyProviders: Int = 0,
    openingStage: String? = null, groups: SourceGroups = SourceGroups()) {
    if (television) {
        TvSourcesScreen(title, sources, busy, error, choose, back,
            info = sources.associate { it.id to TvSourceInfo(quality = it.qualityLabel(), format = it.formatLabel(), addon = it.providerLabel().takeUnless { name -> name == it.label }) },
            detail = detail,
            loadingMore = loadingMore, selectedSource = selectedSource, torrentOnlyProviders = torrentOnlyProviders, openingStage = openingStage,
            groups = groups)
        return
    }
    var quality by remember { mutableStateOf<String?>(null) }
    var provider by remember { mutableStateOf<String?>(null) }
    val visible = sources.filter { (quality == null || it.qualityLabel() == quality) && (provider == null || it.sourceFilterId() == provider) }
    val rows = remember(visible, groups) { sourceListRows(visible, groups) }
    val listState = rememberLazyListState()
    var focusedSource by remember { mutableStateOf<String?>(null) }
    val headerHeight = remember { SourceHeaderHeight() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp && !television
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).focusProperties { canFocus = false }.pointerClickable(onClick = back))
        val panel = when {
            phone -> Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.84f)
            television -> Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.5f)
            tablet -> Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 461.dp).fillMaxWidth()
            else -> Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(sourceDrawerWidth(maxWidth))
        }
        Column(panel.clip(RoundedCornerShape(topStart = if (phone) 24.dp else 0.dp,
            topEnd = if (phone) 24.dp else 0.dp)).background(Palette.surface).semantics { paneTitle = "Choose a source" }) {
            if (phone) Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 4.dp)
                .width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.secondary.copy(alpha = 0.4f)))
            Column(Modifier.padding(horizontal = if (television) 48.dp else 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Choose a source", Modifier.weight(1f), fontSize = if (television) 36.sp else 18.sp,
                        fontWeight = FontWeight.Bold)
                    IconButton(onClick = back, modifier = Modifier.size(32.dp)) { Icon(Lucide.X, "Close", Modifier.size(18.dp)) }
                }
                val summary = when {
                    busy -> if (sources.isEmpty()) "Loading sources…" else openingStage ?: "Opening stream…"
                    sources.isEmpty() && loadingMore -> "Loading sources…"
                    else -> sourceSummary(sources)
                }
                Text(listOfNotNull(sourceHeading(title, detail), summary).joinToString(" · "),
                    color = Palette.secondary, fontSize = if (television) 20.sp else 13.sp)
            }
            SourceFilters(sources, quality, provider, { quality = it }, { provider = it })
            if (busy || loadingMore) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
            KeepSourceListPosition(listState, rows, focusedSource, headerHeight, leading = if (error != null) 1 else 0)
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(horizontal = if (television) 48.dp else 0.dp,
                vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(if (television) 10.dp else 0.dp), topEdgeFade = false) {
                error?.let { item { Text(it, Modifier.padding(20.dp), color = Palette.danger) } }
                if (!busy && !loadingMore && visible.isEmpty() && error == null) item {
                    val empty = if (torrentOnlyProviders > 0)
                        "${torrentOnlyProviders} ${if (torrentOnlyProviders == 1) "provider returned torrent links this device cannot stream directly" else "providers returned torrent links this device cannot stream directly"}. Add a debrid service (for example TorBox) to play them, or try another title."
                    else "No streams found. Enable a provider that supplies sources."
                    Text(empty, Modifier.padding(20.dp), color = Palette.secondary)
                }
                items(rows, key = { it.source.id }) { row ->
                    SourceRow(row.source, television, choose, enabled = !busy, selected = row.source.id == selectedSource,
                        header = row.header, bestMatch = row.bestMatch, headerMeasured = { headerHeight.pixels = it },
                        focusChanged = { has -> if (has) focusedSource = row.source.id else if (focusedSource == row.source.id) focusedSource = null })
                }
            }
        }
    }
}

internal fun sourceDrawerWidth(window: Dp): Dp = minOf(window, maxOf(460.dp, minOf(720.dp, window * 0.55f)))

internal fun sourceSummary(sources: List<SourceCandidate>): String {
    val addons = sources.map { it.sourceFilterId() }.distinct().size
    return "${sources.size} ${if (sources.size == 1) "source" else "sources"} from $addons ${if (addons == 1) "add-on" else "add-ons"}"
}
