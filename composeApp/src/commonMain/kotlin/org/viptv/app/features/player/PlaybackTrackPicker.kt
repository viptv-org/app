package org.viptv.app.features.player

import org.viptv.app.components.FocusLazyColumn as LazyColumn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.*
import org.viptv.app.components.IconButton
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.rememberArriveFade
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.connection.subtitleMode
import org.viptv.app.features.settings.PhonePreferenceDialog
import org.viptv.app.features.settings.PhonePreferenceField
import org.viptv.app.features.settings.PhonePreferenceSegments
import org.viptv.app.features.settings.phonePreferenceLanguage
import org.viptv.app.features.settings.subtitleModeLabel
import org.viptv.app.player.*
import org.viptv.app.theme.Palette

@Composable
internal fun PlaybackTrackPicker(tracks: PlaybackTracks, select: (PlayerCommand.SelectTrack) -> Unit,
    close: () -> Unit, phone: Boolean, setSize: (Float) -> Unit,
    preferences: ProfilePreferences, onPreferences: (ProfilePreferences) -> Unit,
    setBackground: (String?) -> Unit, insets: WindowInsets, bottomInset: Dp = 76.dp, openSettings: (() -> Unit)? = null) {
    val reveal = rememberArriveFade()
    var dialog by remember { mutableStateOf<PhonePreferenceField?>(null) }
    var draft by remember(preferences) { mutableStateOf(preferences) }
    val apply = { next: ProfilePreferences -> draft = next; onPreferences(next) }
    Popup(onDismissRequest = close, properties = PopupProperties(focusable = true)) {
        BoxWithConstraints(Modifier.fillMaxSize().alpha(reveal)) {
            Box(Modifier.fillMaxSize().pointerClickable(onClick = close))
            val panel = if (phone) Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.82f)
                else Modifier.align(Alignment.BottomEnd).padding(end = 150.dp, bottom = bottomInset).widthIn(max = 680.dp).fillMaxWidth().heightIn(max = 460.dp)
            Column(panel.clip(RoundedCornerShape(if (phone) 0.dp else 12.dp)).background(Palette.surface)
                .semantics { paneTitle = "Audio and subtitles" }
                .border(1.dp, Palette.raised, RoundedCornerShape(if (phone) 0.dp else 12.dp))
                .then(if (phone) Modifier.windowInsetsPadding(insets.endSheet()) else Modifier)
                .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Audio & subtitles", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(close, Modifier.size(28.dp)) {
                        Icon(Lucide.X, "Close track selection", Modifier.size(16.dp))
                    }
                }
                if (phone) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AudioColumn(tracks.audio, select, scrollable = false)
                        SubtitlesColumn(tracks.subtitles, select, scrollable = false)
                        SubtitleSettingsColumn(draft, apply, setSize, setBackground) { dialog = it }
                    }
                } else Row(Modifier.weight(1f, fill = false).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AudioColumn(tracks.audio, select, if (tracks.audio.size <= 1) Modifier else Modifier.weight(1f))
                    SubtitlesColumn(tracks.subtitles, select, Modifier.weight(1.4f))
                    SubtitleSettingsColumn(draft, apply, setSize, setBackground, Modifier.weight(1.2f)) { dialog = it }
                }
                if (openSettings != null) PlaybackSettingsLink(openSettings)
            }
        }
    }
    dialog?.let { field ->
        PhonePreferenceDialog(field, draft, busy = false, update = apply, close = { dialog = null })
    }
}

internal const val PLAYER_SETTINGS_LINK = "Subtitles & audio settings"

@Composable
private fun PlaybackSettingsLink(open: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    HorizontalDivider(color = Palette.raised)
    Row(Modifier.fillMaxWidth().height(40.dp).onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, 8.dp, width = 2.dp).clip(RoundedCornerShape(8.dp))
        .pointerClickable(role = Role.Button, onClick = open, shape = RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Lucide.Settings, null, Modifier.size(16.dp))
        Text(PLAYER_SETTINGS_LINK, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
        Icon(Lucide.ChevronRight, null, Modifier.size(16.dp), tint = Palette.secondary)
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, fontSize = 12.sp, color = Palette.secondary, fontWeight = FontWeight.SemiBold)

@Composable
private fun AudioColumn(tracks: List<PlaybackTrack>, select: (PlayerCommand.SelectTrack) -> Unit, modifier: Modifier = Modifier, scrollable: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("Audio")
        if (tracks.size <= 1) {
            Text("Default audio", fontSize = 14.sp, lineHeight = 20.sp, color = Palette.secondary)
        } else if (scrollable) {
            LazyColumn(topEdgeFade = false) {
                items(tracks, key = { it.id }) { track -> audioRow(track, select) }
            }
        } else Column { tracks.forEach { track -> audioRow(track, select) } }
    }
}

@Composable
private fun audioRow(track: PlaybackTrack, select: (PlayerCommand.SelectTrack) -> Unit) {
    PlaybackTrackRow(track.label, null, track.selected) { select(PlayerCommand.SelectTrack(PlaybackTrackKind.Audio, track.id)) }
}

@Composable
private fun SubtitlesColumn(tracks: List<PlaybackTrack>, select: (PlayerCommand.SelectTrack) -> Unit, modifier: Modifier = Modifier, scrollable: Boolean = true) {
    val groups = remember(tracks) { groupSubtitleTracks(tracks) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("Subtitles")
        if (tracks.isEmpty()) {
            Text("No subtitle tracks available", fontSize = 12.sp, color = Palette.secondary)
        } else if (scrollable) {
            LazyColumn(topEdgeFade = false) {
                item(key = "off") { offRow(tracks, select) }
                items(groups, key = { it.ids.joinToString() }) { group -> groupRow(group, select) }
            }
        } else Column {
            offRow(tracks, select)
            groups.forEach { group -> groupRow(group, select) }
        }
    }
}

@Composable
private fun offRow(tracks: List<PlaybackTrack>, select: (PlayerCommand.SelectTrack) -> Unit) {
    PlaybackTrackRow("Off", null, tracks.none { it.selected }) { select(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, null)) }
}

@Composable
private fun groupRow(group: SubtitleGroup, select: (PlayerCommand.SelectTrack) -> Unit) {
    PlaybackTrackRow(group.primary, group.source, group.selected) { select(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, group.ids.first())) }
}

@Composable
private fun SubtitleSettingsColumn(preferences: ProfilePreferences, update: (ProfilePreferences) -> Unit,
    setSize: (Float) -> Unit, setBackground: (String?) -> Unit, modifier: Modifier = Modifier,
    openChoice: (PhonePreferenceField) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingSection("Subtitles") {
            SettingsSelectRow(subtitleModeLabel(preferences.subtitleMode, preferences.preferred_audio_language)) { openChoice(PhonePreferenceField.SubtitleMode) }
        }
        SettingSection("Language") {
            SettingsSelectRow(phonePreferenceLanguage(preferences.preferred_subtitle_language, false)) { openChoice(PhonePreferenceField.SubtitleLanguage) }
        }
        SettingSection("Size") {
            PhonePreferenceSegments(listOf("0.75" to "Small", "1.0" to "Medium", "1.25" to "Large"),
                preferences.subtitle_font_scale.toString(), true) { value ->
                val scale = value.toFloatOrNull() ?: return@PhonePreferenceSegments
                setSize(scale)
                update(preferences.copy(subtitle_font_scale = scale.toDouble()))
            }
        }
        SettingSection("Background") {
            PhonePreferenceSegments(listOf("none" to "None", "box" to "Box"),
                if (preferences.subtitle_background_color.equals("#00000000", true)) "none" else "box", true) { selected ->
                val background = if (selected == "none") "#00000000" else
                    preferences.subtitle_background_color.takeUnless { it.equals("#00000000", true) } ?: "#B3000000"
                setBackground(if (selected == "none") null else background)
                update(preferences.copy(subtitle_background_color = background))
            }
        }
    }
}

@Composable
private fun SettingSection(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(label)
        content()
    }
}

@Composable
private fun SettingsSelectRow(value: String, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF272727))
        .pointerClickable(role = Role.Button, shape = RoundedCornerShape(20.dp), onClick = choose)
        .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(value, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.text)
        Icon(Lucide.ChevronRight, null, Modifier.size(16.dp), tint = Palette.tertiary)
    }
}