package org.viptv.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Cast
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_remote_zip
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette

@Composable
internal fun PhoneHeader(screen: Screen, profile: Profile?, navigate: (Screen) -> Unit) {
    var profileMenu by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (screen in listOf(Screen.Library, Screen.Live, Screen.Calendar)) Text(when (screen) { Screen.Library -> "My List"; Screen.Live -> "Live"; else -> "Calendar" }, fontSize = 28.sp, letterSpacing = (-0.56).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else Brand(28.dp, 20.sp, 9.dp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { unavailable = if (screen == Screen.Calendar) "Calendar filters" else "Casting" }, modifier = Modifier.size(40.dp)) {
                Icon(if (screen == Screen.Calendar) Lucide.SlidersHorizontal else Lucide.Cast, if (screen == Screen.Calendar) "Calendar filters" else "Cast", Modifier.size(22.dp))
            }
            if (screen !in listOf(Screen.Library, Screen.Live, Screen.Calendar)) IconButton(onClick = { unavailable = "Remote control" }, modifier = Modifier.size(40.dp)) {
                Icon(painterResource(Res.drawable.ic_remote_zip), "Remote control", Modifier.size(22.dp))
            }
            Box {
                Box(Modifier.size(40.dp).clickable { profileMenu = true }, contentAlignment = Alignment.Center) {
                    ProfileAvatar(profile, 28.dp, 7.dp)
                }
                DropdownMenu(profileMenu, onDismissRequest = { profileMenu = false }) {
                    DropdownMenuItem(text = { Text("Switch profile") }, onClick = { profileMenu = false; navigate(Screen.Profiles) })
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { profileMenu = false; navigate(Screen.Settings) })
                }
            }
        }
        if (screen == Screen.Home) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                HomeFilters(screen, navigate) { unavailable = "Genre filtering" }
            }
        }
    }
    unavailable?.let { name ->
        AlertDialog(onDismissRequest = { unavailable = null }, title = { Text(name) },
            text = { Text("$name is not available in this experiment yet.") },
            confirmButton = { TextButton(onClick = { unavailable = null }) { Text("OK") } })
    }
}
