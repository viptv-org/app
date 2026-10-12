package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.IntegrationConnection

@Composable
internal fun TvAddonSettings(addons: List<IntegrationConnection>, enabled: Set<String>, local: Boolean, busy: Boolean,
    scale: Float, initial: FocusRequester, sidebar: FocusRequester, info: Map<String, TvAddonInfo>, toggle: (String, Boolean) -> Unit,
    install: () -> Unit, unavailable: () -> Unit, placeholderQr: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        TvSettingsHeading("Add-ons", scale, "Add-ons find the movies, series and channels you watch here.")
        Row(horizontalArrangement = Arrangement.spacedBy(60.dp * scale)) {
            TvFocusScroll {
                LazyColumn(Modifier.width(760.dp * scale), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    items(addons, key = { it.id }) { addon ->
                        TvAction(Modifier.fillMaxWidth().height(100.dp * scale).focusProperties { left = sidebar }
                            .semantics { role = Role.Switch; toggleableState = if (addon.id in enabled) ToggleableState.On else ToggleableState.Off }, scale, enabled = !busy,
                            background = Color(0xFF191919), arrangement = Arrangement.Start, contentPadding = PaddingValues(horizontal = 24.dp * scale),
                            activate = { if (local) toggle(addon.id, addon.id !in enabled) else unavailable() }) { tint ->
                            Box(Modifier.size(60.dp * scale).background(Color(info[addon.id]?.color ?: 0xFF3D6C88), RoundedCornerShape(14.dp * scale)), contentAlignment = Alignment.Center) {
                                Text(info[addon.id]?.letter ?: addon.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "A", color = Palette.text, fontSize = (28 * scale).sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(20.dp * scale))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
                                Text(addon.name, color = tint, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
                                Text(info[addon.id]?.subtitle ?: addon.capabilities.joinToString(" · ") { it.name.lowercase().replaceFirstChar(Char::uppercaseChar) },
                                    color = tint.copy(alpha = 0.75f), fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp)
                            }
                            TvSettingsSwitch(addon.id in enabled, scale)
                        }
                    }
                    item { TvAction(Modifier.fillMaxWidth().height(100.dp * scale).focusProperties { left = sidebar }, scale, requester = initial,
                        enabled = !busy, activate = { if (local) install() else unavailable() }, arrangement = Arrangement.Start,
                        contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                        Box(Modifier.size(60.dp * scale).background(it.copy(alpha = 0.1f), RoundedCornerShape(14.dp * scale)), contentAlignment = Alignment.Center) {
                            Icon(Lucide.Plus, "Add an add-on", Modifier.size(30.dp * scale), it)
                        }
                        Spacer(Modifier.width(20.dp * scale)); Text("Add an add-on", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                    } }
                }
            }
            Column(Modifier.width(364.dp * scale).background(Color(0xFF191919), RoundedCornerShape(18.dp * scale)).padding(horizontal = 28.dp * scale, vertical = 28.dp * scale),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp * scale)) {
                Box(Modifier.size(236.dp * scale).background(Palette.text, RoundedCornerShape(14.dp * scale)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (placeholderQr) Lucide.ScanQrCode else Lucide.Smartphone, null, Modifier.size(72.dp * scale), Palette.background)
                        Text(if (placeholderQr) "[QR code]" else "Pairing unavailable", color = Palette.background, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(if (placeholderQr) "Scan to add one from your phone" else "Add-on pairing is not available yet.",
                    fontSize = (24 * scale).sp, lineHeight = (35 * scale).sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(if (placeholderQr) "or go to\nviptv.syek.tech/addons" else "Use Add an add-on to enter its address on this TV.",
                    fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp, color = Palette.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
