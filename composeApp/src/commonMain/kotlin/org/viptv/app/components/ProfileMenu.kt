package org.viptv.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
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

internal data class ProfileMenuAction(val label: String, val icon: ImageVector, val destination: Screen)

/** What the profile picture offers in every non-TV header. */
internal val profileMenuActions = listOf(
    ProfileMenuAction("Switch profile", Lucide.Users, Screen.Profiles),
    ProfileMenuAction("Settings", Lucide.Settings, Screen.Settings),
)

/**
 * The menu opened by the profile picture in [PhoneHeader] and [TopNavigation]. Place it in the
 * same Box as the trigger. A header names the current profile; the top of the card takes a wash
 * of the profile's colour. Choosing an action dismisses the menu, then navigates.
 */
@Composable
internal fun ProfileMenu(expanded: Boolean, profile: Profile?, onDismissRequest: () -> Unit, navigate: (Screen) -> Unit) {
    PopoverMenu(expanded, onDismissRequest, wash = profile?.let(::profileColor)) {
        if (profile != null) {
            ProfileMenuHeader(profile)
            PopoverMenuDivider()
        }
        profileMenuActions.forEach { action ->
            PopoverMenuItem(action.label, action.icon) { onDismissRequest(); navigate(action.destination) }
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
