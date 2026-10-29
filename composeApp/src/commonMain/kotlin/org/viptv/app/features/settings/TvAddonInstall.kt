package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

@Composable
internal fun TvAddonInstallConfirm(address: String, profileName: String?, local: Boolean, busy: Boolean,
    scale: Float, confirm: () -> Unit, cancel: () -> Unit) {
    val initial = remember { FocusRequester() }
    val host = address.substringAfter("://").substringBefore('/').ifEmpty { address }
    Popup(onDismissRequest = cancel, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .78f)).onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && (it.key == Key.Back || it.key == Key.Escape)) { cancel(); true } else false
        }, contentAlignment = Alignment.Center) {
            Column(Modifier.width(900.dp * scale).clip(RoundedCornerShape(24.dp * scale)).background(Palette.raised)
                .padding(48.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                Text("Install this add-on?", fontSize = (44 * scale).sp, lineHeight = (50 * scale).sp,
                    letterSpacing = (-0.88f * scale).sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .06f), RoundedCornerShape(16.dp * scale))
                    .padding(22.dp * scale), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(22.dp * scale)) {
                    Box(Modifier.size(88.dp * scale).background(Color.White.copy(alpha = .12f), RoundedCornerShape(18.dp * scale)),
                        contentAlignment = Alignment.Center) {
                        Text(host.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "A",
                            fontSize = (40 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
                        Text(host, fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("From $host", fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, color = Palette.secondary)
                    }
                }
                Text(if (local) "Installs on this TV only. Only install add-ons you trust."
                    else "Installs on every device on ${profileName?.let { "$it\u2019s" } ?: "this"} account. Only install add-ons you trust.",
                    fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp, color = Palette.tertiary)
                if (busy) Text("Installing\u2026", fontSize = (24 * scale).sp, color = Palette.secondary)
                Row(Modifier.padding(top = 18.dp * scale), horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    TvAction(Modifier.width(260.dp * scale).height(72.dp * scale), scale, primary = true, requester = initial,
                        enabled = !busy, activate = confirm) { tint ->
                        Icon(Lucide.Plus, null, Modifier.size(28.dp * scale), tint); Spacer(Modifier.width(12.dp * scale))
                        Text("Install", color = tint, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                    TvAction(Modifier.width(220.dp * scale).height(72.dp * scale), scale, enabled = !busy, activate = cancel) { tint ->
                        Text("Cancel", color = tint, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { initial.requestFocus() }
    }
}
