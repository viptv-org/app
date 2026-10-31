package org.viptv.app.features.live

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.AutomaticPage
import org.viptv.app.components.PageRestore
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.theme.Palette
import org.viptv.app.components.TextButton

@Composable
fun LiveScreen(channels: List<LiveChannel>, television: Boolean, start: Int?, now: Int?, watch: (LiveChannel) -> Unit, manage: () -> Unit, groupLabels: Map<String, String> = emptyMap(),
    groupChanged: ((String?) -> Unit)? = null, guideRequested: (LiveChannel) -> Unit = {}, more: (() -> Unit)? = null, busy: Boolean = false, favoriteChanged: ((LiveChannel) -> Unit)? = null,
    favoritesOnly: Boolean = false, favoritesFilter: (() -> Unit)? = null, sources: Map<String, String> = emptyMap(), selectedSource: String? = null,
    sourceChanged: ((String) -> Unit)? = null, emptyReason: String? = null, spread: LiveSpread = LiveSpread.Channels,
    noChannelsReason: String? = null, noChannelsRetry: (() -> Unit)? = null, sourceIntegrations: (() -> Unit)? = null,
    statusNotice: String? = null, noticeRetry: (() -> Unit)? = null) {
    val restore = rememberPageRestore("live")
    if (television) { TvLiveScreen(channels, start, now, watch, manage, groupLabels = groupLabels, groupChanged = groupChanged, guideRequested = guideRequested, more = more, busy = busy, favoriteChanged = favoriteChanged, favoritesOnly = favoritesOnly, favoritesFilter = favoritesFilter, sources = sources, selectedSource = selectedSource, sourceChanged = sourceChanged, spread = spread, noChannelsReason = noChannelsReason, noChannelsRetry = noChannelsRetry, sourceIntegrations = sourceIntegrations, restore = restore); return }
    var fullGuide by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(AllGroups) }
    LaunchedEffect(selectedSource) { category = AllGroups }
    var selected by remember { mutableStateOf(channels.firstOrNull()) }
    var selectedProgram by remember { mutableStateOf<LiveProgram?>(null) }
    val visible = channels.filter { (!favoritesOnly || it.favorite) && (groupChanged != null || category == AllGroups || (it.groupId ?: it.category) == category) }
    LaunchedEffect(category, favoritesOnly, channels) {
        selected = visible.find { it.id == selected?.id && it.connectionId == selected?.connectionId } ?: visible.firstOrNull()
        selectedProgram = selected?.programs?.find { it.id == selectedProgram?.id } ?: selected?.current(now)
    }
    LaunchedEffect(selected?.id) { selected?.let(guideRequested) }
    val channelList = rememberLazyListState()
    val anchored = restore.anchor(visible.size) { visible[it].key() }
    AutomaticPage(visible.size, busy, more) { channelList.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp && !television
        val scale = if (television) maxWidth / 1920.dp else 1f
        val gutter = if (phone) 16.dp else if (television) 96.dp * scale else 40.dp
        val groups = if (groupLabels.isNotEmpty()) groupLabels else channels.mapNotNull { it.category }.distinct().associateWith { it }
        val chooseGroup: (String?) -> Unit = { category = it ?: AllGroups; groupChanged?.invoke(it) }
        val reset: () -> Unit = { chooseGroup(null) }
        val chipsOnly = spread.showsChannelRows()
        val hasFilters = if (chipsOnly) sourceChanged != null && sources.size > 1 else groups.isNotEmpty() || favoritesFilter != null || (sourceChanged != null && sources.size > 1)
        Column(Modifier.fillMaxSize().padding(horizontal = gutter, vertical = if (phone) 8.dp else 28.dp * scale),
            verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            if (hasFilters) LiveFilters(if (chipsOnly) emptyMap() else groups, if (chipsOnly) null else category.takeUnless { it == AllGroups }, chooseGroup,
                false, if (chipsOnly) null else favoritesFilter, reset,
                sources, selectedSource, sourceChanged, gutter = gutter)
            statusNotice?.let { message ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), color = Palette.secondary, fontSize = if (phone) 13.sp else 15.sp)
                    noticeRetry?.let { retry -> TextButton(retry, enabled = !busy) { Text("Retry") } }
                }
            }
            if (!fullGuide) selected?.let { channel -> LivePreview(channel, selectedProgram ?: channel.current(now), now, phone, television, scale,
                { watch(channel) }, favoriteChanged?.let { save -> { save(channel) } }, if (!phone && start != null) ({ fullGuide = true }) else null) }
            if (fullGuide) TextButton(onClick = { fullGuide = false }) { Text("Show preview") }
            if (visible.isEmpty() && spread == LiveSpread.Loading) {
                Box(Modifier.weight(1f)) { LiveChannelSkeletons(false, scale, phone) }
            } else if (visible.isEmpty() && spread == LiveSpread.NoChannels) {
                LiveNoChannels(false, scale, phone, noChannelsReason, noChannelsRetry, sourceIntegrations)
            } else if (visible.isEmpty()) {
                Text(if (favoritesOnly) { if (busy) "Loading favourite channels…" else "No favourite channels${sources[selectedSource]?.let { " from $it" } ?: " from this source"}." } else emptyReason ?: "No channels are available for this profile.", color = Palette.secondary)
                if (favoritesOnly) TextButton({ chooseGroup(null) }) { Text("All channels") }
                else TextButton(manage) { Text("Manage add-ons") }
            } else if (!phone && start != null && visible.any { it.programs.isNotEmpty() }) {
                Box(Modifier.weight(1f)) { LiveGuide(visible, start, now, television, scale,
                    { channel, program -> selected = channel; selectedProgram = program; guideRequested(channel) }, watch, more = more, busy = busy, restore = restore) }
            } else {
                PageRestore(channelList, restore, target = { restore.anchor(visible.size) { visible[it].key() } })
                LazyColumn(state = channelList, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = if (phone) 96.dp else 24.dp)) {
                    itemsIndexed(visible, key = { _, channel -> channel.key() }) { index, channel ->
                        LiveChannelRow(channel, now, { restore.mark(channel.key(), index); watch(channel) }, favoriteChanged?.let { save -> { save(channel) } },
                            modifier = Modifier.pageAnchor(restore, channel.key(), index, target = anchored == index))
                    }
                }
            }
        }
    }
}
