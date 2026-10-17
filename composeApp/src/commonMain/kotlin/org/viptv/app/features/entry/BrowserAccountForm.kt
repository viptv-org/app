package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.viptv.app.components.Brand
import org.viptv.app.connection.validAccountUsername
import org.viptv.app.connection.validNewPassword
import org.viptv.app.theme.Palette

@Composable
internal fun BrowserAccountForm(recovering: Boolean, busy: Boolean, error: String?, back: () -> Unit,
    register: (String, String, String) -> Unit, recover: (String, String, String) -> Unit) {
    var username by remember(recovering) { mutableStateOf("") }
    var name by remember(recovering) { mutableStateOf("") }
    var code by remember(recovering) { mutableStateOf("") }
    var password by remember(recovering) { mutableStateOf("") }
    var confirmation by remember(recovering) { mutableStateOf("") }
    val valid = validAccountUsername(username) && validNewPassword(password) && password == confirmation &&
        if (recovering) code.trim().isNotEmpty() && code.length <= 256 else name.trim().isNotEmpty() && name.trim().encodeToByteArray().size <= 80
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Brand()
            Text(if (recovering) "Recover your account" else "Create your account", style = MaterialTheme.typography.headlineLarge)
            Text(if (recovering) "Use the recovery code you saved when creating your account. This changes your password and signs out existing devices."
                else "Create an account on this server. You’ll receive a recovery code to keep somewhere safe.", color = Palette.secondary)
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Username") }, singleLine = true)
            if (recovering) OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), enabled = !busy,
                label = { Text("Recovery code") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            else OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Display name") }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), enabled = !busy,
                label = { Text(if (recovering) "New password" else "Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            OutlinedTextField(confirmation, { confirmation = it }, Modifier.fillMaxWidth(), enabled = !busy,
                label = { Text("Confirm password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                isError = confirmation.isNotEmpty() && confirmation != password,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            if (confirmation.isNotEmpty() && confirmation != password) Text("Passwords do not match.", color = Palette.danger)
            error?.let { Text(it, color = Palette.danger) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button({ if (valid && !busy) {
                if (recovering) recover(username, code, password) else register(username, name, password)
            } }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (recovering) "Recover account" else "Create account")
            }
            TextButton(back, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
        }
    }
}
