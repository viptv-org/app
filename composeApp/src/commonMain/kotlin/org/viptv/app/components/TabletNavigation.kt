package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette

@Composable
internal fun TabletNavigation(selected: Screen, profile: Profile?, navigate: (Screen) -> Unit) {
    var castNotice by remember { mutableStateOf(false) }
    Column(Modifier.width(92.dp).fillMaxHeight().background(Palette.background), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp)); BrandMark(40.dp); Spacer(Modifier.height(36.dp))
        val entries = listOf(Destination(Screen.Home, "Home", Lucide.House), Destination(Screen.Live, "Live", Lucide.Tv),
            Destination(Screen.Library, "My List", Lucide.Bookmark), Destination(Screen.Search, "Search", Lucide.Search))
        entries.forEach { entry ->
            Column(Modifier.fillMaxWidth().height(80.dp).clickable { navigate(entry.screen) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp, 32.dp).background(if (selected == entry.screen) Palette.raised else Color.Transparent,
                    RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Icon(entry.icon, null, Modifier.size(24.dp)) }
                Spacer(Modifier.height(6.dp)); Text(entry.label, fontSize = 12.sp, lineHeight = 16.sp,
                    color = if (selected == entry.screen) Palette.text else Palette.secondary)
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { castNotice = true }, modifier = Modifier.size(56.dp)) { Icon(Lucide.Cast, "Cast", Modifier.size(24.dp)) }
        IconButton(onClick = { navigate(Screen.Settings) }, modifier = Modifier.size(56.dp)) { Icon(Lucide.Settings, "Settings", Modifier.size(24.dp)) }
        IconButton(onClick = { navigate(Screen.Profiles) }, modifier = Modifier.size(56.dp)) { ProfileAvatar(profile, 28.dp, 7.dp) }
        Spacer(Modifier.height(12.dp))
    }
    if (castNotice) AlertDialog(onDismissRequest = { castNotice = false }, title = { Text("Casting") },
        text = { Text("Casting is not available in this experiment yet.") },
        confirmButton = { TextButton(onClick = { castNotice = false }) { Text("OK") } })
}
