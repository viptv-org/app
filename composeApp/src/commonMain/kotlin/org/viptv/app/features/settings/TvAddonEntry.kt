package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.ScanQrCode
import com.composables.icons.lucide.Smartphone
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvTextField
import org.viptv.app.theme.Palette

@Composable
internal fun TvAddonEntryPage(address: String, addressChange: (String) -> Unit, busy: Boolean, error: String?,
    scale: Float, placeholderQr: Boolean, back: () -> Unit, install: () -> Unit) {
    Box(Modifier.fillMaxSize().imePadding().onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown) false else when (it.key) {
            Key.Back, Key.Escape -> { if (!busy) back(); true }
            else -> false
        }
    }) {
        Column(Modifier.offset(96.dp * scale, 72.dp * scale).width(1700.dp * scale),
            verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            Text("Add-ons", fontSize = (22 * scale).sp, lineHeight = (31.9f * scale).sp,
                fontWeight = FontWeight.SemiBold, color = Palette.tertiary)
            Text("Add an add-on", fontSize = (52 * scale).sp, lineHeight = (59.8f * scale).sp,
                letterSpacing = (-1.04f * scale).sp, fontWeight = FontWeight.Bold)
            Text("Type the address of the add-on\u2019s manifest.", fontSize = (24 * scale).sp,
                lineHeight = (34.8f * scale).sp, color = Palette.secondary)
        }
        TvTextField(address, { addressChange(it.take(2048)) }, scale,
            Modifier.offset(96.dp * scale, 250.dp * scale).width(1060.dp * scale),
            label = "Add-on address", placeholder = "https://addon.example/manifest.json", enabled = !busy,
            imeAction = ImeAction.Go, submit = { if (address.isNotBlank() && !busy) install() },
            focusOnEnter = true, onEscape = { if (!busy) back() })
        Row(Modifier.offset(96.dp * scale, 430.dp * scale), horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
            TvAction(Modifier.width(260.dp * scale).heightIn(min = 72.dp * scale), scale, primary = true,
                enabled = address.isNotBlank() && !busy, activate = install) { tint ->
                Icon(Lucide.Plus, null, Modifier.size(28.dp * scale), tint); Spacer(Modifier.width(12.dp * scale))
                Text("Install", color = tint, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
            }
            TvAction(Modifier.width(220.dp * scale).heightIn(min = 72.dp * scale), scale, activate = back) { tint ->
                Text("Cancel", color = tint, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.offset(1260.dp * scale, 250.dp * scale).width(564.dp * scale)
            .background(Color.White.copy(alpha = .06f), RoundedCornerShape(18.dp * scale)).padding(32.dp * scale),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp * scale)) {
            Box(Modifier.size(236.dp * scale).background(Palette.text, RoundedCornerShape(14.dp * scale)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (placeholderQr) Lucide.ScanQrCode else Lucide.Smartphone, null, Modifier.size(72.dp * scale), Palette.background)
                    Text(if (placeholderQr) "[QR code]" else "Pairing unavailable", color = Palette.background,
                        fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(if (placeholderQr) "Easier: scan to add it from your phone" else "Add-on pairing is not available yet.",
                fontSize = (26 * scale).sp, lineHeight = (34 * scale).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(if (placeholderQr) "or go to https://your-server.example/addons" else "Type its address on this screen instead.",
                fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp, color = Palette.secondary, textAlign = TextAlign.Center)
        }
        if (busy || error != null) Column(Modifier.offset(1260.dp * scale, 700.dp * scale).width(564.dp * scale),
            verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            if (busy) Text("Installing\u2026", fontSize = (22 * scale).sp, color = Palette.secondary)
            error?.let { Text(it, fontSize = (22 * scale).sp, lineHeight = (31.9f * scale).sp, color = Palette.danger) }
        }
    }
}