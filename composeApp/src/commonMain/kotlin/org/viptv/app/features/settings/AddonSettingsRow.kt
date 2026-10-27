package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.app.components.TextButton
import org.viptv.app.components.Switch

@Composable
internal fun AddonSettingsRow(addon: IntegrationConnection, enabled: Boolean, busy: Boolean, manageable: Boolean,
    toggle: (Boolean) -> Unit, remove: () -> Unit) {
    Surface(color = Palette.surface, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AddonLogoTile(addon.logo, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Palette.raised),
                    Modifier.padding(4.dp)) { Icon(Lucide.Puzzle, null, Modifier.size(24.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(addon.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (!addon.enabled) "Disabled for this account" else addon.capabilities.joinToString(" · ") { it.name.lowercase().replaceFirstChar(Char::uppercaseChar) },
                        fontSize = 13.sp, color = Palette.secondary)
                }
                if (manageable) Switch(enabled, toggle, colors = SwitchDefaults.colors(checkedTrackColor = Palette.accent, checkedThumbColor = Palette.background), enabled = !busy && addon.enabled && addon.capabilities.isNotEmpty(), modifier = Modifier.semantics { contentDescription = "Enable ${addon.name} for this profile" })
            }
            if (manageable) TextButton(onClick = remove, enabled = !busy, modifier = Modifier.align(Alignment.End)) { Text("Remove", color = Palette.secondary) }
        }
    }
}
