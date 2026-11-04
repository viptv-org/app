package org.viptv.app.features.entry

import org.viptv.app.components.AppTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.AccessibleIconButton
import org.viptv.app.components.BackControl
import org.viptv.app.components.Button
import org.viptv.app.components.TextButton

@Composable
internal fun MobilePasswordPanel(origin: String, busy: Boolean, error: String?, back: () -> Unit,
    connect: (String, String, String) -> Unit, serverChanged: (String) -> Unit = {}) {
    var server by remember { mutableStateOf(origin) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var editServer by remember { mutableStateOf(origin.isBlank()) }
    BackControl(back, label = "All sign-in options")
    Spacer(Modifier.height(16.dp))
    Text("Sign in with a password", color = Palette.text, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(18.dp))
    Text("Username", color = Palette.secondary, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    SignInInput(email, { email = it }, "username", height = 48.dp)
    Spacer(Modifier.height(18.dp))
    Text("Password", color = Palette.secondary, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    SignInInput(password, { password = it }, "Password", height = 48.dp, secret = true,
        transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()) {
        AccessibleIconButton(if (visible) Lucide.EyeOff else Lucide.Eye,
            if (visible) "Hide password" else "Show password",
            { visible = !visible }, iconSize = 20.dp, tint = Palette.secondary, wash = null)
    }
    error?.let { Text(it, Modifier.padding(top = 12.dp), color = Palette.danger, fontSize = 13.sp) }
    Spacer(Modifier.height(16.dp))
    Button(onClick = { connect(server, email, password); password = "" },
        enabled = !busy && server.isNotBlank() && email.isNotBlank() && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
        Text(if (busy) "Signing in…" else "Sign in", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
    TextButton(onClick = { editServer = !editServer }, modifier = Modifier.fillMaxWidth()) {
        Text("Use another server", color = Palette.secondary, fontSize = 12.sp)
    }
    if (editServer) AppTextField(server, { server = it; serverChanged(it) }, Modifier.fillMaxWidth(),
        label = "Server address", shape = RoundedCornerShape(12.dp))
}
