package org.viptv.app.preview

import androidx.compose.runtime.*
import org.viptv.app.features.entry.TvProfileEditScreen
import org.viptv.app.state.Screen

@Composable
internal fun PreviewTvProfileEdit(navigate: (Screen) -> Unit, backChanged: ((() -> Unit)?) -> Unit, profileId: String) {
    var profile by remember(profileId) { mutableStateOf((PreviewData.profiles.find { it.id == profileId } ?: PreviewData.profile).copy(locked = false)) }
    TvProfileEditScreen(profile, 4, false, null, { navigate(Screen.Profiles) },
        rename = { name, _ -> profile = profile.copy(name = name) },
        setPin = { _, pin -> profile = profile.copy(locked = pin.isNotEmpty()) },
        setAvatar = { id, _ -> profile = profile.copy(avatarId = id) }, delete = { navigate(Screen.Profiles) },
        maturity = "All ratings", language = "English", backChanged = backChanged)
}
