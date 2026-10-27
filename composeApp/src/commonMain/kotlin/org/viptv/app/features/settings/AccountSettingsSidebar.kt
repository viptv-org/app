package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable

/** W3Account: browser account navigation, distinct from native device settings. */
@Composable
internal fun AccountSettingsSidebar(selected: String, profiles: () -> Unit, stremioImport: Boolean = false, select: (String) -> Unit) {
    LazyColumn(Modifier.width(240.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item { Text("Account", Modifier.padding(start = 12.dp, bottom = 14.dp), fontSize = 26.sp, lineHeight = 30.sp,
            letterSpacing = (-.52).sp, fontWeight = FontWeight.Bold) }
        val entries = listOfNotNull("Account" to Lucide.UserRound, "Profiles" to Lucide.UsersRound, "Devices" to Lucide.Monitor,
            (stremioImportSection to Lucide.Import).takeIf { stremioImport },
            "Parental controls" to Lucide.LockKeyhole, "Add-ons" to Lucide.Puzzle, "Playback" to Lucide.CirclePlay, "Subtitles & audio" to Lucide.Captions)
        entries.forEach { (label, icon) -> item(key = label) {
            Surface(color = if (selected == label) Palette.text.copy(alpha = .1f) else Palette.background,
                shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).pointerClickable(shape = RoundedCornerShape(8.dp)) {
                    if (label == "Profiles") profiles() else select(label)
                }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(icon, null, Modifier.size(19.dp))
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } }
    }
}
