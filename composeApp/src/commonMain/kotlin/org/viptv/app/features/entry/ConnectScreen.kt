package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.viptv.app.components.Brand
import org.viptv.app.theme.Palette

// Dedicated phone/TV/desktop layouts precede the wide connection fallback.
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
    // Browser Back and Escape leave the create-account and recovery forms for sign-in, as their own Back does.
    val formOpen = browser && accountMode != null && register != null && recover != null
    DisposableEffect(formOpen) {
        backChanged(if (formOpen) ({ accountMode = null }) else null)
        onDispose { backChanged(null) }
    }
    if (formOpen) {
        BrowserAccountForm(accountMode == "recover", busy, error, { accountMode = null }, register, recover)
        return
    }
    var server by remember { mutableStateOf(origin) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Brand()
            Spacer(Modifier.height(14.dp))
            Text("Welcome back", style = MaterialTheme.typography.headlineLarge)
            Text("Sign in to your VIPTV server.", color = Palette.secondary)
            if (!browser) OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(), label = { Text("Server address") }, placeholder = { Text("https://your-server") }, singleLine = true)
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            error?.let { Text(it, color = Palette.danger) }
            Button(onClick = { connect(server, username, password); password = "" }, enabled = !busy && server.isNotBlank() && username.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                Text(if (busy) "Signing in…" else "Sign in")
            }
            if (browser && register != null && recover != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ accountMode = "register" }, enabled = !busy) { Text("Create account") }
                TextButton({ accountMode = "recover" }, enabled = !busy) { Text("Use recovery code") }
            }
            if (local != null) TextButton(onClick = local, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Use this device without an account") }
        }
    }
}
