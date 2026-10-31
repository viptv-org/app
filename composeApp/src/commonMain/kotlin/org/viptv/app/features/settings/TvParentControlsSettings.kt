package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.TvFocusColumn
import org.viptv.app.connection.*
import org.viptv.app.theme.Palette

@Composable
internal fun TvParentControlsSettings(snapshot: ProfileManagement?, controls: Map<String, ParentProfileControls>,
    busy: Boolean, error: String?, notice: String?, saved: Long, load: () -> Unit, unlock: (String) -> Unit,
    pin: (String, String) -> Unit, policy: (String, KidsPolicy) -> Unit, leavePin: (String, String, String) -> Unit,
    titles: (String) -> Unit, first: FocusRequester, sidebar: FocusRequester, scale: Float) {
    var pinMode by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ServerProfile?>(null) }
    var leavePinFor by remember { mutableStateOf<ServerProfile?>(null) }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(saved) { pinMode = null; editing = null; leavePinFor = null }
    // The page also re-enters composition when the approved-titles page closes, and that removal drops focus.
    LaunchedEffect(snapshot != null) {
        if (snapshot != null) { withFrameNanos { }; runCatching { first.requestFocus() } }
    }
    TvFocusColumn(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
            TvSettingsHeading("Parental controls", scale,
                "The household PIN is required to leave a restricted profile or manage settings from it.")
            if (busy) Text("Saving…", color = Palette.secondary, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
            error?.let { Text(it, color = Palette.danger, fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp) }
            notice?.let { Text(it, color = Palette.secondary, fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp) }
            val current = snapshot
            if (current == null) {
                if (!busy) TvSettingsRow("Retry parental controls", scale = scale,
                    requester = first, left = sidebar, leading = Lucide.Lock) { load() }
            } else if (current.parent.restricted && !current.parent.unlocked) {
                TvSettingsRow("Unlock parental controls", subtitle = "Enter the parent PIN to manage this household.",
                    scale = scale, requester = first, left = sidebar, leading = Lucide.Lock) { pinMode = "unlock" }
            } else {
                TvSettingsRow(if (current.parent.pin_configured) "Change parent PIN" else "Set parent PIN",
                    subtitle = if (current.parent.pin_configured) "A parent PIN is set."
                    else "Set a PIN before enabling kids restrictions.",
                    scale = scale, requester = first, left = sidebar, leading = Lucide.Lock) { pinMode = "save" }
                if (current.parent.pin_configured && current.profiles.none { it.kids })
                    TvSettingsRow("Remove parent PIN", subtitle = "Kids restrictions are off for every profile.",
                        scale = scale, left = sidebar) { pinMode = "remove" }
                if (current.profiles.any { it.kids })
                    Text("Turn off kids restrictions on all profiles before removing the PIN.",
                        color = Palette.secondary, fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp)
                Spacer(Modifier.height(12.dp * scale))
                Text("Profile restrictions", color = Palette.text, fontSize = (30 * scale).sp, lineHeight = (38 * scale).sp)
                current.profiles.forEach { profile ->
                    val profileControls = controls[profile.id]
                    TvSettingsRow(profile.name, subtitle = kidsSummary(profile, profileControls?.policy),
                        scale = scale, left = sidebar, value = "Edit") { editing = profile }
                    if (profile.kids) TvSettingsRow("Approved and blocked titles",
                        subtitle = "Allow or block individual titles for ${profile.name}.",
                        scale = scale, left = sidebar) { titles(profile.id) }
                    if (current.profilePinAvailable) TvSettingsRow(
                        if (profileControls?.leavePin != false) "Change leave PIN" else "Set leave PIN",
                        subtitle = leavePinSummary(profileControls?.leavePin),
                        scale = scale, left = sidebar) { leavePinFor = profile }
                }
            }
        }
    }
    pinMode?.let { mode -> ParentPinDialog(mode, snapshot?.parent?.pin_configured == true, busy, error,
        dismiss = { if (!busy) pinMode = null }, submit = { current, next -> if (mode == "unlock") unlock(current) else pin(current, next) }) }
    editing?.let { profile -> KidsPolicyDialog(profile, controls[profile.id]?.policy ?: fallbackPolicy(profile),
        snapshot?.parent?.pin_configured == true, busy, error,
        dismiss = { if (!busy) editing = null }, save = { policy(profile.id, it) }) }
    leavePinFor?.let { profile -> LeavePinDialog(profile.name, controls[profile.id]?.leavePin != false, busy, error,
        dismiss = { if (!busy) leavePinFor = null }, submit = { current, next -> leavePin(profile.id, current, next) }) }
}
