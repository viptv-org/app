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

internal data class TvSourceInfo(val quality: String? = null, val format: String? = null, val addon: String? = null, val bestMatch: Boolean = false)

@Composable
internal fun TvSourcesScreen(title: Card?, sources: List<SourceCandidate>, busy: Boolean, error: String?,
    choose: (SourceCandidate) -> Unit, back: () -> Unit, detail: DetailSnapshot?, info: Map<String, TvSourceInfo> = emptyMap(),
    extras: TvDetailExtras = TvDetailExtras(), loadingMore: Boolean = false) {
    TvDetailsScreen(detail, false, null, {}, extras, requestInitialFocus = false)
    val motion = rememberTvDrawerMotion(back)
    Popup(onDismissRequest = motion.dismiss, properties = PopupProperties(focusable = true)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val scale = maxWidth / 1920.dp
            val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
            var focusedSource by remember { mutableStateOf<String?>(null) }
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val filterFocus = remember { FocusRequester() }
            var quality by remember { mutableStateOf<String?>(null) }
            val visible = sources.filter { quality == null || info[it.id]?.quality == quality }
            val finding = loadingMore || busy && sources.isEmpty()
            val shimmer = if (finding) rememberTvLoadingShimmer(scale) else Modifier
            LaunchedEffect(quality, visible.map { it.id }, busy) {
                if (busy) return@LaunchedEffect
                val target = visible.find { it.id == focusedSource } ?: visible.firstOrNull()
                if (target == null) { filterFocus.requestFocus(); return@LaunchedEffect }
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
                Text(listOfNotNull(title?.title, if (loadingMore || busy && sources.isEmpty()) "finding sources" else if (busy) "opening stream" else count).joinToString(" · "),
                    fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, color = Palette.secondary)
                Spacer(Modifier.height(22.dp * scale))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                    val qualities = listOf<String?>(null) + sources.mapNotNull { info[it.id]?.quality }.distinct()
                    qualities.forEach { option ->
                        TvAction(Modifier.height(52.dp * scale).widthIn(min = 96.dp * scale), scale, activate = { quality = option },
                            requester = if (quality == option) filterFocus else null, cornerRadius = 26.dp * scale,
                            background = if (quality == option) Color(0xFF393939) else Color.Transparent,
                            contentPadding = PaddingValues(horizontal = 20.dp * scale)) {
                            Text("${option ?: "All"} ${if (option == null) sources.size else sources.count { info[it.id]?.quality == option }}",
                                color = it, fontSize = (22 * scale).sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(28.dp * scale))
                TvFocusScroll {
                LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                    error?.let { item { Text(it, color = Palette.danger, fontSize = (22 * scale).sp) } }
                    itemsIndexed(visible, key = { _, source -> source.id }) { index, source ->
                        TvSourceRow(source, info[source.id], scale, rowFocus.getOrPut(source.id) { FocusRequester() }, !busy, choose,
                            up = if (index == 0) filterFocus else null, focusedSource = { focusedSource = it })
                    }
                    if (finding) items(3) {
                        Box(Modifier.fillMaxWidth().height(104.dp * scale).clip(RoundedCornerShape(14.dp * scale)).then(shimmer)
                            .semantics { contentDescription = "Loading source" })
                    }
                    if (!busy && !loadingMore && visible.isEmpty() && error == null) item { Text("No sources found. Enable an add-on that supplies streams.", color = Palette.secondary, fontSize = (22 * scale).sp) }
                }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
                    if (loadingMore || busy) CircularProgressIndicator(Modifier.size(20.dp * scale), color = Palette.secondary, strokeWidth = 2.dp * scale)
                    Text(if (loadingMore || busy && sources.isEmpty()) "Checking add-ons · ${sources.size} sources so far" else if (busy) "Opening stream…" else "Select a source to start playback.",
                        color = Palette.secondary, fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp)
                }
            }
        }
    }
}
