package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.theme.Palette

@Composable
internal fun TvNavigation(selected: Screen, profile: Profile?, navigate: (Screen) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = maxWidth / 1920.dp
        Box(Modifier.fillMaxWidth().height(132.dp * scale).focusProperties {
            onEnter = { if (requestedFocusDirection == FocusDirection.Right) cancelFocusChange() }
        }.focusGroup()) {
        val initial = remember { FocusRequester() }
        LaunchedEffect(selected) {
            if (selected in listOf(Screen.Movies, Screen.Series, Screen.Anime, Screen.Live, Screen.Calendar, Screen.Library, Screen.History)) initial.requestFocus()
        }
        var menu by remember { mutableStateOf(false) }
        Box(Modifier.offset(96.dp * scale, 44.dp * scale)) {
            Brand(44.dp * scale, (32 * scale).sp, 12.dp * scale, (-1.28f * scale).sp, (32 * scale).sp)
        }
        val destinations = desktopDestinations.filter { it.screen !in listOf(Screen.Discover, Screen.History) }
        // One focus target per item, chained left/right: Compose's 2D search would otherwise drop from the nav into a card below.
        val navFocus = remember { List(destinations.size + 1) { androidx.compose.ui.focus.FocusRequester() } }
        val profileFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        Row(Modifier.offset(300.dp * scale, 38.dp * scale).height(56.dp * scale).focusGroup(),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            var searchFocused by remember { mutableStateOf(false) }
            // IconButton clips to a circle, so the ring is drawn by a wrapper outside that clip; its own focus layer would be a second ring.
            Box(Modifier.size(56.dp * scale).tvFocusOutline(searchFocused, 28.dp * scale, scale, width = 3.dp)) { OwnFocusTreatment {
                IconButton(onClick = { navigate(Screen.Search) },
                    modifier = Modifier.fillMaxSize().focusRequester(navFocus[0]).focusProperties {
                        left = androidx.compose.ui.focus.FocusRequester.Cancel
                        right = navFocus[1]
                        up = androidx.compose.ui.focus.FocusRequester.Cancel
                    }.onFocusChanged { searchFocused = it.isFocused }) {
                    Icon(Lucide.Search, "Search", Modifier.size(30.dp * scale))
                }
            } }
            destinations.forEachIndexed { index, destination ->
                var focused by remember { mutableStateOf(false) }
                val own = navFocus[index + 1]
                Box(Modifier.width(IntrinsicSize.Max).height(56.dp * scale).then(if (destination.screen == selected) Modifier.focusRequester(initial) else Modifier)
                    .focusRequester(own)
                    .focusProperties {
                        left = if (index == 0) navFocus[0] else navFocus[index]
                        right = if (index == destinations.lastIndex) profileFocus else navFocus[index + 2]
                        up = androidx.compose.ui.focus.FocusRequester.Cancel
                    }
                    .onFocusChanged { focused = it.isFocused }
                    // The selected item still needs a visible focus state: the ring sits on whatever the remote is on.
                    .background(if (focused) Palette.raised else Color.Transparent, RoundedCornerShape(8.dp * scale))
                    .tvFocusOutline(focused, 8.dp * scale, scale, width = 3.dp)
                    .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, shape = RoundedCornerShape(8.dp * scale)) { navigate(destination.screen) }
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
            var profileFocused by remember { mutableStateOf(false) }
            Box(Modifier.size(56.dp * scale).tvFocusOutline(profileFocused, 28.dp * scale, scale, width = 3.dp)) { OwnFocusTreatment {
                IconButton(onClick = { menu = true },
                    modifier = Modifier.fillMaxSize().focusRequester(profileFocus).focusProperties {
                        left = navFocus[destinations.size]
                        right = androidx.compose.ui.focus.FocusRequester.Cancel
                        up = androidx.compose.ui.focus.FocusRequester.Cancel
                    }.onFocusChanged { profileFocused = it.isFocused }) {
                    if (profile != null) ProfileAvatar(profile, 56.dp * scale, 28.dp * scale)
                    else Icon(Lucide.UserRound, "Profile menu", Modifier.size(28.dp * scale))
                }
            } }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Switch profile") }, onClick = { menu = false; navigate(Screen.Profiles) })
                DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; navigate(Screen.Settings) })
            }
        }
        }
    }
}
