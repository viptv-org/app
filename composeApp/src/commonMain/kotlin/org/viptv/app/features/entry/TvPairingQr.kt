package org.viptv.app.features.entry

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.viptv.app.theme.Palette

@Composable
internal fun TvPairingQr(session: TvPairingSession?, scale: Float) {
    Column(Modifier.offset(96.dp * scale, 250.dp * scale).width(760.dp * scale), verticalArrangement = Arrangement.spacedBy(22.dp * scale)) {
        Text("Scan with your phone", fontSize = (30 * scale).sp, lineHeight = (43.5f * scale).sp, fontWeight = FontWeight.Bold)
        Text(if (session == null) "Phone pairing is not available on this connection yet."
            else "Point your camera at the code. You’ll finish on your phone and this TV signs in by itself.",
            fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp, color = Palette.secondary)
        Box(Modifier.padding(top = 10.dp * scale).size(340.dp * scale).background(Color.White, RoundedCornerShape(20.dp * scale)), contentAlignment = Alignment.Center) {
            if (session != null) Image(rememberQrCodePainter(session.qrUrl) { this.scale = .88f }, "Pairing QR code",
                Modifier.size(300.dp * scale))
            else Text("Pairing unavailable", color = Palette.background, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp)
        }
    }
}
