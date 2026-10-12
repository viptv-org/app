package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

internal data class TvAccountInfo(val email: String? = null, val devices: Int? = null)

@Composable
internal fun TvSettingsAccount(local: Boolean, info: TvAccountInfo?, scale: Float, initial: FocusRequester,
    sidebar: FocusRequester, leave: () -> Unit, unavailable: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        TvSettingsHeading("Account", scale, reserveSubtitle = true)
        TvSettingsRow(if (local) "Connection" else "Email", scale = scale, value = if (local) "Local mode" else info?.email ?: "Managed on web",
            requester = initial, left = sidebar, activate = unavailable)
        TvSettingsRow("Devices", scale = scale, value = info?.devices?.let { "$it devices" } ?: "Managed on web", left = sidebar, activate = unavailable)
        Spacer(Modifier.height(24.dp * scale))
        TvSettingsRow(if (local) "Change connection" else "Sign out", scale = scale, left = sidebar, activate = leave)
        if (local) Text("Your profiles and progress stay on this TV.", fontSize = (22 * scale).sp, color = Palette.secondary)
    }
}
