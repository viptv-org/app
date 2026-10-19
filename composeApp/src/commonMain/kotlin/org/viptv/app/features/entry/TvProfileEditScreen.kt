package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.connection.Profile
import org.viptv.app.features.settings.TvSettingsRow
import org.viptv.app.theme.Palette

internal enum class TvProfileEditAction { Name, Pin, Delete, Maturity, Language }

@Composable
internal fun TvProfileEditScreen(profile: Profile?, count: Int, busy: Boolean, error: String?,
    back: () -> Unit, rename: (String, String) -> Unit, setPin: (String, String) -> Unit,
    setAvatar: (String, String) -> Unit, delete: (String) -> Unit,
    maturity: String? = null, language: String? = null, backChanged: ((() -> Unit)?) -> Unit = {}, pinAvailable: Boolean = true, editingCurrent: Boolean = true, openSettings: ((String) -> Unit)? = null) {
    var action by remember(profile?.id) { mutableStateOf<TvProfileEditAction?>(null) }
    var avatar by remember { mutableStateOf(false) }
    val targets = remember { TvProfileEditAction.entries.associateWith { FocusRequester() } }
    val first = targets.getValue(TvProfileEditAction.Name); val avatarFocus = remember { FocusRequester() }
    var restore by remember { mutableStateOf<FocusRequester?>(null) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val currentBack by rememberUpdatedState(back)
    DisposableEffect(Unit) { backChanged { currentBack() }; onDispose { backChanged(null) } }
    // Same first-frame race as the PIN screen: wait until the row is placed,
    // otherwise the page opens with no focused control.
    LaunchedEffect(profile?.id) {
        repeat(60) {
            if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    LaunchedEffect(action, avatar, restore, windowFocused) {
        if (action == null && !avatar && windowFocused) restore?.let {
            withFrameNanos { }; it.requestFocus(); restore = null
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        Text("Edit profile", Modifier.offset(96.dp * scale, 96.dp * scale), fontSize = (52 * scale).sp,
            lineHeight = (59.8f * scale).sp, letterSpacing = (-1.04f * scale).sp, fontWeight = FontWeight.Bold)
        Column(Modifier.offset(96.dp * scale, 220.dp * scale).width(400.dp * scale), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
            ProfileAvatar(profile, 280.dp * scale, 36.dp * scale)
            TvAction(Modifier.width(280.dp * scale).height(72.dp * scale).focusProperties { right = first }, scale,
                requester = avatarFocus, enabled = !busy, activate = { avatar = true }) {
                Icon(Lucide.Pencil, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
                Text("Change avatar", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.offset(640.dp * scale, 220.dp * scale).width(1184.dp * scale), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
            TvSettingsRow("Name", scale = scale, value = profile?.name ?: "Unavailable",
                requester = first, left = avatarFocus, activate = { if (!busy) action = TvProfileEditAction.Name })
            TvSettingsRow("Maturity rating", "Titles above this rating are hidden for this profile", scale,
                value = maturity ?: if(pinAvailable) "Not available locally" else "Account policy", left = avatarFocus,
                requester = targets.getValue(TvProfileEditAction.Maturity), activate = { action = TvProfileEditAction.Maturity })
            if (pinAvailable) TvSettingsRow("Profile lock", "Ask for a PIN before this profile opens", scale, checked = profile?.locked == true,
                left = avatarFocus, requester = targets.getValue(TvProfileEditAction.Pin),
                activate = { if (!busy) action = TvProfileEditAction.Pin })
            TvSettingsRow("Language", "Menus and default audio for this profile", scale,
                value = language ?: if(editingCurrent) "In Settings" else "Select profile", left = avatarFocus,
                requester = targets.getValue(TvProfileEditAction.Language), activate = { action = TvProfileEditAction.Language })
            Spacer(Modifier.height(120.dp * scale))
            if (count > 1 && profile?.canDelete != false) TvSettingsRow("Delete profile", scale = scale, rowHeight = 80,
                labelColor = Color(0xFFFF8A80), left = avatarFocus, requester = targets.getValue(TvProfileEditAction.Delete),
                activate = { if (!busy) action = TvProfileEditAction.Delete })
            error?.let { Text(it, color = Palette.danger, fontSize = (24 * scale).sp) }
        }
        action?.let { selected -> TvProfileEditDialog(selected, profile, busy,
            { restore = targets.getValue(selected); action = null }, rename, setPin, delete, pinAvailable, editingCurrent, openSettings, scale) }
    }
    if (avatar) AvatarPicker(profile, busy, { restore = avatarFocus; avatar = false }, { id, pin ->
        setAvatar(id, pin); restore = avatarFocus; avatar = false
    }, pinAvailable = pinAvailable)
}
