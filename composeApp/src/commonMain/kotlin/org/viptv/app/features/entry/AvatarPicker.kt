package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.Profile

private val avatarChoices = listOf(
    "vynxc" to "Portrait",
    "zayne" to "Planet",
    "initial-pink" to "Pink",
    "initial-blue" to "Blue",
)

@Composable
internal fun AvatarPicker(profile: Profile?, busy: Boolean, dismiss: () -> Unit, choose: (String, String) -> Unit) {
    var pin by remember(profile?.id) { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Change avatar") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            avatarChoices.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    pair.forEach { (id, label) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(onClick = { choose(id, pin) }, enabled = !busy && (profile?.locked != true || pin.length in 4..8)) {
                                ProfileAvatar(profile?.copy(avatarId = id), 72.dp, 16.dp)
                            }
                            Text(label)
                        }
                    }
                }
            }
            if (profile?.locked == true) OutlinedTextField(pin,
                { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                label = { Text("Current PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
