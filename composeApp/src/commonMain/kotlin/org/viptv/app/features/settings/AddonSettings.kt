package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.MutationSpinner
import org.viptv.app.state.ADDON_INSTALL_TARGET
import org.viptv.app.state.MutationKind
import org.viptv.app.state.MutationTarget
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.app.components.Button
import org.viptv.app.components.BackControl
import org.viptv.app.components.LocalPointerHover
import org.viptv.app.components.PhoneSheet
import org.viptv.app.components.TextButton
import org.viptv.app.components.IconButton

@Composable
internal fun AddonSettings(local: Boolean, addons: List<IntegrationConnection>, enabledAddonIds: Set<String>, busy: Boolean,
    error: String?, sessionNotice: String?, install: (String) -> Unit, toggle: (String, Boolean) -> Unit,
    remove: (String) -> Unit, back: (() -> Unit)? = null, managementAvailable: Boolean = true,
    installWithCompletion: ((String, (Boolean) -> Unit) -> Unit)? = null, logos: Map<String, String> = emptyMap(),
    initialAddonId: String? = null, configurableAddons: Set<String> = emptySet(), configure: (String) -> Unit = {},
    configureFailure: Pair<String, String>? = null, retryConfigure: () -> Unit = {},
    profileChangesAvailable: Boolean = managementAvailable, inventoryLoaded: Boolean = true) {
    val address = rememberTextFieldState()
    var installing by remember { mutableStateOf(false) }
    var submission by remember { mutableStateOf<Any?>(null) }
    DisposableEffect(Unit) { onDispose { submission = null } }
    val mutations = LocalPendingMutation.current
    val installTarget = MutationTarget(MutationKind.Addon, ADDON_INSTALL_TARGET)
    val installingPending = mutations.isPending(installTarget)
    val sheetBusy = busy || submission != null || installingPending
    val closeInstall = { if (!busy && submission == null) { submission = null; installing = false } }
    val submitInstall = {
        if (!busy && submission == null && address.text.isNotBlank()) {
            val complete = installWithCompletion
            if (complete == null) install(address.text.toString().trim())
            else {
                val owner = Any()
                submission = owner
                complete(address.text.toString().trim()) { success ->
                    if (submission === owner && installing) {
                        submission = null
                        if (success) { address.clearText(); installing = false }
                    }
                }
            }
        }
    }
    var removing by remember { mutableStateOf<IntegrationConnection?>(null) }
    var query by remember { mutableStateOf("") }
    var provides by remember { mutableStateOf<AddonProvides?>(null) }
    val filter = AddonFilter(query, provides)
    val counts = remember(addons) { addonProvidesCounts(addons) }
    val shown = filter.filtered(addons)
    val listState = rememberLazyListState()
    val targetRow = remember(shown, initialAddonId) { shown.indexOfFirst { it.id == initialAddonId } }
    val targetFocus = remember(initialAddonId) { FocusRequester() }
    LaunchedEffect(targetRow) { if (targetRow >= 0) listState.scrollToItem(targetRow + 1) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 640.dp
        val pane = settingsPanePadding(compact)
        LazyColumn(Modifier.widthIn(max = 824.dp).fillMaxSize(), state = listState, contentPadding = PaddingValues(start = pane.calculateStartPadding(LocalLayoutDirection.current),
            top = if (compact) 0.dp else pane.calculateTopPadding(), end = pane.calculateEndPadding(LocalLayoutDirection.current),
            bottom = pane.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // One item, so the row list starts at index 1 and a row's position is its own index plus one.
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (compact) Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
                        back?.let { BackControl(it, Modifier.align(Alignment.CenterStart)) }
                        Text("Add-ons", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        back?.let { BackControl(it, desktop = true) }
                        SettingsPaneTitle("Add-ons")
                    }
                    Text(if (local) "Add-ons find the movies, series and channels you watch. Installed add-ons are shared by profiles on this device; switches apply to the current profile."
                        else "Add-ons find the movies, series and channels you watch. Installed add-ons belong to this account; switches apply to the current profile.",
                        fontSize = 14.sp, lineHeight = 21.sp, color = Palette.secondary)
                    sessionNotice?.let { Text(it, color = Palette.danger) }
                    if (!managementAvailable) Text("Add-on management is not available on this connection.", color = Palette.secondary)
                    if (compact && managementAvailable) {
                        Button(onClick = { installing = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                            shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                            Icon(Lucide.Plus, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Add an add-on", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (!compact && managementAvailable) AddonInstallForm(address, busy || installingPending, { install(address.text.toString().trim()) })
                    if (busy || (!inventoryLoaded && error == null)) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
                    error?.let { Text(it, color = Palette.danger) }
                    configureFailure?.let { (_, message) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(message, color = Palette.danger, modifier = Modifier.weight(1f, fill = false))
                            TextButton(retryConfigure) { Text("Try again") }
                        }
                    }
                    if (addons.isNotEmpty()) {
                        AddonFilterControls(query, { query = it }, provides, { provides = it }, counts, shown.size, addons.size)
                    }
                    if (addons.isNotEmpty() && !compact) Text("Installed", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (inventoryLoaded && addons.isEmpty() && !busy && error == null) Text("No add-ons installed.", color = Palette.secondary)
                    if (addons.isNotEmpty() && shown.isEmpty()) Text("No add-ons match.", color = Palette.secondary)
                }
            }
            items(shown, key = { it.id }) { addon ->
                AddonSettingsRow(addon, addon.id in enabledAddonIds, busy, managementAvailable,
                    { toggle(addon.id, it) }, { removing = addon }, logos[addon.id],
                    highlighted = addon.id == initialAddonId,
                    focusRequester = targetFocus.takeIf { addon.id == initialAddonId },
                    configurable = addon.id in configurableAddons,
                    reconfigure = { configure(addon.id) }, profileChangesAvailable = profileChangesAvailable)
            }
        }
        PhoneSheet(visible = installing, title = "Add an add-on", dismiss = closeInstall,
            touch = !LocalPointerHover.current, closeEnabled = !sheetBusy, closeLabel = "Close add-on form",
            dragEnabled = !sheetBusy) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                AddonInstallForm(address, sheetBusy, submitInstall, compact = true, dismiss = closeInstall)
                if (sheetBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = Palette.danger) }
            }
        }
        removing?.let { selected ->
            val pending = mutations.isPending(MutationTarget(MutationKind.Addon, selected.id))
            AlertDialog(onDismissRequest = { if (!busy && !pending) removing = null }, title = { Text("Remove ${selected.name}?") },
                text = { Text(if (local) "This removes the add-on from every profile on this device." else "This removes the add-on from this account and its profiles.") },
                confirmButton = { TextButton(onClick = { if (!pending) { remove(selected.id); removing = null } }, enabled = !busy && !pending) {
                    if (pending) { MutationSpinner(16.dp, color = Palette.danger); Spacer(Modifier.width(8.dp)); Text("Removing…", color = Palette.danger) }
                    else Text("Remove", color = Palette.danger)
                } },
                dismissButton = { TextButton(onClick = { removing = null }, enabled = !busy) { Text("Cancel") } })
        }
    }
}
