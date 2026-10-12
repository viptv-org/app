package org.viptv.app.features.entry

import androidx.compose.foundation.clickable
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

@Composable
internal fun MobilePasswordPanel(origin: String, busy: Boolean, error: String?, back: () -> Unit,
    forgot: () -> Unit, connect: (String, String, String) -> Unit) {
    var server by remember { mutableStateOf(origin) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var editServer by remember { mutableStateOf(origin.isBlank()) }
    Row(Modifier.height(28.dp).clickable(onClick = back), verticalAlignment = Alignment.CenterVertically) {
        Icon(Lucide.ChevronLeft, null, Modifier.size(16.dp), tint = Palette.secondary)
        Spacer(Modifier.width(5.dp))
        Text("All sign-in options", color = Palette.secondary, fontSize = 14.sp)
    }
    Spacer(Modifier.height(16.dp))
    Text("Sign in with a password", color = Palette.text, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(18.dp))
    Text("Email", color = Palette.secondary, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    SignInInput(email, { email = it }, "name@email.com", height = 48.dp)
    Spacer(Modifier.height(18.dp))
    Text("Password", color = Palette.secondary, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    SignInInput(password, { password = it }, "Password", height = 48.dp,
        transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()) {
        Icon(if (visible) Lucide.EyeOff else Lucide.Eye, if (visible) "Hide password" else "Show password",
            Modifier.size(20.dp).clickable { visible = !visible }, tint = Palette.secondary)
    }
    error?.let { Text(it, Modifier.padding(top = 12.dp), color = Palette.danger, fontSize = 13.sp) }
    Spacer(Modifier.height(16.dp))
    Button(onClick = { connect(server, email, password); password = "" },
        enabled = !busy && server.isNotBlank() && email.isNotBlank() && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
        Text(if (busy) "Signing in…" else "Sign in", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
    TextButton(onClick = forgot, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Forgot password?", color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    TextButton(onClick = { editServer = !editServer }, modifier = Modifier.fillMaxWidth()) {
        Text("Use another server", color = Palette.secondary, fontSize = 12.sp)
    }
    if (editServer) OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(),
        label = { Text("Server address") }, singleLine = true, shape = RoundedCornerShape(12.dp))
}
