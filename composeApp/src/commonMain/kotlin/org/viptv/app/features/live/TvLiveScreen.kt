package org.viptv.app.features.live

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import org.viptv.app.components.PageRestore
import org.viptv.app.components.PageRestoreState
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.components.tvRingReach
import org.viptv.app.components.tvRingRoom
import org.viptv.app.components.AutomaticPage
import org.viptv.app.components.ListEndFooter
import org.viptv.app.components.LocalTvScale

@Composable
internal fun TvLiveScreen(channels: List<LiveChannel>, start: Int?, now: Int?, watch: (LiveChannel) -> Unit,
    categories: List<String> = channels.mapNotNull { it.category }.distinct(), groupLabels: Map<String, String> = emptyMap(),
    groupChanged: ((String?) -> Unit)? = null, guideRequested: (LiveChannel) -> Unit = {}, more: (() -> Unit)? = null, busy: Boolean = false,
    favoriteChanged: ((LiveChannel) -> Unit)? = null, favoritesOnly: Boolean = false, favoritesFilter: (() -> Unit)? = null,
    sources: Map<String, String> = emptyMap(), selectedSource: String? = null, sourceChanged: ((String) -> Unit)? = null,
    spread: LiveSpread = LiveSpread.Channels, noChannelsReason: String? = null, noChannelsRetry: (() -> Unit)? = null,
    sourceIntegrations: (() -> Unit)? = null,
    restore: PageRestoreState = rememberPageRestore("live"), pageError: String? = null) {
    var category by remember { mutableStateOf(AllGroups) }
    LaunchedEffect(selectedSource) { category = AllGroups }
    val groups = if (groupLabels.isNotEmpty()) groupLabels else categories.distinct().associateWith { it }
    val activeCategory = category.takeIf { it == AllGroups || it in groups } ?: AllGroups
    val visible = channels.filter { (!favoritesOnly || it.favorite) && (groupChanged != null || activeCategory == AllGroups || (it.groupId ?: it.category) == activeCategory) }
    var channel by remember { mutableStateOf(channels.firstOrNull()) }
    var program by remember { mutableStateOf<LiveProgram?>(null) }
    val first = remember { FocusRequester() }; val chip = remember { FocusRequester() }
    val favorite = remember { FocusRequester() }; val watchTile = remember { FocusRequester() }
    val empty = remember { FocusRequester() }; val integrationsFocus = remember { FocusRequester() }
    val hasGuide = start != null && visible.any { channel -> channel.programs.any { it.end > start && it.start < start + 120 } }
    val chipsOnly = spread.showsChannelRows()
    val hasFilters = if (chipsOnly) sourceChanged != null && sources.size > 1 else groups.isNotEmpty() || favoritesFilter != null || (sourceChanged != null && sources.size > 1)
    // The preview sits above the chips, so Up out of the guide and the chips has to name it: the page group sends every entry to the first cell and the chips' Down comes back there.
    val previewEntry = if (channel == null) null else if (favoriteChanged != null) favorite else watchTile
    val upFromPage = chip.takeIf { hasFilters } ?: previewEntry
    val downFromPreview = chip.takeIf { hasFilters } ?: first.takeIf { visible.isNotEmpty() }
    LaunchedEffect(activeCategory, favoritesOnly, channels) {
        category = activeCategory
        channel = visible.find { it.card.provider_id == channel?.card?.provider_id && it.card.identity == channel?.card?.identity }
            ?: visible.firstOrNull()
        program = channel?.programs?.find { it.id == program?.id } ?: channel?.current(now)
        channel?.let(guideRequested)
    }
    val channelList = rememberLazyListState()
    val returning = remember { restore.lastKey != null }
    val anchored = restore.landing(visible.size) { visible[it].key() }
    AutomaticPage(visible.size, busy, more) { channelList.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    var previewHeight by remember { mutableStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        val pageHeight = maxHeight
        val density = androidx.compose.ui.platform.LocalDensity.current
        // The preview grows with the system font scale, so the chips and the guide start below it instead of over it.
        val chipsTop = maxOf(380.dp * scale, with(density) { previewHeight.toDp() } + 12.dp * scale)
        val guideTop = maxOf(460.dp * scale, chipsTop + 80.dp * scale)
        if (chipsOnly) {
            Box(Modifier.fillMaxSize().focusProperties { onEnter = { when {
                noChannelsRetry != null -> empty.requestFocus()
                hasFilters -> chip.requestFocus()
                sourceIntegrations != null -> integrationsFocus.requestFocus()
            } } }.focusGroup()) {
                Column(Modifier.fillMaxSize().padding(start = 96.dp * scale, end = 96.dp * scale, top = if (hasFilters) 24.dp * scale else 60.dp * scale),
                    verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    if (hasFilters) Box(Modifier.fillMaxWidth().focusGroup()) {
                        CompositionLocalProvider(LocalTvScale provides scale) {
                            LiveFilters(emptyMap(), null, { _ -> }, false, null, {}, sources, selectedSource, sourceChanged, entry = chip)
                        }
                    }
                    if (spread == LiveSpread.Loading) LiveChannelSkeletons(true, scale, false)
                    else if (spread == LiveSpread.Failed) LiveLoadFailed(true, scale, false, noChannelsReason, noChannelsRetry, sourceIntegrations, empty, integrationsFocus)
                    else LiveNoChannels(true, scale, false, noChannelsReason, noChannelsRetry, sourceIntegrations, empty, integrationsFocus)
                }
            }
            return@BoxWithConstraints
        }
        // The chip row stays outside the group below: that group's onEnter lands on the guide, and any focus request into it counts as an entry, so a chip inside could never take the remote's focus.
        // onEnter also catches the restore's focus request; only a first visit sends it to the guide's first cell, and only an entry from below lands on the preview.
        Box(Modifier.fillMaxSize().focusProperties { onEnter = {
            when {
                requestedFocusDirection == FocusDirection.Up -> previewEntry?.requestFocus()
                returning -> Unit
                visible.isNotEmpty() -> first.requestFocus()
                hasFilters -> chip.requestFocus()
            }
        } }.focusGroup()) {
            channel?.let { TvLivePreview(it, program ?: it.current(now), now, scale, favorite = favoriteChanged?.let { action -> { action(it) } },
                favoriteFocus = favorite, watchFocus = watchTile, down = downFromPreview, onHeight = { previewHeight = it }) { watch(it) } }
            Box(Modifier.offset(96.dp * scale, guideTop).width(1728.dp * scale)
                .height((pageHeight - guideTop - 50.dp * scale).coerceAtLeast(0.dp))) {
                if (visible.isEmpty()) LiveFilteredEmpty(true, scale, false, favoritesOnly, busy, sources[selectedSource]) {
                    category = AllGroups; groupChanged?.invoke(null)
                } else if (hasGuide) {
                    LiveGuide(visible, start!!, now, true, scale,
                    { selected, selectedProgram -> channel = selected; program = selectedProgram; guideRequested(selected) }, watch, first, upFromPage, more = more, busy = busy, restore = restore, pageError = pageError)
                }
                else {
                    PageRestore(channelList, restore, claimOnEntry = true, target = { restore.landing(visible.size) { visible[it].key() } })
                    LazyColumn(Modifier.tvRingRoom(tvRingReach(scale), horizontal = false), state = channelList,
                        contentPadding = PaddingValues(vertical = tvRingReach(scale)), verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                        itemsIndexed(visible, key = { _, item -> item.key() }) { index, item ->
                            TvLiveChannelRow(item, now, scale, if (index == 0) first else null, if (index == 0) upFromPage else null,
                                { channel = item; program = item.current(now); guideRequested(item) }, { restore.mark(item.key(), index); watch(item) },
                                modifier = Modifier.pageAnchor(restore, item.key(), index, target = anchored == index))
                        }
                        if (visible.isNotEmpty() && !busy && pageError != null) item(key = "footer") {
                            ListEndFooter(failed = true, retry = more, noun = "channels", modifier = Modifier.fillMaxWidth(), television = true, scale = scale)
                        } else if (visible.isNotEmpty() && busy) item(key = "footer") {
                            ListEndFooter(loading = true, noun = "channels", modifier = Modifier.fillMaxWidth(), television = true, scale = scale)
                        } else if (visible.isNotEmpty() && !busy && pageError == null && more == null) item(key = "footer") {
                            ListEndFooter(ended = true, noun = "channels", modifier = Modifier.fillMaxWidth(), television = true, scale = scale)
                        }
                    }
                }
            }
        }
        Box(Modifier.offset(96.dp * scale, chipsTop).width(1728.dp * scale).focusGroup()) {
            CompositionLocalProvider(LocalTvScale provides scale) {
                LiveFilters(groups, activeCategory.takeUnless { it == AllGroups }, { category = it ?: AllGroups; groupChanged?.invoke(it) },
                    favoritesOnly, favoritesFilter, { category = AllGroups; groupChanged?.invoke(null) },
                    sources, selectedSource, sourceChanged, entry = chip, down = first, up = previewEntry)
            }
        }
    }
}
