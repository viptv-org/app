package org.viptv.app.features.settings

import org.viptv.app.components.AppTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import org.viptv.app.components.pageBottomClearance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.Button
import org.viptv.app.components.TextButton
import org.viptv.app.connection.AccountIdentity
import org.viptv.app.connection.validParentPin
import org.viptv.app.theme.Palette

@Composable
internal fun DeleteAccountScreen(identity: AccountIdentity?, pinConfigured: Boolean?, busy: Boolean, error: String?,
    load: () -> Unit, back: () -> Unit, delete: (String, String, String?) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { load() }
    val confirmed = identity != null && username == identity.username
    val pinOk = when (pinConfigured) {
        true -> validParentPin(pin)
        null -> pin.isEmpty() || validParentPin(pin)
        false -> true
    }
    val ready = confirmed && password.isNotEmpty() && pinOk && !busy
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp).pageBottomClearance(),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Delete account", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Palette.danger)
        Text(if (identity != null) "This deletes ${identity.name}’s account, its profiles, their history, My List, add-ons and paired devices. It cannot be undone."
            else "This deletes the account, its profiles, their history, My List, add-ons and paired devices. It cannot be undone.",
            fontSize = 14.sp, lineHeight = 21.sp, color = Palette.secondary)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = Palette.danger) }
        when {
            identity == null -> {
                Text(if (busy) "Loading your account…" else "Could not load your account.", color = Palette.secondary)
                TextButton(load, enabled = !busy) { Text("Retry the account") }
            }
            else -> {
                AppTextField(username, { username = it }, Modifier.fillMaxWidth(), enabled = !busy,
                    label = "Type ${identity.username} to confirm")
                AppTextField(password, { password = it }, Modifier.fillMaxWidth(), enabled = !busy,
                    label = "Password", visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (pinConfigured != false) AppTextField(pin, { pin = it.filter { c -> c in '0'..'9' }.take(8) },
                    Modifier.fillMaxWidth(), enabled = !busy, label = "Household PIN",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button({ if (ready) delete(username, password, if (pin.isEmpty()) null else pin) },
                        Modifier.widthIn(min = 240.dp).height(40.dp), enabled = ready, shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.danger.copy(alpha = .12f),
                            contentColor = Palette.danger)) {
                        Text("Delete account", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(back, enabled = !busy) { Text("Cancel") }
                }
            }
        }
    }
}
