package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.components.Brand
import org.viptv.app.components.CredentialFields
import org.viptv.app.components.CredentialForm
import org.viptv.app.components.CredentialRules
import org.viptv.app.components.TextButton
import org.viptv.app.theme.Palette

@Composable
internal fun BrowserAccountForm(recovering: Boolean, busy: Boolean, error: String?, back: () -> Unit,
    register: (String, String, String) -> Unit, recover: (String, String, String) -> Unit) {
    var username by remember(recovering) { mutableStateOf("") }
    var name by remember(recovering) { mutableStateOf("") }
    var code by remember(recovering) { mutableStateOf("") }
    var password by remember(recovering) { mutableStateOf("") }
    var confirmation by remember(recovering) { mutableStateOf("") }
    val valid = CredentialRules.username(username).valid && CredentialRules.password(password).valid &&
        CredentialRules.confirmation(password, confirmation).valid &&
        if (recovering) CredentialRules.recoveryCode(code).valid else CredentialRules.displayName(name).valid
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Brand()
            Text(if (recovering) "Recover your account" else "Create your account", style = MaterialTheme.typography.headlineLarge)
            Text(if (recovering) "Use the recovery code you saved when creating your account. This changes your password and signs out existing devices."
                else "Create an account on this server. You’ll receive a recovery code to keep somewhere safe.", color = Palette.secondary)
            key(recovering) {
                CredentialForm(
                    fields = if (recovering) CredentialFields.recover(username, code, password, confirmation)
                        else CredentialFields.register(username, name, password, confirmation),
                    submitLabel = if (recovering) "Recover account" else "Create account",
                    valid = valid,
                    busy = busy,
                    error = error,
                    onChange = { index, value ->
                        if (recovering) when (index) {
                            0 -> username = value
                            1 -> code = value
                            2 -> password = value
                            else -> confirmation = value
                        } else when (index) {
                            0 -> username = value
                            1 -> name = value
                            2 -> password = value
                            else -> confirmation = value
                        }
                    },
                    onSubmit = { if (recovering) recover(username, code, password) else register(username, name, password) },
                    showProgress = true,
                )
            }
            TextButton(back, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
        }
    }
}