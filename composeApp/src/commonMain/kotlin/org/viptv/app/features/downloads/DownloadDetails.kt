package org.viptv.app.features.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.viptv.app.components.AccessibleIconButton
import org.viptv.app.components.SheetHandle
import org.viptv.app.components.dismissScrim
import org.viptv.app.components.pointerClickable
import org.viptv.app.downloads.DownloadItem
import org.viptv.app.downloads.DownloadState
import org.viptv.app.downloads.DownloadTrack
import org.viptv.app.theme.Palette

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DownloadDetailsSheet(item: DownloadItem, actions: DownloadActions, dismiss: () -> Unit) {
    val downloads = actions.downloads
    val completed = item.state == DownloadState.Completed
    LaunchedEffect(item.id) { actions.detailsOpened(item.id) }
    DownloadSheetFrame(DOWNLOAD_DETAILS_TITLE, dismiss) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item.seriesTitle?.let { Text(it, color = Palette.secondary, fontSize = 13.sp) }
                    Text(episodeLabel(item.card), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    item.failure?.let { Text(it, Modifier.padding(top = 4.dp), color = Palette.danger, fontSize = 13.sp, lineHeight = 18.sp) }
                }
                if (item.sourceLabel != null || item.sourceDetail != null) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.raised).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.sourceLabel?.trim()?.takeIf(String::isNotEmpty)?.let { Text(it, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold) }
                        item.sourceDetail?.trim()?.takeIf(String::isNotEmpty)?.let { Text(it, color = Palette.secondary, fontSize = 13.sp, lineHeight = 18.sp) }
                    }
                }
                val facts = downloadFacts(item)
                if (facts.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    facts.forEach { fact ->
                        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(fact.label, color = Palette.tertiary, fontSize = 12.sp)
                            Text(fact.value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                TrackSection("Audio", item.tracks.filter { it.kind == "audio" }, noTracksLine(item, "audio"))
                TrackSection("Subtitles", item.tracks.filter { it.kind == "subtitle" }, noTracksLine(item, "subtitle"))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.raised))
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (completed) PillButton("Play", { dismiss(); actions.play(item.id) })
                PillButton(CHANGE_SOURCE, { dismiss(); actions.changeSource(item.id) })
                if (completed) PillButton(if (item.keep) "Don't keep" else "Keep", { downloads.keep(item.id, !item.keep) })
                PillButton("Delete", { dismiss(); downloads.delete(item.id) }, danger = true)
            }
    }
}

@Composable
internal fun DownloadSheetFrame(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheet = maxWidth < 640.dp
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .62f))
            .dismissScrim(remember { MutableInteractionSource() }, dismiss))
        val panel = if (sheet) Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * .9f)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        else Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight - 48.dp)
            .clip(RoundedCornerShape(18.dp))
        Column(panel.background(Palette.surface).semantics { paneTitle = title }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) { dismiss(); true } else false
            }) {
            if (sheet) SheetHandle(true, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = if (sheet) 4.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                AccessibleIconButton(Lucide.X, "Close", dismiss, iconSize = 18.dp, wash = null)
            }
            content()
        }
    }
}

@Composable
internal fun ReleaseFollowUpSheet(prompt: ReleasePrompt, actions: DownloadActions) {
    DownloadSheetFrame(RELEASE_PROMPT_TITLE, actions::dismissRelease) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            Text(releaseLine(prompt.source), Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), color = Palette.secondary,
                fontSize = 13.sp, lineHeight = 18.sp)
            NEXT_EPISODE_COUNTS.forEach { count -> ReleaseChoice("Next $count", { actions.releaseNext(count) }) }
            ReleaseChoice(REST_OF_SEASON, actions::releaseSeason)
            ReleaseChoice(NOT_NOW, actions::dismissRelease, quiet = true)
        }
    }
}

@Composable
private fun ReleaseChoice(label: String, onClick: () -> Unit, quiet: Boolean = false) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).pointerClickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart) {
        Text(label, fontSize = 15.sp, fontWeight = if (quiet) FontWeight.Normal else FontWeight.SemiBold,
            color = if (quiet) Palette.secondary else Palette.text)
    }
}

@Composable
private fun TrackSection(title: String, tracks: List<DownloadTrack>, empty: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, Modifier.semantics { heading() }, color = Palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (tracks.isEmpty()) Text(empty, color = Palette.tertiary, fontSize = 14.sp)
        tracks.forEach { track ->
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Text(trackName(track), Modifier.weight(1f), fontSize = 14.sp)
                Text(trackOrigin(track), color = Palette.tertiary, fontSize = 13.sp)
            }
        }
    }
}
