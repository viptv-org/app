package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.*

private val destinations = listOf(Screen.Home to "Home", Screen.Search to "Search", Screen.Live to "Live", Screen.Library to "My List", Screen.History to "History")

@Composable
fun TopNavigation(selected: Screen, television: Boolean, navigate: (Screen) -> Unit) {
    Row(Modifier.fillMaxWidth().height(if (television) 84.dp else 62.dp).padding(horizontal = if (television) 70.dp else 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Brand()
        destinations.forEach { (screen, label) ->
            TextButton(onClick = { navigate(screen) }) { Text(label, color = if (selected == screen) Palette.text else Palette.secondary, fontSize = if (television) 20.sp else 13.sp) }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { navigate(Screen.Profiles) }) { Text("Profile") }
    }
}

@Composable
fun SideNavigation(selected: Screen, navigate: (Screen) -> Unit) {
    Column(Modifier.width(72.dp).fillMaxHeight().padding(horizontal = 10.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        destinations.forEach { (screen, label) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (screen == selected) Palette.raised else Palette.background).clickable { navigate(screen) }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val icon = when (screen) { Screen.Home -> Res.drawable.ic_home; Screen.Search -> Res.drawable.ic_search; Screen.Live -> Res.drawable.ic_live; Screen.Library -> Res.drawable.ic_list; else -> Res.drawable.ic_history }
                Icon(painterResource(icon), label, Modifier.size(23.dp))
                Text(label, fontSize = 9.sp, color = Palette.secondary)
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { navigate(Screen.Settings) }) { Icon(painterResource(Res.drawable.ic_settings), "Settings", Modifier.size(23.dp)) }
    }
}

@Composable
fun BottomNavigation(selected: Screen, navigate: (Screen) -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).background(Palette.surface), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        destinations.take(4).forEach { (screen, label) -> TextButton(onClick = { navigate(screen) }) { Text(label, color = if (screen == selected) Palette.text else Palette.secondary, fontSize = 12.sp) } }
    }
}
