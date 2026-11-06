package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.AccountDevice
import org.viptv.app.connection.AccountDevices
import org.viptv.app.theme.Palette
import org.viptv.app.components.Button
import org.viptv.app.components.BackControl
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.TextButton
import org.viptv.app.state.MutationKind
import org.viptv.app.state.MutationTarget
import org.viptv.app.state.SIGN_OUT_ALL_TARGET
import org.viptv.app.state.signOutDeviceTarget

@Composable
internal fun AccountDevicesScreen(snapshot: AccountDevices?, busy: Boolean, error: String?, load: () -> Unit,
    signOut: (String) -> Unit, signOutAll: () -> Unit, compact: Boolean = false, back: (() -> Unit)? = null,
    canSignOut: Boolean = true, retryable: Boolean = true) {
    var removing by remember { mutableStateOf<AccountDevice?>(null) }
    var removingAll by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(snapshot) {
        if (snapshot == null) removingAll = false
        if (removing != null && snapshot?.devices?.none { it.id == removing?.id } != false) removing = null
    }
    val horizontal = settingsPaneHorizontalPadding(compact, fallback = if (compact) 16.dp else 0.dp)
    val vertical = settingsPaneVerticalPadding(compact, fallback = 0.dp)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = horizontal, vertical = vertical)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (compact) {
                    Box(Modifier.fillMaxWidth().height(56.dp)) {
                        back?.let { BackControl(it, Modifier.align(Alignment.CenterStart)) }
                        Text("Devices", Modifier.align(Alignment.Center), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    SettingsPaneTitle("Devices")
                }
                Text("Everywhere ${snapshot?.account?.name?.let { "${it}’s account" } ?: "your account"} is signed in. Signing a device out doesn’t delete anything from the account.",
                    fontSize = 14.sp, lineHeight = 20.sp, color = Palette.secondary)
            }
            Spacer(Modifier.height(14.dp))
        }
        if (busy && snapshot == null) items(3) {
            Box(Modifier.fillMaxWidth().height(68.dp).padding(bottom = 1.dp).background(Palette.surface))
        }
        if (snapshot != null && snapshot.devices.isEmpty()) item {
            Text("No other devices are signed in.", color = Palette.secondary)
        }
        itemsIndexed(snapshot?.devices.orEmpty(), key = { _, device -> device.id }) { index, device ->
            val first = index == 0
            val last = index == snapshot!!.devices.lastIndex
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = if (first) 14.dp else 0.dp,
                topEnd = if (first) 14.dp else 0.dp, bottomStart = if (last) 14.dp else 0.dp, bottomEnd = if (last) 14.dp else 0.dp))
                .background(Palette.text.copy(alpha = .05f))) {
                AccountDeviceRow(device, busy, compact, canSignOut) { removing = device }
                if (!last) HorizontalDivider(color = Palette.text.copy(alpha = .08f))
            }
        }
        if (snapshot?.can_sign_out_all == true && canSignOut) item {
            Spacer(Modifier.height(14.dp))
            Button({ removingAll = true }, Modifier.widthIn(min = 240.dp).height(40.dp), enabled = !busy,
                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.danger.copy(alpha = .12f), contentColor = Palette.danger)) {
                Text("Sign out of all devices", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            error?.let { Spacer(Modifier.height(14.dp)); Text(it, color = Palette.danger) }
            if (retryable && (error != null || snapshot == null && !busy)) TextButton(load, enabled = !busy) { Text("Retry") }
            if (snapshot != null) TextButton(load, enabled = !busy) { Text("Refresh") }
        }
    }
    val mutations = LocalPendingMutation.current
    removing?.let { device ->
        val target = MutationTarget(MutationKind.Account, signOutDeviceTarget(device.id))
        AccountDeviceConfirmation(device.displayName(), false, busy, mutations.failure(target) ?: error,
            { removing = null }, { signOut(device.id) }, pending = mutations.isPending(target))
    }
    if (removingAll) {
        val target = MutationTarget(MutationKind.Account, SIGN_OUT_ALL_TARGET)
        AccountDeviceConfirmation(null, true, busy, mutations.failure(target) ?: error,
            { removingAll = false }, signOutAll, pending = mutations.isPending(target))
    }
}
