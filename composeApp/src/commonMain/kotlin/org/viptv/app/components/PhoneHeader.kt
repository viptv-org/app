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
import com.composables.icons.lucide.Cast
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
            Brand()
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { unavailable = "Casting" }, modifier = Modifier.size(40.dp)) {
                Icon(Lucide.Cast, "Cast", Modifier.size(22.dp))
            }
            IconButton(onClick = { unavailable = "Remote control" }, modifier = Modifier.size(40.dp)) {
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
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("All" to Screen.Home, "Movies" to Screen.Movies, "Series" to Screen.Series).forEach { (label, destination) ->
                    Surface(onClick = { navigate(destination) }, shape = RoundedCornerShape(20.dp),
                        color = if (destination == screen) Palette.text else Palette.raised,
                        contentColor = if (destination == screen) Palette.background else Palette.text) {
                        Text(label, Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
                Surface(onClick = { unavailable = "Genre filtering" }, shape = RoundedCornerShape(20.dp), color = Palette.raised) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Lucide.ChevronDown, null, Modifier.size(14.dp))
                        Text("Genres")
                    }
                }
            }
        }
    }
    unavailable?.let { name ->
        AlertDialog(onDismissRequest = { unavailable = null }, title = { Text(name) },
            text = { Text("$name is not available in this experiment yet.") },
            confirmButton = { TextButton(onClick = { unavailable = null }) { Text("OK") } })
    }
}
