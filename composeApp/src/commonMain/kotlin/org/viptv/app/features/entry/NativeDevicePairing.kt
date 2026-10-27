package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.DevicePairingDisplay
import org.viptv.app.theme.Palette
import org.viptv.app.components.TextButton

@Composable
internal fun NativeDevicePairing(server: String, display: DevicePairingDisplay?, busy: Boolean, error: String?,
    mobile: Boolean, browser: Boolean, serverChanged: (String) -> Unit, start: (String) -> Unit,
    cancel: () -> Unit, back: () -> Unit, autoOpen: Boolean = true) {
    DevicePairingRequest(server, serverChanged, start, cancel, back) { refresh, changeServer ->
        if (mobile) {
            if (browser) {
                Spacer(Modifier.height(64.dp))
                PairingBrowser(display, busy, error, true, back, refresh, changeServer, autoOpen)
            } else DevicePairingPanel(display, busy, error, true, back, refresh, changeServer)
        } else SignInPanel(if (browser) 440.dp else 480.dp) {
            if (browser) PairingBrowser(display, busy, error, false, back, refresh, changeServer, autoOpen)
            else DevicePairingPanel(display, busy, error, false, back, refresh, changeServer)
        }
    }
}

@Composable
internal fun DevicePairingPanel(display: DevicePairingDisplay?, busy: Boolean, error: String?, mobile: Boolean,
    back: () -> Unit, refresh: () -> Unit, changeServer: () -> Unit) {
    PairingBackLink(back, mobile)
    Spacer(Modifier.height(if (mobile) 16.dp else 12.dp))
    Text("Use another device", color = Palette.text, fontSize = if (mobile) 26.sp else 23.sp,
        fontWeight = FontWeight.Bold, letterSpacing = if (mobile) (-.52).sp else (-.46).sp,
        lineHeight = if (mobile) 29.9.sp else 26.45.sp)
    Spacer(Modifier.height(if (mobile) 18.dp else 12.dp))
    val address = display?.verificationUri?.removePrefix("https://") ?: "your server’s device sign-in page"
    Text("Scan this code with another device, or go to $address and enter the code. Sign in there and approve this device.",
        color = Palette.secondary, fontSize = if (mobile) 15.sp else 14.sp, letterSpacing = 0.sp,
        lineHeight = if (mobile) 21.75.sp else 20.3.sp)
    Spacer(Modifier.height(if (mobile) 18.dp else 16.dp))
    PairingCode(display, busy, mobile)
    Spacer(Modifier.height(if (mobile) 18.dp else 12.dp))
    PairingStatus(display, busy, error)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (display != null || !busy) TextButton(refresh) { Text("New code", fontSize = 12.sp) }
        TextButton(changeServer) { Text("Use another server", color = Palette.secondary, fontSize = 12.sp) }
    }
}
