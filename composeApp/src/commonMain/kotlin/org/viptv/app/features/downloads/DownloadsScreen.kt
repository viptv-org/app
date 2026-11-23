package org.viptv.app.features.downloads

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import kotlinx.coroutines.launch
import org.viptv.app.components.AccessibleIconButton
import org.viptv.app.components.pageBottomClearance
import org.viptv.app.components.pointerClickable
import org.viptv.app.downloads.DownloadItem
import org.viptv.app.downloads.DownloadSettings
import org.viptv.app.downloads.DownloadState
import org.viptv.app.theme.Palette

@Composable
internal fun DownloadsScreen(actions: DownloadActions, offline: Boolean, browse: () -> Unit, showTitle: Boolean) {
    val items by actions.downloads.items.collectAsState()
    val storage by actions.downloads.storage.collectAsState()
    val settings by actions.downloads.settings.collectAsState()
    val evicted by actions.downloads.evictionNotice.collectAsState()
    DisposableEffect(actions) { onDispose { if (actions.downloads.evictionNotice.value > 0) actions.downloads.dismissEvictionNotice() } }
    val groups = remember(items) { downloadGroups(items) }
    val watched = remember(items) { deletableWatched(items) }
    val waitingForSpace = items.any { it.state == DownloadState.WaitingForSpace }
    val now = remember(items) { actions.clock() }
    var limitDialog by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp
        val gutter = if (phone) 16.dp else 40.dp
        LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(start = gutter, end = gutter,
            top = if (phone) 8.dp else 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showTitle) item(key = "title") {
                Text(DOWNLOADS_TITLE, Modifier.semantics { heading() }, fontSize = 28.sp, letterSpacing = (-0.56).sp, fontWeight = FontWeight.Bold)
            }
            if (evicted > 0) item(key = "evicted") {
                DownloadsNotice(Lucide.Info, evictionNotice(evicted), null, null) { actions.downloads.dismissEvictionNotice() }
            }
            if (waitingForSpace) item(key = "space") {
                DownloadsNotice(Lucide.HardDrive, STORAGE_LIMIT_REACHED, STORAGE_LIMIT_BODY, listOf(
                    "Raise limit" to { limitDialog = true },
                    "Manage" to {
                        val index = groupIndex(showTitle, evicted > 0, groups, groups.maxByOrNull { it.bytes }?.key)
                        scope.launch { list.animateScrollToItem(index) }
                    }), null)
            }
            if (items.isNotEmpty()) item(key = "storage") { StorageSummary(storage, settings, watched.size) {
                watched.forEach { actions.downloads.delete(it.id) }
                actions.notice(deletedWatchedNotice(watched.size))
            } }
            if (items.isEmpty()) item(key = "empty") { DownloadsEmpty(if (offline) null else browse) }
            groups.forEach { group ->
                when (group) {
                    is MovieDownload -> item(key = group.key) { DownloadRow(group.item, actions, settings, now) }
                    is SeriesDownloads -> {
                        item(key = group.key) {
                            SeriesHeader(group) { group.items.forEach { actions.downloads.delete(it.id) } }
                        }
                        group.seasons.forEach { (season, episodes) ->
                            item(key = "${group.key}:season:$season") {
                                Text(if (season == 0L) "Specials" else "Season $season", Modifier.padding(top = 4.dp),
                                    color = Palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            items(episodes, key = { it.id }) { DownloadRow(it, actions, settings, now) }
                        }
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.pageBottomClearance()) }
        }
    }
    if (limitDialog) DownloadChoiceDialog("Storage limit", storageLimitChoices(storage), settings.storageLimitBytes,
        { actions.downloads.updateSettings(settings.copy(storageLimitBytes = it)) }) { limitDialog = false }
}

private fun groupIndex(showTitle: Boolean, evicted: Boolean, groups: List<DownloadGroup>, target: String?): Int {
    var index = (if (showTitle) 1 else 0) + (if (evicted) 1 else 0) + 2
    for (group in groups) {
        if (group.key == target) return index
        index += when (group) { is MovieDownload -> 1; is SeriesDownloads -> 1 + group.seasons.sumOf { 1 + it.second.size } }
    }
    return index
}

@Composable
private fun DownloadsNotice(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String?,
    buttons: List<Pair<String, () -> Unit>>?, dismiss: (() -> Unit)?) {
    Surface(shape = RoundedCornerShape(16.dp), color = Palette.surface) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = if (dismiss != null) 4.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(Palette.raised, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = Palette.text)
            }
            Column(Modifier.weight(1f).padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                body?.let { Text(it, color = Palette.secondary, fontSize = 14.sp, lineHeight = 20.sp) }
                if (!buttons.isNullOrEmpty()) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    buttons.forEach { (label, act) -> PillButton(label, act) }
                }
            }
            dismiss?.let { AccessibleIconButton(Lucide.X, "Dismiss", it) }
        }
    }
}

@Composable
internal fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    Box(modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).background(Palette.raised)
        .pointerClickable(role = Role.Button, shape = RoundedCornerShape(18.dp), onClick = onClick)
        .padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (danger) Palette.danger else Palette.text, maxLines = 1)
    }
}

@Composable
private fun StorageSummary(storage: org.viptv.app.downloads.DownloadStorage, settings: DownloadSettings, watched: Int, deleteWatched: () -> Unit) {
    val accent = Palette.accent
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(storageLine(storage, settings), Modifier.weight(1f), color = Palette.secondary, fontSize = 13.sp, lineHeight = 18.sp)
            if (watched > 0) {
                Spacer(Modifier.width(12.dp))
                PillButton("Delete watched", deleteWatched)
            }
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.surface3)) {
            Box(Modifier.fillMaxWidth(storageFraction(storage, settings)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(accent))
        }
    }
}

@Composable
private fun SeriesHeader(group: SeriesDownloads, delete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(group.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(groupSummary(group), color = Palette.secondary, fontSize = 13.sp)
        }
        PillButton("Delete", delete)
    }
}

@Composable
internal fun DownloadRow(item: DownloadItem, actions: DownloadActions, settings: DownloadSettings, nowMillis: Long) {
    var menu by remember { mutableStateOf(false) }
    val failed = item.state == DownloadState.Failed
    val expiring = item.state == DownloadState.Completed && item.watched && !item.keep && item.expiresAtMillis != null
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pointerClickable(shape = RoundedCornerShape(12.dp)) {
        if (item.state == DownloadState.Completed) actions.play(item.id) else menu = true
    }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(112.dp).aspectRatio(16f / 9).clip(RoundedCornerShape(8.dp)).background(Palette.surface)) {
            val art = item.card.landscape ?: item.card.poster
            if (art != null) AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (item.active) item.fraction?.let { fraction ->
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .25f))) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Palette.accent))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(episodeLabel(item.card), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(downloadStateLine(item, settings, nowMillis), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (failed || item.state == DownloadState.WaitingForSpace) Palette.danger else Palette.secondary)
            if (failed) item.failure?.let { Text(it, fontSize = 13.sp, color = Palette.secondary, maxLines = 2) }
            if (failed) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Try again", { actions.retry(item) })
                PillButton("Remove", { actions.downloads.delete(item.id) })
            } else if (expiring) PillButton("Keep", { actions.downloads.keep(item.id, true) }, Modifier.padding(top = 4.dp))
        }
        Box {
            DownloadControl(item.card, item, actions)
            DownloadItemMenu(item, actions, menu) { menu = false }
        }
    }
}

@Composable
private fun DownloadsEmpty(browse: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 72.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(64.dp).background(Palette.surface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Lucide.ArrowDownToLine, null, Modifier.size(28.dp), tint = Palette.secondary)
        }
        Text(DOWNLOADS_EMPTY_TITLE, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(DOWNLOADS_EMPTY_BODY, color = Palette.secondary, fontSize = 14.sp, lineHeight = 20.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
        browse?.let { PillButton("Browse", it, Modifier.padding(top = 8.dp)) }
    }
}

@Composable
internal fun DownloadsEntry(actions: DownloadActions, modifier: Modifier = Modifier) {
    val items by actions.downloads.items.collectAsState()
    val storage by actions.downloads.storage.collectAsState()
    val done = items.count { it.state == DownloadState.Completed }
    val active = items.count { it.active }
    val summary = when {
        items.isEmpty() -> "Watch without a connection"
        active > 0 -> "$active downloading · ${sizeLabel(storage.usedBytes)}"
        else -> "$done downloaded · ${sizeLabel(storage.usedBytes)}"
    }
    Surface(onClick = actions.openPage, modifier = modifier, shape = RoundedCornerShape(16.dp), color = Palette.surface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Lucide.Download, null, Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(DOWNLOADS_TITLE, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(summary, fontSize = 12.sp, color = Palette.secondary)
            }
            Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = Palette.secondary)
        }
    }
}

@Composable
internal fun OfflineBanner(openDownloads: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.widthIn(max = 520.dp).fillMaxWidth(), RoundedCornerShape(20.dp), color = Palette.surface, shadowElevation = 20.dp) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(Palette.raised, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Lucide.CloudOff, null, Modifier.size(18.dp), tint = Palette.text)
            }
            Text(OFFLINE_BANNER, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
            PillButton(DOWNLOADS_TITLE, openDownloads)
        }
    }
}
