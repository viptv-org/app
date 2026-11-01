package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.components.rememberTvColumnItem
import org.viptv.app.connection.AccountDevice
import org.viptv.app.connection.AccountDevices
import org.viptv.app.connection.historyDayLabel
import org.viptv.app.theme.Palette

@Composable
internal fun TvAccountDevices(snapshot: AccountDevices?, busy: Boolean, error: String?, load: () -> Unit,
    scale: Float, first: FocusRequester, sidebar: FocusRequester, back: () -> Unit) {
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && (it.key == Key.Back || it.key == Key.Escape)) { back(); true } else false
    }, verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        TvSettingsHeading("Devices & sessions", scale, subtitle = accountsLead(snapshot))
        if (busy && snapshot == null) repeat(3) {
            Box(Modifier.fillMaxWidth().height(96.dp * scale).background(Color(0xFF191919), RoundedCornerShape(14.dp * scale)))
        }
        val devices = snapshot?.devices.orEmpty()
        if (snapshot != null && devices.isEmpty()) Text("No other devices are signed in.", Modifier.padding(vertical = (12).dp * scale),
            color = Palette.secondary, fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp)
        devices.forEachIndexed { index, device ->
            TvAccountDeviceRow(device, scale, if (index == 0) first else null, sidebar)
        }
        if (snapshot?.can_sign_out_all == true) TvSettingsDisabledRow("Sign out of all devices", tvAccountManagedElsewhere, scale)
        Text(tvAccountManagedElsewhere, Modifier.padding(top = 4.dp * scale), color = Palette.secondary,
            fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp)
        if (error != null) Text(error, Modifier.padding(top = 4.dp * scale), color = Palette.danger,
            fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
        if (error != null || snapshot == null && !busy) {
            TvAction(Modifier.fillMaxWidth().height(96.dp * scale), scale, ring = false, left = sidebar,
                requester = if (devices.isEmpty()) first else null, enabled = !busy, activate = load) { tint ->
                Text("Retry", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TvAccountDeviceRow(device: AccountDevice, scale: Float, requester: FocusRequester?, left: FocusRequester) {
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) Palette.background else Palette.text
    val fallback = remember { FocusRequester() }
    val target = requester ?: fallback
    val column = rememberTvColumnItem(target)
    Row(Modifier.fillMaxWidth().height(96.dp * scale).then(column.position).focusRequester(target)
        .focusProperties { this.left = left; column.constrain(this) }
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer { this.shape = RoundedCornerShape(14.dp * scale); shadowElevation = if (focused) 24.dp.toPx() * scale else 0f; clip = false }
        .background(if (focused) Palette.text else Color(0xFF191919), RoundedCornerShape(14.dp * scale))
        .focusable()
        .semantics(mergeDescendants = true) {}
        .padding(horizontal = 28.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp * scale).background(Palette.text.copy(alpha = .1f), RoundedCornerShape(10.dp * scale)),
            contentAlignment = Alignment.Center) {
            Icon(if (device.kind == "browser") Lucide.ExternalLink else Lucide.Monitor, null, Modifier.size(20.dp * scale), color)
        }
        Spacer(Modifier.width(20.dp * scale))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
            Text(device.displayName(), color = color, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.SemiBold)
            Text(if (device.current) "Now" else "Signed in ${signedInLabel(device)}", color = if (focused) Color(0xFF4A4945) else Palette.secondary,
                fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp, fontWeight = FontWeight.Medium)
        }
        if (device.current) Text("This device", color = Palette.tertiary, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp)
        else Text("Sign out", color = Palette.danger.copy(alpha = .4f),
            fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun accountsLead(snapshot: AccountDevices?): String =
    "Everywhere ${snapshot?.account?.name?.let { "${it}’s account" } ?: "your account"} is signed in. Signing a device out doesn’t delete anything from the account."

private fun signedInLabel(device: AccountDevice): String =
    historyDayLabel(device.created_at * 1000L).let { if (it == "Today" || it == "Yesterday") it.lowercase() else it }