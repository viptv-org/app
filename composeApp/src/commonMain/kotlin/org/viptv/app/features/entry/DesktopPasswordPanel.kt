package org.viptv.app.features.entry

import org.viptv.app.components.AppTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.app.connection.openAccountAuthorization
import org.viptv.app.connection.SERVER_ADDRESS_ERROR
import org.viptv.app.connection.remoteOriginOrNull
import com.composables.icons.lucide.*
import org.viptv.app.components.AccessibleIconButton
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.BackControl
import org.viptv.app.components.Button

@Composable
internal fun DesktopPasswordPanel(
    initialOrigin: String,
    busy: Boolean,
    error: String?,
    back: () -> Unit,
    connect: (String, String, String) -> Unit,
    serverChanged: (String) -> Unit = {},
    forgot: (String) -> Unit = { address -> if (address.isNotBlank()) runCatching { openAccountAuthorization(address) } },
) {
    var server by remember { mutableStateOf(initialOrigin) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var changeServer by remember { mutableStateOf(initialOrigin.isBlank()) }
    var serverError by remember { mutableStateOf<String?>(null) }
    SignInPanel(440.dp, verticalPadding = 24.dp) {
        BackControl(back, label = "All sign-in options", desktop = true)
        Spacer(Modifier.height(8.dp))
        Text("Sign in with a password", color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Username", color = Palette.text, fontSize = 12.sp)
        Spacer(Modifier.height(5.dp))
        SignInInput(username, { username = it }, "username")
        Spacer(Modifier.height(12.dp))
        Text("Password", color = Palette.text, fontSize = 12.sp)
        Spacer(Modifier.height(5.dp))
        SignInInput(password, { password = it }, "Password", secret = true,
            transformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()) {
            AccessibleIconButton(if (showPassword) Lucide.EyeOff else Lucide.Eye,
                if (showPassword) "Hide password" else "Show password",
                { showPassword = !showPassword }, iconSize = 18.dp, tint = Palette.secondary, wash = null)
        }
        error?.let { Text(it, Modifier.padding(top = 9.dp), color = Palette.danger, fontSize = 12.sp) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            val selected = remoteOriginOrNull(server)
            if (selected == null) { serverError = SERVER_ADDRESS_ERROR; changeServer = true }
            else { connect(selected, username, password); password = "" }
        },
            enabled = !busy && username.isNotBlank() && password.isNotBlank() && server.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
            Text(if (busy) "Signing in…" else "Sign in", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        if (server.isBlank()) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("Forgot password?", color = Palette.text.copy(alpha = 0.4f), fontSize = 13.sp)
                Text("Enter your server address first.", Modifier.padding(top = 2.dp), color = Palette.secondary, fontSize = 11.sp)
            }
        } else Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Forgot password?", Modifier.pointerClickable(onClick = {
                val selected = remoteOriginOrNull(server)
                if (selected == null) { serverError = SERVER_ADDRESS_ERROR; changeServer = true } else forgot(selected)
            }), color = Palette.text, fontSize = 13.sp)
        }
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(if (changeServer) "Hide server address" else "Use another server",
                Modifier.pointerClickable { changeServer = !changeServer; serverError = null }, color = Palette.secondary, fontSize = 11.sp)
        }
        if (changeServer) AppTextField(server, { server = it; serverChanged(it); serverError = null }, Modifier.fillMaxWidth(),
            label = "HTTPS server address", isError = serverError != null, supportingText = { serverError?.let { Text(it) } },
            shape = RoundedCornerShape(10.dp))
    }
}
