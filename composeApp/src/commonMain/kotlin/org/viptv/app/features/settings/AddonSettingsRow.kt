package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.MutationSpinner
import org.viptv.app.components.TextButton
import org.viptv.app.state.MutationKind
import org.viptv.app.state.MutationTarget
import org.viptv.app.components.Switch
import org.viptv.app.components.requestFocusWhenPlaced

internal const val ADDON_TARGET_ROW_TAG = "addon-target-row"

@Composable
internal fun AddonSettingsRow(addon: IntegrationConnection, enabled: Boolean, busy: Boolean, manageable: Boolean,
    toggle: (Boolean) -> Unit, remove: () -> Unit, logo: String? = null, highlighted: Boolean = false,
    focusRequester: FocusRequester? = null, configurable: Boolean = false, reconfigure: () -> Unit = {}) {
    val pending = LocalPendingMutation.current.isPending(MutationTarget(MutationKind.Addon, addon.id))
    val shape = RoundedCornerShape(14.dp)
    Surface(color = if (highlighted) Palette.raised else Palette.surface, shape = shape,
        modifier = if (highlighted) Modifier.border(2.dp, Palette.accent, shape).testTag(ADDON_TARGET_ROW_TAG) else Modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AddonLogoTile(logo, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Palette.raised),
                Modifier.padding(4.dp)) { Icon(Lucide.Puzzle, null, Modifier.size(24.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(addon.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (!addon.enabled) "Disabled for this account" else addon.providesSummary(), fontSize = 13.sp,
                    color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (pending) MutationSpinner(20.dp, color = Palette.secondary)
            if (manageable && configurable) TextButton(onClick = reconfigure, enabled = !busy && !pending) { Text("Reconfigure", color = Palette.secondary) }
            if (manageable) TextButton(onClick = remove, enabled = !busy && !pending) { Text("Remove", color = Palette.secondary) }
            if (manageable) Switch(enabled, toggle, colors = SwitchDefaults.colors(checkedTrackColor = Palette.accent, checkedThumbColor = Palette.background),
                enabled = !busy && !pending && addon.enabled && addon.capabilities.isNotEmpty(),
                modifier = (if (focusRequester == null) Modifier else focusRequester.requestFocusWhenPlaced())
                    .semantics { contentDescription = "Enable ${addon.name} for this profile" })
        }
    }
}
