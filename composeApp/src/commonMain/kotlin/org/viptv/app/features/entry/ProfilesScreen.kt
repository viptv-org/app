package org.viptv.app.features.entry

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.viptv.app.components.AppTextField
import org.viptv.app.components.TextButton
import org.viptv.app.connection.Profile

@Composable
fun ProfilesScreen(
    profiles: List<Profile>, busy: Boolean, error: String?, local: Boolean, managing: Boolean, compact: Boolean, desktop: Boolean,
    select: (String) -> Unit, edit: (String) -> Unit, manage: () -> Unit, done: () -> Unit,
    create: (String) -> Unit, television: Boolean = false, back: (() -> Unit)? = null,
) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    if (television) TvProfiles(profiles, busy, error, local, managing, select, edit, manage, done) { adding = true }
    else if (compact) MobileProfiles(profiles, busy, error, local, managing, select, edit, manage, done, back = back) { adding = true }
    else if (desktop) DesktopProfiles(profiles, busy, error, local, managing, select, edit, manage, done) { adding = true }
    else WideProfiles(profiles, busy, error, local, managing, select, edit, manage, done) { adding = true }
    if (adding) {
        val nameFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { nameFocus.requestFocus() } }
        AlertDialog(onDismissRequest = { adding = false }, title = { Text("Add a profile") },
            text = { AppTextField(name, { name = it }, label = "Name", modifier = Modifier.focusRequester(nameFocus), onEscape = { adding = false }) },
            confirmButton = { TextButton(onClick = { create(name); adding = false; name = "" }, enabled = name.isNotBlank() && !busy) { Text("Add") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } })
    }
}
