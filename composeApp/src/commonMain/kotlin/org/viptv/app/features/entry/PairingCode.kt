package org.viptv.app.features.entry

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.viptv.app.connection.DevicePairingDisplay
import org.viptv.app.theme.Palette

@Composable
internal fun PairingCode(display: DevicePairingDisplay?, busy: Boolean, mobile: Boolean) {
    val size = if (mobile) 150.dp else 135.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (mobile) 20.dp else 18.dp)) {
        Box(Modifier.size(size).background(Color.White, RoundedCornerShape(if (mobile) 12.dp else 10.dp)), contentAlignment = Alignment.Center) {
            if (display != null) Image(rememberQrCodePainter(display.qrUri) { scale = .84f }, "Sign-in QR code", Modifier.size(size - 12.dp))
            else if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = Palette.background, strokeWidth = 3.dp)
            else Text("No code", color = Palette.background, fontSize = 15.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Code", color = Palette.secondary, fontSize = 12.sp)
            val fontSize = if ((display?.userCode?.length ?: 0) > 12) 20 else if (mobile) 30 else 27
            Text(display?.userCode ?: if (busy) "Creating…" else "—", color = Palette.text,
                fontSize = fontSize.sp, fontFamily = FontFamily.Monospace, letterSpacing = (fontSize * .06f).sp,
                lineHeight = (fontSize * 1.2f).sp, fontWeight = FontWeight.Bold)
            display?.let {
                Text("Expires in ${it.remainingSeconds / 60}:${(it.remainingSeconds % 60).toString().padStart(2, '0')}",
                    color = Palette.secondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
internal fun PairingStatus(display: DevicePairingDisplay?, busy: Boolean, error: String?) {
    if (display != null || busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(Palette.accent, RoundedCornerShape(4.dp)))
        Text(if (display == null) "Creating a sign-in code…" else "Waiting for approval…", color = Palette.secondary, fontSize = 13.sp)
    }
    error?.let { Text(it, color = Palette.danger, fontSize = 13.sp) }
}
