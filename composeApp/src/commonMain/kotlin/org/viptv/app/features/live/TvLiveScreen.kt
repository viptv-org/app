package org.viptv.app.features.live

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

@Composable
internal fun TvLiveScreen(channels: List<LiveChannel>, start: Int?, now: Int?, watch: (LiveChannel) -> Unit,
    manage: () -> Unit, categories: List<String> = channels.mapNotNull { it.category }.distinct(), groupLabels: Map<String, String> = emptyMap(),
    groupChanged: ((String?) -> Unit)? = null, guideRequested: (LiveChannel) -> Unit = {}, more: (() -> Unit)? = null) {
    var category by remember { mutableStateOf("All") }
    val categoryIds = if (groupLabels.isNotEmpty()) groupLabels.keys.toList() else categories
    val activeCategory = category.takeIf { it == "All" || it in categoryIds } ?: "All"
    val visible = if (groupChanged != null) channels else channels.filter { activeCategory == "All" || (it.groupId ?: it.category) == activeCategory }
    var channel by remember { mutableStateOf(channels.firstOrNull()) }
    var program by remember { mutableStateOf<LiveProgram?>(null) }
    val first = remember { FocusRequester() }; val filter = remember { FocusRequester() }
    val hasGuide = start != null && visible.any { channel -> channel.programs.any { it.end > start && it.start < start + 120 } }
    LaunchedEffect(activeCategory, channels) {
        category = activeCategory
        channel = visible.find { it.card.provider_id == channel?.card?.provider_id && it.card.identity == channel?.card?.identity }
            ?: visible.firstOrNull()
        program = channel?.programs?.find { it.id == program?.id } ?: channel?.current(now)
        channel?.let(guideRequested)
    }
    BoxWithConstraints(Modifier.fillMaxSize().focusProperties { onEnter = { if (visible.isNotEmpty()) first.requestFocus() else filter.requestFocus() } }.focusGroup()) {
        val scale = maxWidth / 1920.dp
        // The shared TV navigation occupies the first 132 design pixels.
        channel?.let { TvLivePreview(it, program ?: it.current(now), now, scale) { watch(it) } }
        TvLiveFilters(categoryIds, activeCategory, { category = it; groupChanged?.invoke(it.takeUnless { it == "All" }) }, scale, filter, if (visible.isNotEmpty()) first else null,
            Modifier.offset(96.dp * scale, 380.dp * scale), groupLabels)
        Box(Modifier.offset(96.dp * scale, 460.dp * scale).width(1728.dp * scale)
            .height((maxHeight - 510.dp * scale).coerceAtLeast(0.dp))) {
            if (visible.isEmpty()) Column(verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                Text("No channels are available for this profile.", color = Palette.secondary, fontSize = (24 * scale).sp)
                TvAction(Modifier.height(64.dp * scale), scale, activate = manage, contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                    Text("Manage add-ons", color = it, fontSize = (24 * scale).sp)
                }
            } else if (hasGuide) LiveGuide(visible, start!!, now, true, scale,
                { selected, selectedProgram -> channel = selected; program = selectedProgram; guideRequested(selected) }, watch, first, filter)
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                itemsIndexed(visible, key = { _, item -> item.card.provider_id + ":" + item.card.identity.id }) { index, item ->
                    TvLiveChannelRow(item, now, scale, if (index == 0) first else null, if (index == 0) filter else null,
                        { channel = item; program = item.current(now); guideRequested(item) }, { watch(item) })
                }
                more?.let { load -> item { TvAction(Modifier.fillMaxWidth().height(64.dp * scale), scale, activate = load) { Text("Load more channels", color = it) } } }
            }
        }
    }
}
