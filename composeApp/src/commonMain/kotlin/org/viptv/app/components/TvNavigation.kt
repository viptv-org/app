package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette

@Composable
internal fun TvNavigation(selected: Screen, profile: Profile?, navigate: (Screen) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = maxWidth / 1920.dp
        Box(Modifier.fillMaxWidth().height(132.dp * scale)) {
        val initial = remember { FocusRequester() }
        LaunchedEffect(selected) {
            if (selected in listOf(Screen.Home, Screen.Movies, Screen.Series, Screen.Live, Screen.Calendar, Screen.Library)) initial.requestFocus()
        }
        var menu by remember { mutableStateOf(false) }
        Box(Modifier.offset(96.dp * scale, 44.dp * scale)) {
            Brand(44.dp * scale, (32 * scale).sp, 12.dp * scale, (-1.28f * scale).sp, (32 * scale).sp)
        }
        Row(Modifier.offset(300.dp * scale, 38.dp * scale).height(56.dp * scale),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            IconButton(onClick = { navigate(Screen.Search) }, modifier = Modifier.size(56.dp * scale)) {
                Icon(Lucide.Search, "Search", Modifier.size(30.dp * scale))
            }
            desktopDestinations.filter { it.screen !in listOf(Screen.Discover, Screen.History) }.forEach { destination ->
                var focused by remember { mutableStateOf(false) }
                Box(Modifier.width(IntrinsicSize.Max).height(56.dp * scale).then(if (destination.screen == selected) Modifier.focusRequester(initial) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .background(if (focused && selected != destination.screen) Palette.raised else Color.Transparent, RoundedCornerShape(8.dp * scale))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { navigate(destination.screen) }
                    .padding(horizontal = 18.dp * scale), contentAlignment = Alignment.Center) {
                    Text(destination.label, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp,
                        color = if (selected == destination.screen || focused) Palette.text else Palette.body,
                        fontWeight = if (selected == destination.screen) FontWeight.Bold else FontWeight.Medium)
                    if (selected == destination.screen) Box(Modifier.align(Alignment.BottomCenter).offset(y = (-4).dp * scale)
                        .fillMaxWidth().height(3.dp * scale).background(Palette.text, RoundedCornerShape(2.dp * scale)))
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(end = 96.dp * scale, top = 38.dp * scale)) {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(56.dp * scale)) {
                if (profile != null) ProfileAvatar(profile, 56.dp * scale, 28.dp * scale)
                else Icon(Lucide.UserRound, "Profile menu", Modifier.size(28.dp * scale))
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Switch profile") }, onClick = { menu = false; navigate(Screen.Profiles) })
                DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; navigate(Screen.Settings) })
            }
        }
        }
    }
}
