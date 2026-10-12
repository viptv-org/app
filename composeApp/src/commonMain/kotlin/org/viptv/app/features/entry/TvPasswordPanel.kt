package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

@Composable
internal fun TvPasswordPanel(origin: String, busy: Boolean, error: String?, scale: Float,
    connect: (String, String, String) -> Unit, forgot: () -> Unit) {
    var server by remember { mutableStateOf(origin.ifBlank { "https://viptv.syek.tech" }) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var editingPassword by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var serverDialog by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.offset(96.dp * scale, 96.dp * scale), verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            Text("Use a password", fontSize = (24 * scale).sp, lineHeight = (34.8f * scale).sp, color = androidx.compose.ui.graphics.Color(0xFF8F8D89), fontWeight = FontWeight.SemiBold)
            Text("Sign in with your password", fontSize = (52 * scale).sp, lineHeight = (60 * scale).sp, letterSpacing = (-1.04f * scale).sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.offset(96.dp * scale, 230.dp * scale).width(1130.dp * scale)) {
            TvEntryField("Email", email, "name@email.com", scale, !editingPassword) { editingPassword = false }
            Spacer(Modifier.height(16.dp * scale))
            TvEntryField("Password", if (visible) password else "•".repeat(password.length), "Password", scale, editingPassword, masked = !visible) { editingPassword = true }
            Spacer(Modifier.height(46.dp * scale))
            TvEntryKeyboard(scale) { value ->
                if (editingPassword) password = (password + value).take(512) else email = (email + value).take(320)
            }
        }
        Column(Modifier.offset(1290.dp * scale, 270.dp * scale).width(534.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            TvPasswordAction("Sign in", Lucide.ArrowRight, scale, !busy && email.isNotBlank() && password.isNotEmpty()) {
                connect(server, email, password); password = ""
            }
            TvPasswordAction(if (visible) "Hide password" else "Show password", if (visible) Lucide.EyeOff else Lucide.Eye, scale) { visible = !visible }
            TvPasswordAction("Forgot password", Lucide.Mail, scale, activate = forgot)
            Text("Typing with a remote is slow. Scanning a code with your phone is quicker.", Modifier.padding(top = 14.dp * scale),
                fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp, color = Palette.secondary)
            TvPasswordAction("Delete character", Lucide.Delete, scale) { if (editingPassword) password = password.dropLast(1) else email = email.dropLast(1) }
            TvPasswordAction("Use another server", Lucide.Server, scale) { serverDialog = true }
            error?.let { Text(it, color = Palette.danger, fontSize = (22 * scale).sp) }
            if (busy) Text("Signing in…", color = Palette.secondary, fontSize = (22 * scale).sp)
        }
    }
    if (serverDialog) AlertDialog(onDismissRequest = { serverDialog = false }, title = { Text("Server address") },
        text = { OutlinedTextField(server, { server = it }, singleLine = true, label = { Text("HTTPS server address") }) },
        confirmButton = { TextButton(onClick = { serverDialog = false }) { Text("Done") } })
}

@Composable
private fun TvPasswordAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, scale: Float, enabled: Boolean = true, activate: () -> Unit) {
    TvAction(Modifier.fillMaxWidth().height(72.dp * scale), scale, enabled = enabled, activate = activate) {
        Icon(icon, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
        Text(label, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold, color = it)
    }
}
