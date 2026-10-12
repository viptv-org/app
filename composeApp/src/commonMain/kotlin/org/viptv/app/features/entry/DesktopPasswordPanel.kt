package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import com.composables.icons.lucide.*

@Composable
internal fun DesktopPasswordPanel(
    initialOrigin: String,
    busy: Boolean,
    error: String?,
    back: () -> Unit,
    forgot: () -> Unit,
    connect: (String, String, String) -> Unit,
) {
    var server by remember { mutableStateOf(initialOrigin.ifBlank { "https://viptv.syek.tech" }) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var changeServer by remember { mutableStateOf(false) }
    SignInPanel(440.dp, verticalPadding = 24.dp) {
        Text("‹  All sign-in options", Modifier.height(20.dp).clickable(onClick = back),
            color = Palette.text, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text("Sign in with a password", color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Email", color = Palette.text, fontSize = 12.sp)
        Spacer(Modifier.height(5.dp))
        SignInInput(username, { username = it }, "name@email.com")
        Spacer(Modifier.height(12.dp))
        Text("Password", color = Palette.text, fontSize = 12.sp)
        Spacer(Modifier.height(5.dp))
        SignInInput(password, { password = it }, "Password",
            transformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()) {
            Icon(if (showPassword) Lucide.EyeOff else Lucide.Eye,
                if (showPassword) "Hide password" else "Show password",
                Modifier.size(18.dp).clickable { showPassword = !showPassword }, tint = Palette.secondary)
        }
        error?.let { Text(it, Modifier.padding(top = 9.dp), color = Palette.danger, fontSize = 12.sp) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { connect(server, username, password); password = "" },
            enabled = !busy && username.isNotBlank() && password.isNotBlank() && server.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
            Text(if (busy) "Signing in…" else "Sign in", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Forgot password?", Modifier.clickable(onClick = forgot), color = Palette.text, fontSize = 13.sp)
        }
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(if (changeServer) "Hide server address" else "Use another server",
                Modifier.clickable { changeServer = !changeServer }, color = Palette.secondary, fontSize = 11.sp)
        }
        if (changeServer) OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(),
            label = { Text("Server address") }, singleLine = true, shape = RoundedCornerShape(10.dp))
    }
}

@Composable
internal fun SignInInput(
    value: String,
    change: (String) -> Unit,
    hint: String,
    transformation: VisualTransformation = VisualTransformation.None,
    height: Dp = 44.dp,
    trailing: @Composable (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(value, change, Modifier.fillMaxWidth().height(height)
        .onFocusChanged { focused = it.isFocused }
        .background(Color(0xFF242426), shape)
        .border(if (focused) 2.dp else 1.dp, if (focused) Palette.text else Color(0xFF454548), shape),
        singleLine = true, visualTransformation = transformation,
        textStyle = TextStyle(color = Palette.text, fontSize = 14.sp),
        cursorBrush = SolidColor(Palette.text), decorationBox = { inner ->
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, color = Palette.secondary, fontSize = 14.sp)
                    inner()
                }
                trailing?.invoke()
            }
        })
}
