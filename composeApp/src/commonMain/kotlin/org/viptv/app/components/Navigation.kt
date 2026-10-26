package org.viptv.app.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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

@Composable
fun TopNavigation(selected: Screen, television: Boolean, navigate: (Screen) -> Unit, profile: org.viptv.app.connection.Profile? = null) {
    if (television) { TvNavigation(selected, profile, navigate); return }
    val destinations = desktopDestinations.filter {
        it.screen != Screen.History
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val collapsed = maxWidth < 1000.dp
    val phone = maxWidth < 640.dp
    val visible = if (phone) emptyList() else if (collapsed) destinations.filter {
        it.screen in listOf(Screen.Home, Screen.Movies, Screen.Series, Screen.Live)
    } else destinations
    var overflow by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(68.dp)
        .padding(horizontal = if (collapsed) 16.dp else 57.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Box(Modifier.padding(end = if (collapsed) 0.dp else 14.dp)) { Brand(28.dp, 20.sp, 9.dp) }
        visible.forEach { destination ->
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val focused by interaction.collectIsFocusedAsState()
            Text(destination.label, Modifier.clip(RoundedCornerShape(8.dp))
                .clickable(interactionSource = interaction, indication = null) { navigate(destination.screen) }.padding(vertical = 10.dp),
                color = if (selected == destination.screen || hovered || focused) Palette.text else Palette.body,
                fontSize = 14.sp,
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
        ProfileMenuButton(profile, navigate, RoundedCornerShape(8.dp)) { open ->
            val chevron by animateFloatAsState(if (open) 180f else 0f, tween(150), label = "Profile menu chevron")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (profile != null) org.viptv.app.features.entry.ProfileAvatar(profile, 28.dp, 7.dp)
                else Icon(Lucide.UserRound, null, Modifier.size(20.dp))
                Icon(Lucide.ChevronDown, null, Modifier.size(14.dp).rotate(chevron))
            }
        }
    }
    }
}
