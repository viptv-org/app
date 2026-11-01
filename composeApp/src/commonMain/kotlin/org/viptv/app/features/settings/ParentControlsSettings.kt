package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.viptv.app.components.pageBottomClearance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.Button
import org.viptv.app.components.OutlinedButton
import org.viptv.app.components.TextButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParentControlsSettings(snapshot: ProfileManagement?, controls: Map<String, ParentProfileControls>,
    busy: Boolean, error: String?, notice: String?, saved: Long, load: () -> Unit, unlock: (String) -> Unit,
    pin: (String, String) -> Unit, policy: (String, KidsPolicy) -> Unit, leavePin: (String, String, String) -> Unit,
    titles: (String) -> Unit) {
    var pinMode by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ServerProfile?>(null) }
    var leavePinFor by remember { mutableStateOf<ServerProfile?>(null) }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(saved) { pinMode = null; editing = null; leavePinFor = null }
    org.viptv.app.components.TvFocusScroll(horizontal = false, contentPadding = androidx.compose.foundation.layout.PaddingValues(32.dp)) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp).pageBottomClearance(),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Parental controls", style = MaterialTheme.typography.headlineMedium)
        Text("The household PIN is required to leave a restricted profile or manage settings from it. Each profile has its own age limit.", color = Palette.secondary)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(it, color = Palette.danger)
            if (snapshot != null) OutlinedButton(load, enabled = !busy) { Text("Refresh controls") }
        }
        notice?.let { Text(it, color = Palette.secondary) }
        if (snapshot == null) {
            if (!busy) OutlinedButton(load) { Text("Retry parental controls") }
        } else if (snapshot.parent.restricted && !snapshot.parent.unlocked) {
            Text("Enter the parent PIN to manage this household.", color = Palette.secondary)
            Button({ pinMode = "unlock" }, enabled = !busy && snapshot.parent.pin_configured) { Text("Unlock parental controls") }
        } else {
            Text("Household PIN", style = MaterialTheme.typography.titleLarge)
            Text(if (snapshot.parent.pin_configured) "A parent PIN is set." else "Set a PIN before enabling kids restrictions.", color = Palette.secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ pinMode = "save" }, enabled = !busy) { Text(if (snapshot.parent.pin_configured) "Change parent PIN" else "Set parent PIN") }
                if (snapshot.parent.pin_configured) TextButton({ pinMode = "remove" }, enabled = !busy && snapshot.profiles.none { it.kids }) {
                    Text("Remove PIN", color = if (!busy && snapshot.profiles.none { it.kids }) Palette.danger else Palette.tertiary)
                }
            }
            if (snapshot.profiles.any { it.kids }) Text("Turn off kids restrictions on all profiles before removing the PIN.", color = Palette.secondary)
            HorizontalDivider(color = Palette.raised)
            Text("Profile restrictions", style = MaterialTheme.typography.titleLarge)
            snapshot.profiles.forEach { profile ->
                val current = controls[profile.id]
                Surface(shape = MaterialTheme.shapes.medium, color = Palette.surface) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                        Text(kidsSummary(profile, current?.policy), color = Palette.secondary)
                        if (snapshot.profilePinAvailable) Text(leavePinSummary(current?.leavePin), color = Palette.secondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ editing = profile }, enabled = !busy) { Text("Edit restrictions") }
                            if (snapshot.profilePinAvailable) TextButton({ leavePinFor = profile }, enabled = !busy) {
                                Text(if (current?.leavePin != false) "Change leave PIN" else "Set leave PIN")
                            }
                            if (profile.kids) TextButton({ titles(profile.id) }, enabled = !busy) { Text("Approved and blocked titles") }
                        }
                    }
                }
            }
        }
    } }
    pinMode?.let { mode -> ParentPinDialog(mode, snapshot?.parent?.pin_configured == true, busy, error,
        dismiss = { if (!busy) pinMode = null }, submit = { current, next -> if (mode == "unlock") unlock(current) else pin(current, next) }) }
    editing?.let { profile -> KidsPolicyDialog(profile, controls[profile.id]?.policy ?: fallbackPolicy(profile),
        snapshot?.parent?.pin_configured == true, busy, error,
        dismiss = { if (!busy) editing = null }, save = { policy(profile.id, it) }) }
    leavePinFor?.let { profile -> LeavePinDialog(profile.name, controls[profile.id]?.leavePin != false, busy, error,
        dismiss = { if (!busy) leavePinFor = null }, submit = { current, next -> leavePin(profile.id, current, next) }) }
}

internal fun fallbackPolicy(profile: ServerProfile) = KidsPolicy(profile.kids, profile.max_age ?: 12)

internal fun kidsSummary(profile: ServerProfile, policy: KidsPolicy?): String {
    if (!profile.kids) return "Kids restrictions off"
    val age = policy?.max_age ?: profile.max_age
    val movies = policy?.movie_rating?.let { "movies $it" }
    val series = policy?.series_rating?.let { "series $it" }
    return when {
        movies != null && series != null -> "Kids profile · $movies · $series"
        movies != null -> "Kids profile · $movies · other titles up to age ${age ?: "unavailable"}"
        series != null -> "Kids profile · $series · other titles up to age ${age ?: "unavailable"}"
        age != null -> "Kids profile · up to age $age"
        else -> "Kids profile · age limit unavailable"
    }
}

internal fun leavePinSummary(configured: Boolean?): String = when (configured) {
    true -> "Leave PIN set"
    false -> "No leave PIN"
    null -> "Leave PIN status unavailable"
}
