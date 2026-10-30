package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

internal data class TvAccountInfo(val email: String? = null, val devices: Int? = null, val username: String? = null)

@Composable
internal fun TvSettingsAccount(local: Boolean, info: TvAccountInfo?, scale: Float, initial: FocusRequester,
    sidebar: FocusRequester, leave: () -> Unit, openDashboard: (() -> Unit)?,
    busy: Boolean = false, error: String? = null, retry: (() -> Unit)? = null) {
    val missing = !local && info == null && retry != null
    val identity = if (local) "Local mode" else info?.username ?: info?.email ?: "Managed on web"
    val devices = info?.devices?.let { "$it devices" } ?: "Managed on web"
    val signOut = if (openDashboard == null && !missing) initial else null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        TvSettingsHeading("Account", scale,
            if (openDashboard == null && !missing) listOfNotNull(identity, devices.takeIf { !local }).joinToString(" · ") else null, reserveSubtitle = true)
        if (!missing) {
            if (openDashboard != null) {
                TvSettingsRow(if (local) "Connection" else if (info?.email != null && info.username == null) "Email" else "Username", scale = scale,
                    value = identity, requester = initial, left = sidebar, activate = openDashboard)
                if (!local) TvSettingsRow("Devices", scale = scale, value = devices, left = sidebar, activate = openDashboard)
            }
        } else if (error == null) Text(if (busy) "Loading account information…" else "Account information is unavailable.",
            fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp, color = Palette.secondary)
        if (error != null && !local) Text(error, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp, color = Palette.danger)
        if (!local && retry != null) {
            TvAction(Modifier.fillMaxWidth().height(96.dp * scale).focusProperties { left = sidebar }, scale, ring = false,
                requester = if (missing) initial else null, enabled = !busy, activate = retry) {
                Text(if (busy) "Loading account…" else if (missing) "Retry account" else "Refresh account",
                    fontSize = (26 * scale).sp, fontWeight = FontWeight.SemiBold, color = it)
            }
            if (missing && openDashboard != null) TvSettingsRow("Open server dashboard", scale = scale, left = sidebar, activate = openDashboard)
        }
        Spacer(Modifier.height(24.dp * scale))
        TvSettingsRow(if (local) "Change connection" else "Sign out", scale = scale, requester = signOut, left = sidebar, activate = leave)
        if (local) Text("Your profiles and progress stay on this TV.", fontSize = (22 * scale).sp, color = Palette.secondary)
    }
}
