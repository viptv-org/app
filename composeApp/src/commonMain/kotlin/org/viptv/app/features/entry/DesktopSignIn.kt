package org.viptv.app.features.entry

import com.composables.icons.lucide.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import org.viptv.app.resources.Res
import org.viptv.app.resources.bricolage
import org.viptv.app.theme.Palette

internal enum class SignInStep { Choice, Password, Browser, Email, Device }

@Composable
internal fun DesktopSignIn(
    origin: String,
    busy: Boolean,
    error: String?,
    local: (() -> Unit)?,
    connect: (String, String, String) -> Unit,
    pairing: org.viptv.app.connection.DevicePairingDisplay? = null,
    startPairing: ((String) -> Unit)? = null,
    cancelPairing: () -> Unit = {},
    backChanged: ((() -> Unit)?) -> Unit = {},
) {
    var step by remember { mutableStateOf(SignInStep.Choice) }
    var server by remember(origin) { mutableStateOf(origin) }
    val back = { step = SignInStep.Choice }
    DisposableEffect(step) {
        backChanged(if (step == SignInStep.Choice) null else back)
        onDispose { backChanged(null) }
    }
    Box(Modifier.fillMaxSize()) {
        DesktopSignInBackdrop()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (step) {
                SignInStep.Choice -> SignInPanel(480.dp) {
                    SignInBrand()
                    Spacer(Modifier.height(16.dp))
                    Text("Sign in to VIPTV", color = Palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(9.dp))
                    Text("Pick whichever is easiest. They all reach the same account.", color = Palette.secondary, fontSize = 14.sp)
                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Palette.danger, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    SignInChoice(SignInMethod.Browser, "Continue in browser", "Approve this sign-in through your server") { step = SignInStep.Browser }
                    SignInChoice(SignInMethod.Email, "Email me a code", "No password needed") { step = SignInStep.Email }
                    SignInChoice(SignInMethod.Password, "Use a password") { step = SignInStep.Password }
                    SignInChoice(SignInMethod.Device, "Use another device", "Scan a code and approve this sign-in") { step = SignInStep.Device }
                    if (local != null) SignInChoice(SignInMethod.Local, "Use without an account", "Everything stays on this device", last = true, activate = local)
                }
                SignInStep.Password -> DesktopPasswordPanel(server, busy, error, back,
                    { step = SignInStep.Email }, connect, serverChanged = { server = it })
                SignInStep.Browser, SignInStep.Device -> if (startPairing != null)
                    NativeDevicePairing(server, pairing, busy, error, false, step == SignInStep.Browser,
                        { server = it }, startPairing, cancelPairing, back)
                    else SignInPanel(480.dp) {
                        Text("Device sign-in requires a server connection.", color = Palette.secondary)
                        androidx.compose.material3.TextButton(back) { Text("All sign-in options") }
                    }
                SignInStep.Email -> DesktopEmailPanel(back)
            }
        }
    }
}

@Composable
internal fun SignInPanel(width: Dp, verticalPadding: Dp = 32.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.width(width).offset(y = (-20).dp), shape = RoundedCornerShape(20.dp),
        color = Color(0xFF151518), border = BorderStroke(1.dp, Color(0xFF2A2A2E))) {
        Column(Modifier.padding(horizontal = 32.dp, vertical = verticalPadding), content = content)
    }
}

@Composable
private fun SignInBrand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(28.dp).background(Palette.accent, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
            Text("V", color = Palette.background, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        Text("VIPTV", color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily(Font(Res.font.bricolage, FontWeight.ExtraBold)))
    }
}

@Composable
private fun SignInChoice(icon: SignInMethod, title: String, subtitle: String? = null, last: Boolean = false, activate: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = if (last) 0.dp else 8.dp).height(52.dp)
        .clip(RoundedCornerShape(12.dp)).background(Color(0xFF222224)).clickable(onClick = activate)
        .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        SignInMethodIcon(icon, Modifier.width(30.dp).height(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
            subtitle?.let { Text(it, color = Palette.secondary, fontSize = 12.sp, lineHeight = 16.sp) }
        }
        androidx.compose.material3.Icon(Lucide.ChevronRight, null, Modifier.size(17.dp), tint = Palette.secondary)
    }
}
