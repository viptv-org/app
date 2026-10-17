package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.openAccountAuthorization
import org.viptv.app.theme.Palette

@Composable
internal fun ParentControlsNative(local: Boolean, address: String?, profiles: () -> Unit,
    first: FocusRequester? = null, sidebar: FocusRequester? = null, scale: Float = 1f) {
    var failed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().then(if (first == null) Modifier.padding(32.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
        Text("Parental controls", style = MaterialTheme.typography.headlineMedium,
            fontSize = (if (first != null) 40 * scale else 28f).sp)
        Text(if (local) "Manage each local profile’s PIN in Profiles." else
            "Manage the household PIN and profile restrictions through your server’s web dashboard.",
            color = Palette.secondary, fontSize = (if (first != null) 26 * scale else 16f).sp)
        if (!local && address != null) Text(address, color = Palette.text, fontSize = (if (first != null) 26 * scale else 16f).sp)
        val action = {
            if (local) profiles()
            else if (address != null) try { openAccountAuthorization(address); failed = false } catch (_: Exception) { failed = true }
            Unit
        }
        if (first != null) TvSettingsRow(if (local) "Manage profiles" else "Open server dashboard", scale = scale, requester = first, left = sidebar, activate = action)
        else Button(action, enabled = local || address != null) { Text(if (local) "Manage profiles" else "Open server dashboard") }
        if (failed) Text("Open the address above in a browser on your phone or computer.", color = Palette.secondary)
    }
}
