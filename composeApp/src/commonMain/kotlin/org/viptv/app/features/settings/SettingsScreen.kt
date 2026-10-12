package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.Profile
import org.viptv.engine.wire.IntegrationConnection

@Composable
fun SettingsScreen(local: Boolean, addons: List<IntegrationConnection>, enabledAddonIds: Set<String>, busy: Boolean,
    error: String?, sessionNotice: String?, install: (String) -> Unit, toggle: (String, Boolean) -> Unit,
    remove: (String) -> Unit, profiles: () -> Unit, leave: () -> Unit, profile: Profile? = null, back: () -> Unit = {}) {
    var addonsOpen by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < 640.dp && !addonsOpen) {
            PhoneSettings(profile, addons.size, local, sessionNotice, profiles, { addonsOpen = true }, leave, back)
        } else {
            AddonSettings(local, addons, enabledAddonIds, busy, error, sessionNotice, install, toggle, remove,
                profiles, leave, if (addonsOpen) ({ addonsOpen = false }) else null)
        }
    }
}
