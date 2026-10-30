package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.Button
import org.viptv.app.components.OutlinedButton
import org.viptv.app.components.TextButton
import org.viptv.app.connection.ProfileManagement
import org.viptv.app.theme.Palette

internal const val householdPinOwnerOnly = "Managed by the account owner."

@Composable
internal fun HouseholdPinSettings(snapshot: ProfileManagement?, busy: Boolean, error: String?, notice: String?, saved: Long,
    load: () -> Unit, unlock: (String) -> Unit, pin: (String, String) -> Unit) {
    var mode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(saved) { mode = null }
    val configured = snapshot?.parent?.pin_configured == true
    val kids = snapshot?.profiles.orEmpty().any { it.kids }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Household PIN", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        Text("Use a 4–8 digit PIN to protect leaving kids profiles. Turn off kids mode on all profiles before removing the PIN.",
            fontSize = 14.sp, lineHeight = 21.sp, color = Palette.secondary)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = Palette.danger) }
        notice?.let { Text(it, color = Palette.secondary) }
        when {
            snapshot == null -> if (!busy) OutlinedButton(load) { Text("Retry the household PIN") }
            snapshot.parent.restricted && !snapshot.parent.unlocked -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter the parent PIN to manage this household.", color = Palette.secondary)
                Button({ mode = "unlock" }, enabled = !busy && configured) { Text("Unlock parental controls") }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (configured) "A PIN is set." else "No PIN is set.", color = Palette.secondary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button({ mode = "save" }, enabled = !busy) { Text(if (configured) "Change PIN" else "Set PIN") }
                    if (configured) TextButton({ mode = "remove" }, enabled = !busy && !kids) {
                        Text("Remove PIN", color = if (!busy && !kids) Palette.danger else Palette.tertiary)
                    }
                }
                if (configured && kids) Text("Turn off kids restrictions on all profiles before removing the PIN.", color = Palette.secondary)
            }
        }
        if (error != null && snapshot != null) TextButton(load, enabled = !busy) { Text("Retry") }
    }
    mode?.let { current -> ParentPinDialog(current, configured, busy, error,
        dismiss = { if (!busy) mode = null },
        submit = { pin0, next -> if (current == "unlock") unlock(pin0) else pin(pin0, next) }) }
}
