package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.IntegrationConnection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddonSettings(local: Boolean, addons: List<IntegrationConnection>, enabledAddonIds: Set<String>, busy: Boolean,
    error: String?, sessionNotice: String?, install: (String) -> Unit, toggle: (String, Boolean) -> Unit,
    remove: (String) -> Unit, back: (() -> Unit)? = null, managementAvailable: Boolean = true) {
    val address = rememberTextFieldState()
    var installing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<IntegrationConnection?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 640.dp
        LazyColumn(Modifier.widthIn(max = 824.dp).fillMaxSize(), contentPadding = PaddingValues(if (compact) 16.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    back?.let { IconButton(onClick = it) { Icon(Lucide.ChevronLeft, "Back to settings") } }
                    Text("Add-ons", fontSize = if (compact) 18.sp else 28.sp, fontWeight = FontWeight.Bold)
                }
            }
            item { Text(if (local) "Add-ons find the movies, series and channels you watch. Installed add-ons are shared by profiles on this device; switches apply to the current profile."
                else "Add-ons find the movies, series and channels you watch. Installed add-ons belong to this account; switches apply to the current profile.",
                fontSize = 14.sp, lineHeight = 21.sp, color = Palette.secondary) }
            sessionNotice?.let { item { Text(it, color = Palette.danger) } }
            if (!managementAvailable) item { Text("Add-on management is not available on this connection.", color = Palette.secondary) }
            if (!compact && managementAvailable) item { AddonInstallForm(address, busy, { install(address.text.toString().trim()) }) }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
            error?.let { item { Text(it, color = Palette.danger) } }
            if (addons.isNotEmpty() && !compact) item { Text("Installed", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            if (addons.isEmpty() && !busy) item { Text("No add-ons installed.", color = Palette.secondary) }
            items(addons, key = { it.id }) { addon ->
                AddonSettingsRow(addon, addon.id in enabledAddonIds, busy, managementAvailable,
                    { toggle(addon.id, it) }, { removing = addon })
            }
            if (compact && managementAvailable) item {
                Button(onClick = { installing = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                    Icon(Lucide.Plus, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Add an add-on", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (installing) ModalBottomSheet(onDismissRequest = { if (!busy) installing = false }, containerColor = Palette.surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Add an add-on", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { installing = false }, enabled = !busy) { Icon(Lucide.X, "Close add-on form") }
                }
                AddonInstallForm(address, busy, { install(address.text.toString().trim()) }, compact = true, dismiss = { if (!busy) installing = false })
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = Palette.danger) }
            }
        }
        removing?.let { selected ->
            AlertDialog(onDismissRequest = { if (!busy) removing = null }, title = { Text("Remove ${selected.name}?") },
                text = { Text(if (local) "This removes the add-on from every profile on this device." else "This removes the add-on from this account and its profiles.") },
                confirmButton = { TextButton(onClick = { remove(selected.id); removing = null }, enabled = !busy) { Text("Remove", color = Palette.danger) } },
                dismissButton = { TextButton(onClick = { removing = null }, enabled = !busy) { Text("Cancel") } })
        }
    }
}
