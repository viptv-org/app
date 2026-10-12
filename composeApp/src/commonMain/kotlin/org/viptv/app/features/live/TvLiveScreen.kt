package org.viptv.app.features.live

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    manage: () -> Unit, categories: List<String> = channels.mapNotNull { it.category }.distinct()) {
    var category by remember { mutableStateOf("All") }
    val visible = channels.filter { category == "All" || it.category == category }
    var channel by remember { mutableStateOf(channels.firstOrNull()) }
    var program by remember { mutableStateOf<LiveProgram?>(null) }
    var favouritesNotice by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }; val filter = remember { FocusRequester() }
    val hasGuide = start != null && visible.any { channel -> channel.programs.any { it.end > start && it.start < start + 120 } }
    LaunchedEffect(category, channels) {
        channel = visible.find { it.card.identity == channel?.card?.identity } ?: visible.firstOrNull()
        program = channel?.programs?.find { it.id == program?.id } ?: channel?.current(now)
    }
    BoxWithConstraints(Modifier.fillMaxSize().focusProperties { onEnter = { if (hasGuide) first.requestFocus() } }.focusGroup()) {
        val scale = maxWidth / 1920.dp
        // The shared TV navigation occupies the first 132 design pixels.
        channel?.let { TvLivePreview(it, program ?: it.current(now), now, scale) { watch(it) } }
        TvLiveFilters(categories, category, { category = it }, scale, filter, if (hasGuide) first else null,
            Modifier.offset(96.dp * scale, 380.dp * scale))
        Box(Modifier.offset(96.dp * scale, 460.dp * scale).width(1728.dp * scale)
            .height((maxHeight - 510.dp * scale).coerceAtLeast(0.dp))) {
            if (visible.isEmpty()) Column(verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                Text("No channels are available for this profile.", color = Palette.secondary, fontSize = (24 * scale).sp)
                TvAction(Modifier.height(64.dp * scale), scale, activate = manage, contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                    Text("Manage add-ons", color = it, fontSize = (24 * scale).sp)
                }
            } else if (hasGuide) LiveGuide(visible, start!!, now, true, scale,
                { selected, selectedProgram -> channel = selected; program = selectedProgram }, watch, first, filter)
            else LazyColumn { items(visible) { item -> LiveChannelRow(item, now, { watch(item) }, { favouritesNotice = true }) } }
        }
    }
    if (favouritesNotice) AlertDialog(onDismissRequest = { favouritesNotice = false }, title = { Text("Channel favourites") },
        text = { Text("Saving channel favourites is not available for this connection yet.") },
        confirmButton = { TextButton(onClick = { favouritesNotice = false }) { Text("OK") } })
}
