package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.components.rememberTvDrawerMotion
import org.viptv.app.components.rememberTvLoadingShimmer
import org.viptv.app.features.details.TvDetailsScreen
import org.viptv.app.features.details.TvDetailExtras
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.*
import kotlinx.coroutines.launch

@androidx.compose.runtime.Immutable
internal data class TvSourceInfo(val quality: String? = null, val format: String? = null, val addon: String? = null, val bestMatch: Boolean = false)

@Composable
internal fun TvSourcesScreen(title: Card?, sources: List<SourceCandidate>, busy: Boolean, error: String?,
    choose: (SourceCandidate) -> Unit, back: () -> Unit, info: Map<String, TvSourceInfo> = emptyMap(),
    detail: DetailSnapshot? = null, loadingMore: Boolean = false, selectedSource: String? = null, torrentOnlyProviders: Int = 0) {
    // The real details page stays composed behind this drawer.
    val motion = rememberTvDrawerMotion(back)
    Popup(onDismissRequest = motion.dismiss, properties = PopupProperties(focusable = true)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val scale = maxWidth / 1920.dp
            val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
            var focusedSource by remember { mutableStateOf<String?>(null) }
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val filterFocus = remember { FocusRequester() }
            val providerFocus = remember { FocusRequester() }
            val scope = rememberCoroutineScope()
            var entryJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
            var quality by remember { mutableStateOf<String?>(null) }
            var provider by remember { mutableStateOf<String?>(null) }
            var lastProviderFocus by remember { mutableStateOf<String?>(null) }
            val visible = remember(sources, info, quality, provider) { sources.filter { (quality == null || info[it.id]?.quality == quality) && (provider == null || it.sourceFilterId() == provider) } }
            val finding = loadingMore || busy && sources.isEmpty()
            val shimmer = if (finding) rememberTvLoadingShimmer(scale) else Modifier
            // Re-anchor to the top row when the head changes (arrival or final
            // ranking) unless the user already navigated to another row.
            var autoFocused by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(visible.firstOrNull()?.id, error) {
                if (visible.isEmpty()) return@LaunchedEffect
                if (focusedSource != null && focusedSource != autoFocused) return@LaunchedEffect
                val target = visible.find { it.id == selectedSource } ?: visible.firstOrNull()
                if (target == null) { filterFocus.requestFocus(); return@LaunchedEffect }
                autoFocused = target.id
                val index = visible.indexOf(target) + if (error != null) 1 else 0
                val shown = listState.layoutInfo.visibleItemsInfo.any {
                    it.key == target.id && it.offset >= listState.layoutInfo.viewportStartOffset &&
                        it.offset + it.size <= listState.layoutInfo.viewportEndOffset
                }
                if (!shown) listState.scrollToItem(index)
                withFrameNanos { }
                rowFocus[target.id]?.requestFocus()
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = motion.progress.value }.background(Color.Black.copy(alpha = 0.55f))
                .focusProperties { canFocus = false }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = motion.dismiss))
            Column(Modifier.align(Alignment.CenterEnd).width(960.dp * scale).fillMaxHeight()
                .graphicsLayer { translationX = size.width * (1f - motion.progress.value) }.background(Color(0xFF151515))
                .padding(start = 64.dp * scale, end = 96.dp * scale, top = 72.dp * scale, bottom = 56.dp * scale)) {
                Text("Choose a source", fontSize = (44 * scale).sp, lineHeight = (53 * scale).sp, letterSpacing = (-0.88f * scale).sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp * scale))
                val count = sourceSummary(sources)
                Text(listOfNotNull(sourceHeading(title, detail), if (loadingMore || busy && sources.isEmpty()) "finding sources" else if (busy) "opening stream" else count).joinToString(" · "),
                    fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, color = Palette.secondary)
                Spacer(Modifier.height(22.dp * scale))
                TvSourcesFilters(sources, info, scale, quality, provider, { quality = it }, { provider = it }, filterFocus, providerFocus,
                    focusedProvider = lastProviderFocus, providerFocused = { lastProviderFocus = it }) {
                    if (entryJob?.isActive != true && visible.isNotEmpty()) entryJob = scope.launch {
                        listState.scrollToItem(if (error != null) 1 else 0)
                        withFrameNanos { }
                        rowFocus[visible.first().id]?.requestFocus()
                    }
                }
                Spacer(Modifier.height(20.dp * scale))
                TvFocusScroll {
                LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                    error?.let { item { Text(it, color = Palette.danger, fontSize = (22 * scale).sp) } }
                    itemsIndexed(visible, key = { _, source -> source.id }) { index, source ->
                        TvSourceRow(source, info[source.id], scale, rowFocus.getOrPut(source.id) { FocusRequester() }, true, { if (!busy) choose(it) },
                            up = if (index == 0) providerFocus else null, focusedSource = { focusedSource = it })
                    }
                    if (finding) items(3) {
                        Box(Modifier.fillMaxWidth().height(104.dp * scale).clip(RoundedCornerShape(14.dp * scale)).then(shimmer)
                            .semantics { contentDescription = "Loading source" })
                    }
                    if (!busy && !loadingMore && visible.isEmpty() && error == null) item {
                        val empty = if (torrentOnlyProviders > 0)
                            "${torrentOnlyProviders} ${if (torrentOnlyProviders == 1) "provider returned torrent links this TV cannot stream directly" else "providers returned torrent links this TV cannot stream directly"}. Add a debrid service (for example TorBox) to that add-on’s address, or enable a provider with direct streams."
                        else if (sources.isNotEmpty()) "No sources match these filters."
                        else "No sources found. Enable a provider that supplies streams."
                        Text(empty, color = Palette.secondary, fontSize = (22 * scale).sp)
                    }
                }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
                    if (loadingMore || busy) CircularProgressIndicator(Modifier.size(20.dp * scale), color = Palette.secondary, strokeWidth = 2.dp * scale)
                    Text(if (loadingMore || busy && sources.isEmpty()) "Checking providers · ${sources.size} sources so far" else if (busy) "Opening stream…" else "Select a source to start playback.",
                        color = Palette.secondary, fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp)
                }
            }
        }
    }
}
