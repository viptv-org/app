package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

internal data class TvAccountInfo(val name: String, val username: String, val role: String, val devices: Int? = null)

internal const val tvAccountManagedElsewhere = "Manage this from a signed-in phone, computer or the web dashboard."

@Composable
internal fun TvSettingsAccount(local: Boolean, info: TvAccountInfo?, scale: Float, initial: FocusRequester,
    sidebar: FocusRequester, leave: () -> Unit, openDashboard: (() -> Unit)?,
    busy: Boolean = false, error: String? = null, retry: (() -> Unit)? = null) {
    val missing = !local && info == null && retry != null
    val devicesFirst = !local && info != null && openDashboard != null
    val retryFirst = !local && info == null && missing
    val signOutFirst = !devicesFirst && !retryFirst
    Column(verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        TvSettingsHeading("Account", scale)
        if (local) {
            Text("Local mode. Your profiles and progress stay on this TV.",
                fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp, color = Palette.secondary)
        } else if (info != null) {
            TvAccountValue("Name", info.name, scale)
            TvAccountValue("Username", "@${info.username}", scale)
            TvAccountValue("Role", if (info.role == "owner") "Owner" else "Member", scale)
            TvSettingsDisabledRow("Household PIN", tvAccountManagedElsewhere, scale)
            if (openDashboard != null) TvSettingsRow("Devices & sessions", scale = scale,
                value = info.devices?.let { "$it devices" } ?: "Manage on the web",
                requester = if (devicesFirst) initial else null, left = sidebar, activate = openDashboard)
        } else {
            Text(if (busy) "Loading account information…" else "Account information is unavailable.",
                fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp, color = Palette.secondary)
        }
        if (error != null) Text(error, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp, color = Palette.danger)
        if (!local && retry != null && (missing || error != null)) {
            TvAction(Modifier.fillMaxWidth().height(96.dp * scale).focusProperties { left = sidebar }, scale, ring = false,
                requester = if (retryFirst) initial else null, enabled = !busy, activate = retry) {
                Text(if (busy) "Loading account…" else "Retry account",
                    fontSize = (26 * scale).sp, fontWeight = FontWeight.SemiBold, color = it)
            }
            if (missing && openDashboard != null) TvSettingsRow("Open server dashboard", scale = scale, left = sidebar, activate = openDashboard)
        }
        Spacer(Modifier.height(24.dp * scale))
        TvSettingsRow(if (local) "Change connection" else "Sign out", scale = scale,
            requester = if (signOutFirst) initial else null, left = sidebar, activate = leave,
            labelColor = if (local) null else Palette.danger)
    }
}

@Composable
private fun TvAccountValue(label: String, value: String, scale: Float) {
    Row(Modifier.fillMaxWidth().height(96.dp * scale).padding(horizontal = 28.dp * scale),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.text, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = Palette.secondary, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.Medium)
    }
}