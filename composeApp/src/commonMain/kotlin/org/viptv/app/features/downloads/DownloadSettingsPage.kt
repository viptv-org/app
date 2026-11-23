package org.viptv.app.features.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TextButton
import org.viptv.app.components.pointerClickable
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.pageBottomClearance
import org.viptv.app.components.pointerSelectable
import org.viptv.app.downloads.Downloads
import org.viptv.app.features.settings.PhonePreferenceDivider
import org.viptv.app.features.settings.PhonePreferenceGroup
import org.viptv.app.features.settings.PhonePreferenceRow
import org.viptv.app.features.settings.PhonePreferenceSwitchRow
import org.viptv.app.theme.Palette

private enum class DownloadSettingsDialog { Quality, StorageLimit, DeleteWatched, Smart }

@Composable
internal fun DownloadSettingsPage(downloads: Downloads, compact: Boolean) {
    val settings by downloads.settings.collectAsState()
    val storage by downloads.storage.collectAsState()
    var dialog by remember { mutableStateOf<DownloadSettingsDialog?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = if (compact) 16.dp else 32.dp, vertical = if (compact) 8.dp else 32.dp).pageBottomClearance()
        .widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PhonePreferenceGroup {
            PhonePreferenceSwitchRow("Download on Wi‑Fi only", "Waits for Wi‑Fi before downloading.", settings.wifiOnly) {
                downloads.updateSettings(settings.copy(wifiOnly = it))
            }
            PhonePreferenceDivider()
            DescribedRow("Download quality", "Highest quality to download.", qualityLabel(settings.maxQualityHeight)) { dialog = DownloadSettingsDialog.Quality }
            PhonePreferenceDivider()
            DescribedRow("Storage limit", "Old downloads are deleted to stay under it.", storageLimitLabel(settings.storageLimitBytes)) { dialog = DownloadSettingsDialog.StorageLimit }
            PhonePreferenceDivider()
            PhonePreferenceRow("Delete watched downloads", deleteWatchedLabel(settings.deleteWatchedAfterMillis), true) { dialog = DownloadSettingsDialog.DeleteWatched }
            PhonePreferenceDivider()
            DescribedRow("Smart downloads", "Keeps the next episodes of a series you're watching downloaded.", smartDownloadLabel(settings.keepNextEpisodes)) { dialog = DownloadSettingsDialog.Smart }
            PhonePreferenceDivider()
            PhonePreferenceSwitchRow(DOWNLOAD_SUBTITLES, DOWNLOAD_SUBTITLES_BODY, settings.downloadSubtitles) {
                downloads.updateSettings(settings.copy(downloadSubtitles = it))
            }
        }
        Text(settingsStorageLine(storage), Modifier.padding(horizontal = 16.dp), color = Palette.secondary, fontSize = 13.sp)
    }
    val close = { dialog = null }
    when (dialog) {
        DownloadSettingsDialog.Quality -> DownloadChoiceDialog("Download quality", qualityChoices, settings.maxQualityHeight,
            { downloads.updateSettings(settings.copy(maxQualityHeight = it)) }, close)
        DownloadSettingsDialog.StorageLimit -> DownloadChoiceDialog("Storage limit", storageLimitChoices(storage), settings.storageLimitBytes,
            { downloads.updateSettings(settings.copy(storageLimitBytes = it)) }, close)
        DownloadSettingsDialog.DeleteWatched -> DownloadChoiceDialog("Delete watched downloads", deleteWatchedChoices, settings.deleteWatchedAfterMillis,
            { downloads.updateSettings(settings.copy(deleteWatchedAfterMillis = it)) }, close)
        DownloadSettingsDialog.Smart -> DownloadChoiceDialog("Smart downloads", smartDownloadChoices, settings.keepNextEpisodes,
            { downloads.updateSettings(settings.copy(keepNextEpisodes = it)) }, close)
        null -> Unit
    }
}

@Composable
private fun DescribedRow(label: String, description: String, value: String, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).pointerClickable(onClick = choose).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Palette.secondary, fontSize = 13.sp)
        }
        Text(value, color = Palette.secondary, fontSize = 14.sp, maxLines = 1)
        Icon(Lucide.ChevronRight, null, Modifier.size(17.dp), tint = Palette.tertiary)
    }
}

@Composable
internal fun <T> DownloadChoiceDialog(title: String, choices: List<Pair<T, String>>, selected: T, choose: (T) -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            choices.forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).pointerSelectable(value == selected, role = Role.RadioButton,
                    onClick = { choose(value); close() }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(value == selected, onClick = null); Spacer(Modifier.width(8.dp)); Text(label)
                }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(close) { Text("Cancel") } })
}
