package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvTextField
import org.viptv.app.connection.SERVER_ADDRESS_ERROR
import org.viptv.app.connection.remoteOriginOrNull
import org.viptv.app.features.settings.TvDialogAction
import org.viptv.app.features.settings.TvDialogPanel
import org.viptv.app.features.settings.rememberTvDialogStops
import org.viptv.app.features.settings.tvDialogFocusWatch
import org.viptv.app.theme.Palette

@Composable
internal fun TvPasswordPanel(origin: String, busy: Boolean, error: String?, scale: Float,
    connect: (String, String, String) -> Unit, serverChanged: (String) -> Unit = {}) {
    var server by remember(origin) { mutableStateOf(origin) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    // Without a configured server the panel has nothing to sign in to, so the address asks first.
    var serverDialog by remember { mutableStateOf(origin.isBlank()) }
    val passwordField = remember { FocusRequester() }
    val signIn: () -> Unit = {
        if (canSignInWithPassword(server, email, password, busy)) {
            connect(server, email, password)
            password = ""
        }
    }
    Box(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.offset(96.dp * scale, 96.dp * scale), verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            Text("Use a password", fontSize = (24 * scale).sp, lineHeight = (34.8f * scale).sp, color = Palette.tertiary, fontWeight = FontWeight.SemiBold)
            Text("Sign in with your password", fontSize = (52 * scale).sp, lineHeight = (60 * scale).sp, letterSpacing = (-1.04f * scale).sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.offset(96.dp * scale, 230.dp * scale).width(1130.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            TvTextField(email, { email = it.take(320) }, scale, Modifier.fillMaxWidth(),
                label = "Username", placeholder = "username", enabled = !busy,
                imeAction = ImeAction.Next, submit = { passwordField.requestFocus() }, focusOnEnter = true)
            TvTextField(password, { password = it.take(512) }, scale, Modifier.fillMaxWidth(),
                label = "Password", placeholder = "Password", enabled = !busy, secret = !visible,
                imeAction = ImeAction.Done, submit = signIn, requester = passwordField)
        }
        Column(Modifier.offset(1290.dp * scale, 270.dp * scale).width(534.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            TvPasswordAction("Sign in", Lucide.ArrowRight, scale, canSignInWithPassword(server, email, password, busy), signIn)
            TvPasswordAction(if (visible) "Hide password" else "Show password", if (visible) Lucide.EyeOff else Lucide.Eye, scale, !busy) { visible = !visible }
            Text("Typing with a remote is slow. Scanning a code with your phone is quicker.", Modifier.padding(top = 14.dp * scale),
                fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp, color = Palette.secondary)
            TvPasswordAction("Use another server", Lucide.Server, scale) { serverError = null; serverDialog = true }
            error?.let { Text(it, color = Palette.danger, fontSize = (22 * scale).sp) }
            if (busy) Text("Signing in…", color = Palette.secondary, fontSize = (22 * scale).sp)
        }
    }
    if (serverDialog) TvServerAddressDialog(server, serverError, scale, dismiss = { serverDialog = false },
        change = { server = it; serverError = null },
        accept = {
            val selected = remoteOriginOrNull(server)
            if (selected == null) serverError = SERVER_ADDRESS_ERROR
            else { server = selected; serverChanged(selected); serverError = null; serverDialog = false }
        })
}

internal fun canSignInWithPassword(server: String, email: String, password: String, busy: Boolean) =
    !busy && remoteOriginOrNull(server) != null && email.isNotBlank() && password.isNotEmpty()

@Composable
private fun TvServerAddressDialog(address: String, error: String?, scale: Float, dismiss: () -> Unit,
    change: (String) -> Unit, accept: () -> Unit) {
    val stops = rememberTvDialogStops(3)
    val field = stops[0]
    val done = stops[1]
    val cancel = stops[2]
    TvDialogPanel(scale, "Server address", stops, field, dismiss, actions = {
        TvDialogAction(scale, "Connect", done, enabled = address.isNotBlank(), activate = accept)
        TvDialogAction(scale, "Cancel", cancel, activate = dismiss)
    }) {
        TvTextField(address, change, scale, Modifier.tvDialogFocusWatch(field).fillMaxWidth(),
            label = "HTTPS server address", invalid = error != null, requester = field.requester,
            focusOnEnter = true, onEscape = dismiss)
        error?.let {
            Spacer(Modifier.height((12 * scale).dp))
            Text(it, color = Palette.danger, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
        }
    }
}

@Composable
private fun TvPasswordAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, scale: Float, enabled: Boolean = true, activate: () -> Unit) {
    TvAction(Modifier.fillMaxWidth().heightIn(min = 72.dp * scale), scale, enabled = enabled, activate = activate) {
        Icon(icon, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
        Text(label, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold, color = it)
    }
}