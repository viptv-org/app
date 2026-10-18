package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.theme.Palette

@Composable
internal fun SettingsSidebar(selected: String, profile: Profile?, profiles: () -> Unit, stremioImport: Boolean = false, select: (String) -> Unit) {
    LazyColumn(Modifier.width(240.dp).fillMaxHeight().background(Palette.surface), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Settings", Modifier.padding(vertical = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth().clickable(onClick = profiles).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(profile, 40.dp, 10.dp)
                Column { Text(profile?.name ?: "Profile", fontWeight = FontWeight.SemiBold); Text("Switch profile", fontSize = 12.sp, color = Palette.secondary) }
            }
        }
        val entries = listOfNotNull("General" to Lucide.Settings, "Playback" to Lucide.CirclePlay, "Subtitles & audio" to Lucide.Captions,
            "Watch on TV" to Lucide.Tv, "Add-ons" to Lucide.Puzzle, "Notifications" to Lucide.Bell,
            "Parental controls" to Lucide.LockKeyhole, "Account" to Lucide.UserRound,
            (stremioImportSection to Lucide.Import).takeIf { stremioImport }, "Help" to Lucide.Info, "About" to Lucide.Info)
        entries.forEach { (label, icon) -> item {
            Surface(color = if (selected == label) Palette.raised else Palette.surface, shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.fillMaxWidth().clickable { select(label) }.padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icon, null, Modifier.size(20.dp))
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } }
    }
}

@Composable
internal fun SettingsSection(section: String, local: Boolean, busy: Boolean, error: String?, notice: String?, leave: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(section, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        notice?.let { Text(it, color = Palette.danger) }
        when (section) {
            "Account" -> {
                Text(if (local) "Using this device locally" else "Connected to a server", color = Palette.secondary)
                TextButton(onClick = leave, enabled = !busy) { Text(if (local) "Change connection" else "Sign out", color = Palette.danger) }
            }
            "About" -> Text("VIPTV · Experimental build", color = Palette.secondary)
            else -> Text("$section is not available on this connection yet.", color = Palette.secondary)
        }
        error?.let { Text(it, color = Palette.danger) }
    }
}
