package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    choose: (SourceCandidate) -> Unit, back: () -> Unit, detail: DetailSnapshot? = null, television: Boolean = false, tablet: Boolean = false, loadingMore: Boolean = false) {
    if (television) {
        TvSourcesScreen(title, sources, busy, error, choose, back, detail, loadingMore = loadingMore)
        return
    }
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
                Text(listOfNotNull(title?.title, if (busy) { if (sources.isEmpty()) "Loading sources…" else "Opening stream…" } else sourceSummary(sources)).joinToString(" · "),
                    color = Palette.secondary, fontSize = if (television) 20.sp else 13.sp)
            }
            if (busy || loadingMore) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = if (television) 48.dp else 0.dp,
                vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(if (television) 10.dp else 0.dp)) {
                error?.let { item { Text(it, Modifier.padding(20.dp), color = Palette.danger) } }
                if (!busy && !loadingMore && sources.isEmpty() && error == null) item {
                    Text("No direct streams found. Enable an add-on that supplies sources.", Modifier.padding(20.dp), color = Palette.secondary)
                }
                items(sources, key = { it.id }) { SourceRow(it, television, choose, enabled = !busy) }
            }
        }
    }
}

internal fun sourceSummary(sources: List<SourceCandidate>): String {
    val providers = sources.map { it.provider_id }.distinct().size
    return "${sources.size} ${if (sources.size == 1) "source" else "sources"} from $providers ${if (providers == 1) "add-on" else "add-ons"}"
}
