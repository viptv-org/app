package org.viptv.app.features.entry

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import org.viptv.app.connection.Profile
import org.viptv.app.components.TextButton

internal fun Profile?.requiresLocalPin(pinAvailable: Boolean) = pinAvailable && this?.locked == true && lockKind != "parent"

@Composable
internal fun ProfilePreferenceGuidance(language: Boolean, local: Boolean, editingCurrent: Boolean,
    profileName: String?, dismiss: () -> Unit, settings: ((String) -> Unit)?) {
    val section = if (language) "Subtitles & audio" else "Parental controls"
    val choose = language && !editingCurrent
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (language) "Audio & subtitles" else "Maturity rating") },
        text = { Text(when {
            choose -> "Select ${profileName ?: "this profile"} in Profiles, then open Settings → Subtitles & audio. Language settings apply only to the selected profile."
            language -> "Set default audio and subtitle languages in Settings → Subtitles & audio. Per-profile menu language is not supported."
            local -> "Maturity filtering is not available in local mode. Local profile PINs control profile entry, not title ratings."
            else -> "Manage maturity restrictions for household profiles in Settings → Parental controls. The server uses an account parent PIN, not a PIN on each profile."
        }) },
        confirmButton = { TextButton({ dismiss(); settings?.invoke(section) }) {
            Text(if (settings == null) "OK" else if (choose) "Choose profile" else "Open settings")
        } }, dismissButton = { if (settings != null) TextButton(dismiss) { Text("Cancel") } })
}
