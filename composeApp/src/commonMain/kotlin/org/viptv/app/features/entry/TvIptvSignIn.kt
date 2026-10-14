package org.viptv.app.features.entry

import org.viptv.app.connection.IptvCredentials
import org.viptv.app.connection.IptvMode

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette


@Composable
internal fun TvIptvSignIn(scale: Float, phone: () -> Unit, initial: IptvCredentials = IptvCredentials(), connect: IptvEntryActions? = null,
    busy: Boolean = false, error: String? = null) {
    var mode by remember { mutableStateOf(initial.mode) }
    var xtream by remember { mutableStateOf(if (initial.mode == IptvMode.Xtream) initial else IptvCredentials()) }
    var m3u by remember { mutableStateOf(if (initial.mode == IptvMode.M3u) initial else IptvCredentials(IptvMode.M3u)) }
    val draft = if (mode == IptvMode.Xtream) xtream else m3u
    val update: (IptvCredentials) -> Unit = { if (mode == IptvMode.Xtream) xtream = it else m3u = it }
    val latestActions by rememberUpdatedState(connect)
    DisposableEffect(Unit) { onDispose { latestActions?.cancel?.invoke() } }
    var field by remember { mutableStateOf<Int?>(null) }; var unavailable by remember { mutableStateOf(false) }
    val targets = remember { List(4) { FocusRequester() } }
    val tab = remember { FocusRequester() }
    var restore by remember { mutableStateOf<FocusRequester?>(null) }; val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(Unit) { targets[if (initial.mode == IptvMode.Xtream && initial.username.isNotEmpty()) 1 else 0].requestFocus() }
    LaunchedEffect(field, unavailable, restore, windowFocused) {
        if (field == null && !unavailable && windowFocused) restore?.let { withFrameNanos { }; it.requestFocus(); restore = null }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.offset(96.dp * scale, 96.dp * scale), verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            Text("IPTV provider", fontSize = (24 * scale).sp, lineHeight = (34.8f * scale).sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8F8D89))
            Text("Connect your IPTV provider", fontSize = (52 * scale).sp, lineHeight = (59.8f * scale).sp, letterSpacing = (-1.04f * scale).sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.offset(96.dp * scale, 250.dp * scale).focusProperties { onEnter = { tab.requestFocus() } }.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            IptvMode.entries.forEach { option -> TvAction(Modifier.height(64.dp * scale).focusProperties { down = targets[0] }, scale,
                requester = if (option == draft.mode) tab else null,
                activate = { mode = option }, cornerRadius = 32.dp * scale,
                background = if (draft.mode == option) Color.White.copy(alpha = .16f) else Color.Transparent,
                contentPadding = PaddingValues(horizontal = 28.dp * scale)) {
                Text(if (option == IptvMode.Xtream) "Xtream Codes" else "M3U playlist",
                    color = if (option == draft.mode || it == Palette.background) it else Palette.secondary,
                    fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
            } }
        }
        Column(Modifier.offset(96.dp * scale, 350.dp * scale).width(1130.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            TvIptvField(if (draft.mode == IptvMode.Xtream) "Server address" else "Playlist URL", draft.address, scale, targets[0], tab) { field = 0 }
            if (draft.mode == IptvMode.Xtream) {
                TvIptvField("Username", draft.username, scale, targets[1]) { field = 1 }
                TvIptvField("Password", "•".repeat(draft.password.length), scale, targets[2]) { field = 2 }
            }
        }
        Text("Select a field to type with the on-screen keyboard or your phone.", Modifier.offset(96.dp * scale, 710.dp * scale).width(1130.dp * scale),
            fontSize = (22 * scale).sp, lineHeight = (31.9f * scale).sp, color = Color(0xFF8F8D89))
        Column(Modifier.offset(1290.dp * scale, 350.dp * scale).width(534.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            TvAction(Modifier.fillMaxWidth().height(72.dp * scale), scale, requester = targets[3], enabled = !busy && (connect == null || draft.complete), activate = {
                if (connect == null) unavailable = true else connect.connect(if (draft.mode == IptvMode.M3u) draft.copy(username = "", password = "") else draft)
            }) { Icon(Lucide.ArrowRight, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale)); Text(if (busy) "Connecting…" else "Connect", color = it, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold) }
            TvAction(Modifier.fillMaxWidth().height(72.dp * scale), scale, activate = phone) {
                Icon(Lucide.Smartphone, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
                Text("Type it on your phone instead", color = it, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
            }
            Text("Provider details are saved on this device for your selected profile.", Modifier.padding(top = 14.dp * scale), fontSize = (22 * scale).sp, lineHeight = (31.9f * scale).sp, color = Color(0xFF8F8D89))
            error?.let { Text(it, fontSize = (22 * scale).sp, color = Palette.danger) }
        }
    }
    field?.let { index -> TvIptvDialog(if (index == 0) "Provider address" else if (index == 1) "Username" else "Password",
        if (index == 0) draft.address else if (index == 1) draft.username else draft.password, index == 2,
        { restore = targets[index]; field = null }, { value -> update(when(index) { 0 -> draft.copy(address = value); 1 -> draft.copy(username = value); else -> draft.copy(password = value) }) }) }
    if (unavailable) AlertDialog(onDismissRequest = { restore = targets[3]; unavailable = false }, title = { Text("IPTV provider") },
        text = { Text("Provider connections are not available on this connection yet.") }, confirmButton = { TextButton(onClick = { restore = targets[3]; unavailable = false }) { Text("OK") } })
}
