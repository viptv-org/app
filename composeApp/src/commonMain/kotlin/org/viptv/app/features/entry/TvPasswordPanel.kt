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
import org.viptv.app.theme.Palette
import org.viptv.app.components.TextButton

@Composable
internal fun TvPasswordPanel(origin: String, busy: Boolean, error: String?, scale: Float,
    connect: (String, String, String) -> Unit, serverChanged: (String) -> Unit = {}) {
    var server by remember { mutableStateOf(origin.ifBlank { "https://your-server.example" }) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var serverDialog by remember { mutableStateOf(false) }
    val passwordField = remember { FocusRequester() }
    val signIn: () -> Unit = {
        if (!busy && email.isNotBlank() && password.isNotEmpty()) {
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
            TvPasswordAction("Sign in", Lucide.ArrowRight, scale, !busy && email.isNotBlank() && password.isNotEmpty(), signIn)
            TvPasswordAction(if (visible) "Hide password" else "Show password", if (visible) Lucide.EyeOff else Lucide.Eye, scale, !busy) { visible = !visible }
            Text("Typing with a remote is slow. Scanning a code with your phone is quicker.", Modifier.padding(top = 14.dp * scale),
                fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp, color = Palette.secondary)
            TvPasswordAction("Use another server", Lucide.Server, scale) { serverDialog = true }
            error?.let { Text(it, color = Palette.danger, fontSize = (22 * scale).sp) }
            if (busy) Text("Signing in…", color = Palette.secondary, fontSize = (22 * scale).sp)
        }
    }
    if (serverDialog) AlertDialog(onDismissRequest = { serverDialog = false }, title = { Text("Server address") },
        text = { TvTextField(server, { server = it }, scale, Modifier.fillMaxWidth(), label = "HTTPS server address",
            placeholder = "https://your-server.example", focusOnEnter = true, onEscape = { serverDialog = false }) },
        confirmButton = { TextButton(onClick = { serverChanged(server); serverDialog = false }) { Text("Done") } })
}

@Composable
private fun TvPasswordAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, scale: Float, enabled: Boolean = true, activate: () -> Unit) {
    TvAction(Modifier.fillMaxWidth().heightIn(min = 72.dp * scale), scale, enabled = enabled, activate = activate) {
        Icon(icon, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
        Text(label, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold, color = it)
    }
}