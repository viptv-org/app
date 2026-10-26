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
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.components.tvRingReach
import org.viptv.app.components.tvRingRoom
import org.viptv.app.components.AutomaticPage
import org.viptv.app.theme.Palette

@Composable
internal fun TvLiveScreen(channels: List<LiveChannel>, start: Int?, now: Int?, watch: (LiveChannel) -> Unit,
    manage: () -> Unit, categories: List<String> = channels.mapNotNull { it.category }.distinct(), groupLabels: Map<String, String> = emptyMap(),
    groupChanged: ((String?) -> Unit)? = null, guideRequested: (LiveChannel) -> Unit = {}, more: (() -> Unit)? = null, busy: Boolean = false,
    favoriteChanged: ((LiveChannel) -> Unit)? = null, favoritesOnly: Boolean = false, favoritesFilter: (() -> Unit)? = null,
    sources: Map<String, String> = emptyMap(), selectedSource: String? = null, sourceChanged: ((String) -> Unit)? = null) {
    var category by remember { mutableStateOf("All") }
    LaunchedEffect(selectedSource) { category = "All" }
    val categoryIds = if (groupLabels.isNotEmpty()) groupLabels.keys.toList() else categories
    val activeCategory = category.takeIf { it == "All" || it in categoryIds } ?: "All"
    val visible = channels.filter { (!favoritesOnly || it.favorite) && (groupChanged != null || activeCategory == "All" || (it.groupId ?: it.category) == activeCategory) }
    var channel by remember { mutableStateOf(channels.firstOrNull()) }
    var program by remember { mutableStateOf<LiveProgram?>(null) }
    val first = remember { FocusRequester() }; val filter = remember { FocusRequester() }
    val hasGuide = start != null && visible.any { channel -> channel.programs.any { it.end > start && it.start < start + 120 } }
    LaunchedEffect(activeCategory, favoritesOnly, channels) {
        category = activeCategory
        channel = visible.find { it.card.provider_id == channel?.card?.provider_id && it.card.identity == channel?.card?.identity }
            ?: visible.firstOrNull()
        program = channel?.programs?.find { it.id == program?.id } ?: channel?.current(now)
        channel?.let(guideRequested)
    }
    val channelList = rememberLazyListState()
    AutomaticPage(visible.size, busy, more) { channelList.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    BoxWithConstraints(Modifier.fillMaxSize().focusProperties { onEnter = { if (visible.isNotEmpty()) first.requestFocus() else filter.requestFocus() } }.focusGroup()) {
        val scale = maxWidth / 1920.dp
        // The shared TV navigation occupies the first 132 design pixels.
        channel?.let { TvLivePreview(it, program ?: it.current(now), now, scale, favorite = favoriteChanged?.let { action -> { action(it) } }) { watch(it) } }
        TvLiveFilters(categoryIds, activeCategory, { category = it; groupChanged?.invoke(it.takeUnless { it == "All" }) }, scale, filter, if (visible.isNotEmpty()) first else null,
            Modifier.offset(96.dp * scale, 380.dp * scale).width(1728.dp * scale), groupLabels,
            favoritesSelected = favoritesOnly, favorites = favoritesFilter, sources = sources, selectedSource = selectedSource, sourceChanged = sourceChanged, busy = busy)
        Box(Modifier.offset(96.dp * scale, 460.dp * scale).width(1728.dp * scale)
            .height((maxHeight - 510.dp * scale).coerceAtLeast(0.dp))) {
            if (visible.isEmpty()) Column(verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                Text(if (favoritesOnly) { if (busy) "Loading favourite channels…" else "No favourite channels${sources[selectedSource]?.let { " from $it" } ?: " from this source"}." } else "No channels are available for this profile.", color = Palette.secondary, fontSize = (24 * scale).sp)
                TvAction(Modifier.height(64.dp * scale), scale, activate = if (favoritesOnly) ({ category = "All"; groupChanged?.invoke(null); Unit }) else manage, contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                    Text(if (favoritesOnly) "All channels" else "Manage add-ons", color = it, fontSize = (24 * scale).sp)
                }
            } else if (hasGuide) {
                // The guide replaces the channel list when guide data arrives; the
                // list's focus goes with it, so the guide claims its first row.
                LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
                LiveGuide(visible, start!!, now, true, scale,
                { selected, selectedProgram -> channel = selected; program = selectedProgram; guideRequested(selected) }, watch, first, filter, more = more, busy = busy)
            }
            else LazyColumn(Modifier.tvRingRoom(tvRingReach(scale), horizontal = false), state = channelList,
                contentPadding = PaddingValues(vertical = tvRingReach(scale)), verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                itemsIndexed(visible, key = { _, item -> item.card.provider_id + ":" + item.card.identity.id }) { index, item ->
                    TvLiveChannelRow(item, now, scale, if (index == 0) first else null, if (index == 0) filter else null,
                        { channel = item; program = item.current(now); guideRequested(item) }, { watch(item) })
                }
            }
        }
    }
}
