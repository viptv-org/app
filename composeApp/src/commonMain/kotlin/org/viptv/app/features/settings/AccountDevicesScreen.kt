package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.AccountDevice
import org.viptv.app.connection.AccountDevices
import org.viptv.app.theme.Palette
import org.viptv.app.components.Button
import org.viptv.app.components.TextButton

@Composable
internal fun AccountDevicesScreen(snapshot: AccountDevices?, busy: Boolean, error: String?, load: () -> Unit,
    signOut: (String) -> Unit, signOutAll: () -> Unit, compact: Boolean = false, back: (() -> Unit)? = null) {
    var removing by remember { mutableStateOf<AccountDevice?>(null) }
    var removingAll by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(snapshot) {
        if (snapshot == null) removingAll = false
        if (removing != null && snapshot?.devices?.none { it.id == removing?.id } != false) removing = null
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = if (compact) PaddingValues(16.dp) else PaddingValues(0.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                back?.let { TextButton(it) { Text("Back") } }
                Text("Devices", fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.48).sp)
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
                AccountDeviceRow(device, busy, compact) { removing = device }
                if (!last) HorizontalDivider(color = Palette.text.copy(alpha = .08f))
            }
        }
        if (snapshot?.can_sign_out_all == true) item {
            Spacer(Modifier.height(14.dp))
            Button({ removingAll = true }, Modifier.widthIn(min = 240.dp).height(40.dp), enabled = !busy,
                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.danger.copy(alpha = .12f), contentColor = Palette.danger)) {
                Text("Sign out of all devices", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            error?.let { Spacer(Modifier.height(14.dp)); Text(it, color = Palette.danger) }
            if (error != null || snapshot == null && !busy) TextButton(load, enabled = !busy) { Text("Retry") }
            if (snapshot != null) TextButton(load, enabled = !busy) { Text("Refresh") }
        }
    }
    removing?.let { device -> AccountDeviceConfirmation(device.displayName(), false, busy, error,
        { removing = null }, { signOut(device.id) }) }
    if (removingAll) AccountDeviceConfirmation(null, true, busy, error,
        { removingAll = false }, signOutAll)
}
