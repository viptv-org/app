package org.viptv.app.features.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

@Composable
fun LiveScreen(channels: List<LiveChannel>, television: Boolean, start: Int?, now: Int?, watch: (LiveChannel) -> Unit, manage: () -> Unit) {
    var fullGuide by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("All") }
    var selected by remember(channels) { mutableStateOf(channels.firstOrNull()) }
    var selectedProgram by remember { mutableStateOf<LiveProgram?>(null) }
    var notice by remember { mutableStateOf(false) }
    val visible = channels.filter { category == "All" || it.category == category }
    LaunchedEffect(category, channels) {
        if (selected !in visible) selected = visible.firstOrNull()
        selectedProgram = null
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp && !television
        val scale = if (television) maxWidth / 1920.dp else 1f
        val gutter = if (phone) 16.dp else if (television) 96.dp * scale else 40.dp
        val categories = channels.mapNotNull { it.category }.distinct()
        Column(Modifier.fillMaxSize().padding(horizontal = gutter, vertical = if (phone) 8.dp else 28.dp * scale),
            verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            if (phone && categories.isNotEmpty()) LiveFilters(categories, category, { category = it })
            if (!fullGuide) selected?.let { LivePreview(it, selectedProgram ?: it.current(now), now, phone, television, scale,
                { watch(it) }, { notice = true }, if (!phone && start != null) ({ fullGuide = true }) else null) }
            if (fullGuide) TextButton(onClick = { fullGuide = false }) { Text("Show preview") }
            if (!phone && categories.isNotEmpty()) LiveFilters(categories, category, { category = it }, if (television) scale * 1.5f else 1f)
            if (visible.isEmpty()) {
                Text("No channels are available for this profile.", color = Palette.secondary)
                TextButton(manage) { Text("Manage add-ons") }
            } else if (!phone && start != null && visible.any { it.programs.isNotEmpty() }) {
                Box(Modifier.weight(1f)) { LiveGuide(visible, start, now, television, scale,
                    { channel, program -> selected = channel; selectedProgram = program }, watch) }
            } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = if (phone) 96.dp else 24.dp)) {
                items(visible, key = { it.card.provider_id + ":" + it.card.identity.id }) {
                    LiveChannelRow(it, now, { watch(it) }, { notice = true })
                }
            }
        }
    }
    if (notice) AlertDialog(onDismissRequest = { notice = false }, title = { Text("Channel favourites") },
        text = { Text("Saving channel favourites is not available for this connection yet.") },
        confirmButton = { TextButton(onClick = { notice = false }) { Text("OK") } })
}
