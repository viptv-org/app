package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.*
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
    detail: DetailSnapshot? = null, loadingMore: Boolean = false, selectedSource: String? = null, torrentOnlyProviders: Int = 0,
    checkedAddons: Int = 0, checkedProviders: Int = 0, checkAgain: () -> Unit = {}, manageAddons: () -> Unit = {},
    debridHelp: () -> Unit = {}) {
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
            // TVUI-03: nothing playable — the boards replace the chip rows and the
            // result list with a designed state panel instead of one 22sp line.
            val torrentOnly = !busy && !loadingMore && error == null && sources.isEmpty() && torrentOnlyProviders > 0
            val noSources = !busy && !loadingMore && error == null && sources.isEmpty() && torrentOnlyProviders == 0
            val stateFocus = remember { FocusRequester() }
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
            // A state panel has no rows, so it owns the first focusable control and
            // the remote never lands on an empty drawer. Row focus below is untouched:
            // that effect returns early while `visible` is empty.
            LaunchedEffect(noSources, torrentOnly) {
                if (!noSources && !torrentOnly) return@LaunchedEffect
                withFrameNanos { }
                stateFocus.requestFocus()
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = motion.progress.value }.background(Color.Black.copy(alpha = 0.55f))
                .focusProperties { canFocus = false }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = motion.dismiss))
            Column(Modifier.align(Alignment.CenterEnd).width(960.dp * scale).fillMaxHeight()
                .graphicsLayer { translationX = size.width * (1f - motion.progress.value) }.background(Color(0xFF161618))
                .padding(start = 64.dp * scale, end = 96.dp * scale, top = 72.dp * scale, bottom = 56.dp * scale)) {
                Text("Choose a source", fontSize = (44 * scale).sp, lineHeight = (53 * scale).sp, letterSpacing = (-0.88f * scale).sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp * scale))
                val count = sourceSummary(sources)
                val summary = when {
                    loadingMore || busy && sources.isEmpty() -> "finding sources"
                    busy -> "opening stream"
                    torrentOnly -> sourcesTorrentHeading(torrentOnlyProviders)
                    noSources -> sourcesCheckedLine(checkedAddons, checkedProviders)
                    else -> count
                }
                Text(listOfNotNull(sourceHeading(title, detail), summary).joinToString(" · "),
                    fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, color = Palette.secondary)
                Spacer(Modifier.height(22.dp * scale))
                if (noSources || torrentOnly) {
                    // `Tv3SourcesEmpty` drops the panel 80 px below the sheet's own
                    // 22 gap; `Tv3SourcesTorrent` puts the alert straight against it.
                    if (noSources) Spacer(Modifier.height(80.dp * scale))
                    if (torrentOnly) TvSourcesTorrentPanel(torrentOnlyProviders, scale, debridHelp, motion.dismiss, stateFocus)
                    else TvSourcesEmptyPanel(sourceHeading(title, detail), checkedAddons, checkedProviders, scale,
                        checkAgain, manageAddons, stateFocus)
                } else TvSourcesFilters(sources, info, scale, quality, provider, { quality = it }, { provider = it }, filterFocus, providerFocus,
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
                    if (!busy && !loadingMore && visible.isEmpty() && error == null && sources.isNotEmpty()) item {
                        Text("No sources match these filters.", color = Palette.secondary, fontSize = (22 * scale).sp)
                    }
                }
                }
                if (!noSources && !torrentOnly) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
                    if (loadingMore || busy) CircularProgressIndicator(Modifier.size(20.dp * scale), color = Palette.secondary, strokeWidth = 2.dp * scale)
                    Text(if (loadingMore || busy && sources.isEmpty()) "Checking add-ons · ${sources.size} sources so far" else if (busy) "Opening stream…" else "Play always starts the best match. Change how it’s chosen in Settings › Playback.",
                        color = Palette.secondary, fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp)
                }
            }
        }
    }
}

private fun Int.counted(one: String, many: String): String = "$this ${if (this == 1) one else many}"

/** Board subtitle clause "checked 3 add-ons and 8 providers"; null while the counts are unknown. */
internal fun sourcesCheckedLine(addons: Int, providers: Int): String? = when {
    addons > 0 && providers > 0 -> "checked ${addons.counted("add-on", "add-ons")} and ${providers.counted("provider", "providers")}"
    providers > 0 -> "checked ${providers.counted("provider", "providers")}"
    addons > 0 -> "checked ${addons.counted("add-on", "add-ons")}"
    else -> null
}

/** Board subtitle clause "3 results this TV can’t play". */
internal fun sourcesTorrentHeading(providers: Int): String = "${providers.counted("result", "results")} this TV can’t play"

/** `Tv3SourcesEmpty` copy: "None of your 3 add-ons or 8 providers has <title> right now. …" */
internal fun sourcesEmptyExplanation(titleName: String?, addons: Int, providers: Int): String {
    val checked = when {
        addons > 0 && providers > 0 -> "None of your ${addons.counted("add-on", "add-ons")} or ${providers.counted("provider", "providers")}"
        providers > 0 -> "None of your ${providers.counted("provider", "providers")}"
        addons > 0 -> "None of your ${addons.counted("add-on", "add-ons")}"
        else -> "None of your add-ons or providers"
    }
    val subject = titleName?.takeIf { it.isNotBlank() } ?: "this title"
    return "$checked has $subject right now. New releases often appear within a few days."
}

/** `Tv3SourcesTorrent` copy, verbatim apart from the live provider count. */
internal fun sourcesTorrentExplanation(providers: Int): String =
    "${providers.counted("provider returned", "providers returned")} torrent links this device cannot stream directly. Add a debrid service (for example TorBox) to play them, or try another title."

/**
 * `Tv3SourcesEmpty`: 88 px search circle, 40 px heading, 26 px explanation and the
 * board's two actions (280×72 / 300×72, r12, 26 px w700). Both actions are
 * [TvAction]s, so they keep the sheet's focus fill + `tvFocusOutline` ring.
 */
@Composable
internal fun TvSourcesEmptyPanel(titleName: String?, addons: Int, providers: Int, scale: Float,
    checkAgain: () -> Unit, manageAddons: () -> Unit, requester: FocusRequester) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp * scale)) {
        Box(Modifier.size(88.dp * scale).clip(CircleShape).background(Palette.text.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
            Icon(Lucide.Search, null, Modifier.size(42.dp * scale), Palette.text)
        }
        Text("No sources yet", fontSize = (40 * scale).sp, lineHeight = (46 * scale).sp,
            letterSpacing = (-0.8f * scale).sp, fontWeight = FontWeight.Bold)
        Text(sourcesEmptyExplanation(titleName, addons, providers), fontSize = (26 * scale).sp,
            lineHeight = (38 * scale).sp, color = Palette.secondary)
        Row(Modifier.padding(top = 12.dp * scale), horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            TvAction(Modifier.width(280.dp * scale).height(72.dp * scale), scale, requester = requester, activate = checkAgain,
                contentPadding = PaddingValues(horizontal = 24.dp * scale)) { tint ->
                Icon(Lucide.RefreshCw, null, Modifier.size(28.dp * scale), tint); Spacer(Modifier.width(12.dp * scale))
                Text("Check again", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            }
            TvAction(Modifier.width(300.dp * scale).height(72.dp * scale), scale, activate = manageAddons,
                contentPadding = PaddingValues(horizontal = 24.dp * scale)) { tint ->
                Icon(Lucide.Puzzle, null, Modifier.size(28.dp * scale), tint); Spacer(Modifier.width(12.dp * scale))
                Text("Manage add-ons", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * `Tv3SourcesTorrent`: `role="alert"` panel — r16, `rgba(255,255,255,.07)`, padding
 * 26/28, 32 px info glyph, 26 px copy, "What’s a debrid service?" 400×72 and
 * "Back" 180×72.
 */
@Composable
internal fun TvSourcesTorrentPanel(providers: Int, scale: Float, debridHelp: () -> Unit, dismiss: () -> Unit,
    requester: FocusRequester) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp * scale)).background(Palette.text.copy(alpha = .07f))
        .semantics { liveRegion = LiveRegionMode.Polite }
        .padding(horizontal = 28.dp * scale, vertical = 26.dp * scale),
        horizontalArrangement = Arrangement.spacedBy(20.dp * scale), verticalAlignment = Alignment.Top) {
        Icon(Lucide.Info, null, Modifier.padding(top = 2.dp * scale).size(32.dp * scale), Palette.text)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            Text(sourcesTorrentExplanation(providers), fontSize = (26 * scale).sp,
                lineHeight = (38 * scale).sp, color = Palette.text)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
                TvAction(Modifier.width(400.dp * scale).height(72.dp * scale), scale, requester = requester, activate = debridHelp,
                    contentPadding = PaddingValues(horizontal = 24.dp * scale)) { tint ->
                    Icon(Lucide.Info, null, Modifier.size(28.dp * scale), tint); Spacer(Modifier.width(12.dp * scale))
                    Text("What’s a debrid service?", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                }
                TvAction(Modifier.width(180.dp * scale).height(72.dp * scale), scale, activate = dismiss,
                    contentPadding = PaddingValues(horizontal = 24.dp * scale)) { tint ->
                    Text("Back", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
