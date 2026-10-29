package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.app.components.TextButton

@Composable
internal fun MobileSignInUnavailable(step: SignInStep, back: () -> Unit, password: () -> Unit) {
    val title = when (step) {
        SignInStep.Browser -> "Finish signing in in your browser"
        SignInStep.Email -> "Sign in"
        SignInStep.Device -> "Use another device"
        else -> "Sign in with your IPTV provider"
    }
    val message = when (step) {
        SignInStep.Browser -> "Browser sign-in is not available on this connection yet."
        SignInStep.Email -> "Email sign-in and password recovery are not available on this connection yet."
        SignInStep.Device -> "Device approval is not available on this connection yet."
        else -> "Provider connection is not available yet."
    }
    Spacer(Modifier.height(12.dp))
    Text(title, color = Palette.text, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(18.dp))
    Text(message, color = Palette.secondary, fontSize = 15.sp, lineHeight = 22.sp)
    TextButton(onClick = password, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("Use a password instead", color = Palette.text)
    }
    TextButton(onClick = back, modifier = Modifier.fillMaxWidth()) {
        Text("All sign-in options", color = Palette.secondary)
    }
}
