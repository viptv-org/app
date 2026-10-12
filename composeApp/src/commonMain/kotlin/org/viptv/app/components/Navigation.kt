package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_remote_zip

internal data class Destination(val screen: Screen, val label: String, val icon: ImageVector)

internal val desktopDestinations = listOf(
    Destination(Screen.Home, "Home", Lucide.House),
    Destination(Screen.Discover, "Discover", Lucide.Compass),
    Destination(Screen.Movies, "Movies", Lucide.Film),
    Destination(Screen.Series, "Series", Lucide.Tv),
    Destination(Screen.Live, "Live", Lucide.RadioReceiver),
    Destination(Screen.Calendar, "Calendar", Lucide.Calendar),
    Destination(Screen.Library, "My List", Lucide.Bookmark),
    Destination(Screen.History, "History", Lucide.History),
)

@Composable
fun TopNavigation(selected: Screen, television: Boolean, navigate: (Screen) -> Unit) {
    val destinations = desktopDestinations.filter {
        it.screen != Screen.History && (!television || it.screen != Screen.Discover)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val collapsed = !television && maxWidth < 1000.dp
    val phone = !television && maxWidth < 640.dp
    val visible = if (phone) emptyList() else if (collapsed) destinations.filter {
        it.screen in listOf(Screen.Home, Screen.Movies, Screen.Series, Screen.Live)
    } else destinations
    var overflow by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(if (television) 84.dp else 62.dp)
        .padding(horizontal = if (television) 70.dp else if (collapsed) 16.dp else 36.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (television) 28.dp else 20.dp)) {
        Brand()
        visible.forEach { destination ->
            Text(destination.label, Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { navigate(destination.screen) }.padding(vertical = 10.dp),
                color = if (selected == destination.screen) Palette.text else Palette.secondary,
                fontSize = if (television) 20.sp else 13.sp,
                fontWeight = if (selected == destination.screen) FontWeight.Bold else FontWeight.Medium)
        }
        Spacer(Modifier.weight(1f))
        if (collapsed) Box {
            IconButton(onClick = { overflow = true }) { Icon(Lucide.Menu, "Navigation", Modifier.size(20.dp)) }
            DropdownMenu(overflow, { overflow = false }) {
                destinations.filter { it !in visible }.forEach { destination ->
                    DropdownMenuItem(text = { Text(destination.label) }, leadingIcon = { Icon(destination.icon, null, Modifier.size(20.dp)) },
                        onClick = { navigate(destination.screen); overflow = false })
                }
            }
        }
        IconButton(onClick = { navigate(Screen.Search) }) { Icon(Lucide.Search, "Search", Modifier.size(20.dp)) }
        IconButton(onClick = { navigate(Screen.Settings) }) { Icon(Lucide.Settings, "Settings", Modifier.size(20.dp)) }
        IconButton(onClick = { navigate(Screen.Profiles) }) { Icon(Lucide.UserRound, "Profile", Modifier.size(20.dp)) }
    }
    }
}

@Composable
fun SideNavigation(selected: Screen, navigate: (Screen) -> Unit) {
    Column(Modifier.width(72.dp).fillMaxHeight().background(Palette.background), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        desktopDestinations.forEach { destination ->
            if (destination.screen == Screen.Library) {
                HorizontalDivider(Modifier.width(32.dp).padding(vertical = 8.dp), color = Color.White.copy(alpha = .08f))
            }
            Column(Modifier.width(56.dp).height(48.dp).clip(RoundedCornerShape(10.dp))
                .background(if (destination.screen == selected) Color(0xFF2A2A2D) else Color.Transparent)
                .clickable { navigate(destination.screen) }, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                val tint = if (destination.screen == selected) Palette.text else Palette.secondary
                if (destination.screen == Screen.Live) Icon(painterResource(Res.drawable.ic_remote_zip), destination.label, Modifier.size(20.dp), tint = tint)
                else Icon(destination.icon, destination.label, Modifier.size(20.dp), tint = tint)
                Spacer(Modifier.height(3.dp))
                Text(destination.label, fontSize = 10.sp, lineHeight = 12.sp, color = Palette.text)
            }
            if (destination.screen != Screen.Calendar) Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { navigate(Screen.Settings) }) { Icon(Lucide.Puzzle, "Add-ons", Modifier.size(20.dp)) }
        IconButton(onClick = { navigate(Screen.Settings) }) { Icon(Lucide.Settings, "Settings", Modifier.size(20.dp)) }
        Spacer(Modifier.height(8.dp))
    }
}
