package org.viptv.app.features.sources

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.flow.collect
import org.viptv.app.connection.SourceGroups
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.app.features.details.TvDetailExtras
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.IconButton
import org.viptv.app.components.LocalPointerHover
import org.viptv.app.components.Motion
import org.viptv.app.components.SheetHandle
import org.viptv.app.components.motionSpec
import org.viptv.app.components.rememberOverlayMotion
import org.viptv.app.components.rememberSheetDrag

@Composable
fun SourcesScreen(title: Card?, sources: List<SourceCandidate>, busy: Boolean, error: String?,
    choose: (SourceCandidate) -> Unit, back: () -> Unit, detail: DetailSnapshot? = null, television: Boolean = false, tablet: Boolean = false, loadingMore: Boolean = false, selectedSource: String? = null, torrentOnlyProviders: Int = 0,
    openingStage: String? = null, groups: SourceGroups = SourceGroups(), drawerVisible: Boolean = true, onClosed: () -> Unit = {}, scrolled: () -> Unit = {},
    errorCode: String? = null, openGateways: (() -> Unit)? = null, banner: String? = null, failed: List<String> = emptyList()) {
    if (television) {
        TvSourcesScreen(title, sources, busy, error, choose, back,
            info = sources.associate { it.id to TvSourceInfo(quality = it.qualityLabel(), format = it.formatLabel(), addon = it.providerLabel().takeUnless { name -> name == it.label }) },
            detail = detail,
            loadingMore = loadingMore, selectedSource = selectedSource, torrentOnlyProviders = torrentOnlyProviders, openingStage = openingStage,
            groups = groups, banner = banner, failed = failed)
        return
    }
    var quality by remember { mutableStateOf<String?>(null) }
    var provider by remember { mutableStateOf<String?>(null) }
    val visible = sources.filter { (quality == null || it.qualityLabel() == quality) && (provider == null || it.sourceFilterId() == provider) }
    val rows = remember(visible, groups, failed) { sourceListRows(visible, groups, failed) }
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) scrolled()
        }
    }
    var focusedSource by remember { mutableStateOf<String?>(null) }
    val headerHeight = remember { SourceHeaderHeight() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp && !television
        val touch = !television && !LocalPointerHover.current
        val leave: FiniteAnimationSpec<Float> = motionSpec(tween(Motion.leave, easing = Motion.exit))
        val motion = rememberOverlayMotion(drawerVisible,
            containerEnter = motionSpec(tween(Motion.panel, easing = Motion.standard)),
            scrimEnter = motionSpec(tween(Motion.arrive, easing = Motion.standard)),
            leave = leave)
        var sheetHeight by remember { mutableFloatStateOf(0f) }
        val drag = rememberSheetDrag(enabled = touch && phone, threshold = sheetHeight / 3f, dismiss = back,
            dismissSpec = leave, settleSpec = motionSpec(tween(Motion.response, easing = Motion.standard)))
        LaunchedEffect(motion.presented) { if (!motion.presented) onClosed() }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = motion.scrim }.background(Color.Black.copy(alpha = 0.55f)).focusProperties { canFocus = false }.pointerClickable(onClick = back))
        val panel = when {
            phone -> Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.84f)
            television -> Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.5f)
            tablet -> Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 461.dp).fillMaxWidth()
            else -> Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(sourceDrawerWidth(maxWidth))
        }
        Column(panel.graphicsLayer {
            if (phone) translationY = size.height * (1f - motion.container) + drag.offset
            else translationX = size.width * (1f - motion.container)
        }.onSizeChanged { if (phone) sheetHeight = it.height.toFloat() }.nestedScroll(drag.nestedScroll)
            .clip(RoundedCornerShape(topStart = if (phone) 24.dp else 0.dp,
            topEnd = if (phone) 24.dp else 0.dp)).background(Palette.surface).semantics { paneTitle = "Choose a source" }) {
            val summary = when {
                busy -> if (sources.isEmpty()) "Loading sources…" else openingStage ?: "Opening stream…"
                sources.isEmpty() && loadingMore -> "Loading sources…"
                else -> sourceSummary(sources)
            }
            val summaryLine = listOfNotNull(sourceHeading(title, detail), summary).joinToString(" · ")
            if (phone) Column(Modifier.fillMaxWidth().then(drag.handle)) {
                SheetHandle(touch, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 4.dp))
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Choose a source", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = back, modifier = Modifier.size(32.dp)) { Icon(Lucide.X, "Close", Modifier.size(18.dp)) }
                }
                Text(summaryLine, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), color = Palette.secondary, fontSize = 13.sp)
                banner?.let { SourceFallbackBanner(it, 13.sp, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) }
            } else Column(Modifier.padding(horizontal = if (television) 48.dp else 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Choose a source", Modifier.weight(1f), fontSize = if (television) 36.sp else 18.sp,
                        fontWeight = FontWeight.Bold)
                    IconButton(onClick = back, modifier = Modifier.size(32.dp)) { Icon(Lucide.X, "Close", Modifier.size(18.dp)) }
                }
                Text(summaryLine, color = Palette.secondary, fontSize = if (television) 20.sp else 13.sp)
                banner?.let { SourceFallbackBanner(it, if (television) 20.sp else 13.sp, Modifier.fillMaxWidth()) }
            }
            SourceFilters(sources, quality, provider, { quality = it }, { provider = it })
            if (busy || loadingMore) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
            KeepSourceListPosition(listState, rows, focusedSource, headerHeight, leading = if (error != null) 1 else 0)
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(horizontal = if (television) 48.dp else 0.dp,
                vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(if (television) 10.dp else 0.dp), topEdgeFade = false) {
                error?.let {
                    item(key = "error") {
                        Column(Modifier.padding(20.dp)) {
                            Text(it, color = Palette.danger)
                            if (openGateways != null && errorCode == "gateway_required") {
                                Spacer(Modifier.height(8.dp))
                                org.viptv.app.components.TextButton(openGateways) { Text("Open Gateways") }
                            }
                        }
                    }
                }
                if (!busy && !loadingMore && visible.isEmpty() && error == null) item {
                    val empty = if (torrentOnlyProviders > 0)
                        "${torrentOnlyProviders} ${if (torrentOnlyProviders == 1) "provider returned torrent links this device cannot stream directly" else "providers returned torrent links this device cannot stream directly"}. Add a debrid service (for example TorBox) to play them, or try another title."
                    else "No streams found. Enable a provider that supplies sources."
                    Text(empty, Modifier.padding(20.dp), color = Palette.secondary)
                }
                items(rows, key = { it.source.id }) { row ->
                    SourceRow(row.source, television, choose, enabled = !busy, selected = row.source.id == selectedSource,
                        header = row.header, bestMatch = row.bestMatch, didntStart = row.didntStart, headerMeasured = { headerHeight.pixels = it },
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
