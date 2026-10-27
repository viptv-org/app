package org.viptv.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.state.Screen

/** The phone header: the page title or wordmark, the profile menu and, on Home, the filter chips. */
@Composable
internal fun PhoneHeader(screen: Screen, profile: Profile?, navigate: (Screen) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (screen in listOf(Screen.Library, Screen.Live, Screen.Calendar)) Text(when (screen) { Screen.Library -> "My List"; Screen.Live -> "Live"; else -> "Calendar" }, fontSize = 28.sp, letterSpacing = (-0.56).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else Brand(28.dp, 20.sp, 9.dp)
            Spacer(Modifier.weight(1f))
            ProfileMenuButton(profile, navigate, RoundedCornerShape(10.dp), Modifier.size(40.dp)) { ProfileAvatar(profile, 28.dp, 7.dp) }
        }
        if (screen == Screen.Home) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                HomeFilters(screen, navigate)
            }
        }
    }
}
