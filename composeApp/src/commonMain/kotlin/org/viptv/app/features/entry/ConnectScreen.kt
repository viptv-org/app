package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.components.Brand
import org.viptv.app.components.CredentialFields
import org.viptv.app.components.CredentialForm
import org.viptv.app.components.TextButton
import org.viptv.app.theme.Palette

@Composable
fun ConnectScreen(browser: Boolean, origin: String, busy: Boolean, error: String?, local: (() -> Unit)?, connect: (String, String, String) -> Unit, desktop: Boolean = false, phone: Boolean = false,
    television: Boolean = false, backChanged: ((() -> Unit)?) -> Unit = {},
    pairing: org.viptv.app.connection.DevicePairingDisplay? = null, startPairing: ((String) -> Unit)? = null,
    cancelPairing: () -> Unit = {},
    register: ((String, String, String) -> Unit)? = null, recover: ((String, String, String) -> Unit)? = null) {
    if (television) {
        TvSignIn(origin, busy, error, local, connect, backChanged, remotePairing = pairing, startPairing = startPairing, cancelPairing = cancelPairing)
        return
    }
    if (phone) {
        MobileSignIn(origin, busy, error, local, connect, pairing, startPairing, cancelPairing, backChanged)
        return
    }
    if (desktop || !browser && !phone) {
        DesktopSignIn(origin, busy, error, local, connect, pairing, startPairing, cancelPairing, backChanged)
        return
    }
    var accountMode by remember { mutableStateOf<String?>(null) }
    val formOpen = browser && accountMode != null && register != null && recover != null
    DisposableEffect(formOpen) {
        backChanged(if (formOpen) ({ accountMode = null }) else null)
        onDispose { backChanged(null) }
    }
    if (formOpen) {
        BrowserAccountForm(accountMode == "recover", busy, error, { accountMode = null }, register, recover)
        return
    }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val valid = username.isNotBlank() && password.isNotEmpty()
    val signIn = { if (!busy && valid) { connect(origin, username, password); password = "" } }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Brand()
            Spacer(Modifier.height(14.dp))
            Text("Welcome back", style = MaterialTheme.typography.headlineLarge)
            Text("Sign in to your VIPTV server.", color = Palette.secondary)
            CredentialForm(
                fields = CredentialFields.signIn(username, password),
                submitLabel = if (busy) "Signing in…" else "Sign in",
                valid = valid,
                busy = busy,
                error = error,
                onChange = { index, value -> if (index == 0) username = value else password = value },
                onSubmit = signIn,
                modifier = Modifier.fillMaxWidth(),
                showProgress = false,
                storePasswordOnSuccess = true,
            )
            if (browser && register != null && recover != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ accountMode = "register" }, enabled = !busy) { Text("Create account") }
                TextButton({ accountMode = "recover" }, enabled = !busy) { Text("Use recovery code") }
            }
            if (local != null) TextButton(onClick = local, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Use this device without an account") }
        }
    }
}
