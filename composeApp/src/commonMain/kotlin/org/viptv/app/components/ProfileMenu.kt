package org.viptv.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Users
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.features.entry.profileColor
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette

private class ProfileMenuAction(val label: String, val icon: ImageVector, val destination: Screen)

private val profileMenuActions = listOf(
    ProfileMenuAction("Switch profile", Lucide.Users, Screen.Profiles),
    ProfileMenuAction("Settings", Lucide.Settings, Screen.Settings),
)

/**
 * The profile picture of the non-TV headers ([PhoneHeader], [TopNavigation]) and the menu it
 * opens. [trigger] draws the picture and receives whether the menu is open; the button is
 * announced as "Profile menu". The menu names the current profile over a wash of its colour,
 * then offers Switch profile and Settings; choosing one dismisses the menu and navigates.
 */
@Composable
internal fun ProfileMenuButton(
    profile: Profile?,
    navigate: (Screen) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    trigger: @Composable (open: Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(modifier.clip(shape).semantics { contentDescription = "Profile menu" }.pointerClickable(shape = shape) { open = true }, contentAlignment = Alignment.Center) {
            Box(Modifier.clearAndSetSemantics {}) { trigger(open) }
        }
        PopoverMenu(open, { open = false }, wash = profile?.let(::profileColor)) {
            if (profile != null) {
                ProfileMenuHeader(profile)
                PopoverMenuDivider()
            }
            profileMenuActions.forEach { action ->
                PopoverMenuItem(action.label, action.icon) { open = false; navigate(action.destination) }
            }
        }
    }
}

@Composable
private fun ProfileMenuHeader(profile: Profile) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.clearAndSetSemantics {}) { ProfileAvatar(profile, 40.dp, 8.dp) }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("Watching as", color = Palette.tertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(profile.name, color = Palette.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
