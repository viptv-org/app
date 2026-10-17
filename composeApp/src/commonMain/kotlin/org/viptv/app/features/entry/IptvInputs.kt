package org.viptv.app.features.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.connection.*
import org.viptv.app.theme.Palette

@Composable
internal fun IptvInputs(draft: IptvCredentials, mobile: Boolean, change: (IptvCredentials) -> Unit) {
    val height = if (mobile) 48.dp else 43.dp
    val fontSize = if (mobile) 16.sp else 15.sp
    var showPassword by remember { mutableStateOf(false) }
    IptvInputLabel(if (draft.mode == IptvMode.M3u) "M3U playlist link" else "Server address", mobile)
    Spacer(Modifier.height(6.dp))
    SignInInput(draft.address, { change(draft.copy(address = it)) },
        if (draft.mode == IptvMode.M3u) "https://provider.example/playlist.m3u" else "http://provider.example:8080",
        height = height, fontSize = fontSize)
    if (draft.mode == IptvMode.Xtream) {
        Spacer(Modifier.height(if (mobile) 14.dp else 12.dp))
        IptvInputLabel("Username", mobile)
        Spacer(Modifier.height(6.dp))
        SignInInput(draft.username, { change(draft.copy(username = it)) }, "Username", height = height, fontSize = fontSize)
        Spacer(Modifier.height(if (mobile) 14.dp else 12.dp))
        IptvInputLabel("Password", mobile)
        Spacer(Modifier.height(6.dp))
        SignInInput(draft.password, { change(draft.copy(password = it)) }, "Password", height = height, fontSize = fontSize, secret = true,
            transformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()) {
            Icon(if (showPassword) Lucide.EyeOff else Lucide.Eye, if (showPassword) "Hide password" else "Show password",
                Modifier.size(18.dp).clickable { showPassword = !showPassword }, tint = Palette.secondary)
        }
    }
}

@Composable
private fun IptvInputLabel(text: String, mobile: Boolean) {
    Text(text, color = Palette.secondary, fontSize = if (mobile) 13.sp else 12.sp,
        lineHeight = if (mobile) 18.85.sp else 17.4.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
}
