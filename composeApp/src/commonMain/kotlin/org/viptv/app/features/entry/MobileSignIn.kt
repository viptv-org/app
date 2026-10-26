package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette

@Composable
internal fun MobileSignIn(origin: String, busy: Boolean, error: String?, local: (() -> Unit)?,
    connect: (String, String, String) -> Unit, pairing: org.viptv.app.connection.DevicePairingDisplay? = null,
    startPairing: ((String) -> Unit)? = null, cancelPairing: () -> Unit = {}, backChanged: ((() -> Unit)?) -> Unit = {},
) {
    var step by remember { mutableStateOf(SignInStep.Choice) }
    var server by remember(origin) { mutableStateOf(origin) }
    val back = { step = SignInStep.Choice }
    DisposableEffect(step) {
        backChanged(if (step == SignInStep.Choice) null else back)
        onDispose { backChanged(null) }
    }
    org.viptv.app.components.TvFocusScroll(horizontal = false) { Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SignInBackBar(back)
        when (step) {
            SignInStep.Choice -> {
                Spacer(Modifier.height(12.dp))
                Text("Sign in to VIPTV", color = Palette.text, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text("Pick whichever is easiest. They all reach the same account.", color = Palette.secondary, fontSize = 15.sp, lineHeight = 22.sp)
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Palette.danger, fontSize = 15.sp, lineHeight = 20.sp)
                }
                Spacer(Modifier.height(18.dp))
                MobileSignInChoice(SignInMethod.Browser, "Continue in browser", "Approve this sign-in through your server") { step = SignInStep.Browser }
                MobileSignInChoice(SignInMethod.Email, "Email me a code", "No password needed") { step = SignInStep.Email }
                MobileSignInChoice(SignInMethod.Password, "Use a password") { step = SignInStep.Password }
                MobileSignInChoice(SignInMethod.Device, "Use another device", "Scan a code and approve this sign-in") { step = SignInStep.Device }
                if (local != null) MobileSignInChoice(SignInMethod.Local, "Use without an account", "Everything stays on this device", local)
            }
            SignInStep.Password -> MobilePasswordPanel(server, busy, error, back, { step = SignInStep.Email }, connect, serverChanged = { server = it })
            SignInStep.Browser, SignInStep.Device -> if (startPairing != null)
                NativeDevicePairing(server, pairing, busy, error, true, step == SignInStep.Browser,
                    { server = it }, startPairing, cancelPairing, back)
                else MobileSignInUnavailable(step, back, { step = SignInStep.Password })
            else -> MobileSignInUnavailable(step, back, { step = SignInStep.Password })
        }
        Spacer(Modifier.height(24.dp))
    } }
}

@Composable
internal fun SignInBackBar(back: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterStart) {
        Icon(Lucide.ChevronLeft, "All sign-in options", Modifier.size(24.dp).clickable(onClick = back), tint = Palette.text)
    }
}

@Composable
private fun MobileSignInChoice(method: SignInMethod, title: String, subtitle: String? = null, activate: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 58.dp)
        .clip(RoundedCornerShape(12.dp)).background(Color(0xFF191919)).clickable(onClick = activate)
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        SignInMethodIcon(method, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.text, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = Palette.secondary, fontSize = 13.sp, lineHeight = 18.sp) }
        }
        Spacer(Modifier.width(12.dp))
        Icon(Lucide.ChevronRight, null, Modifier.size(16.dp), tint = Palette.secondary)
    }
}
