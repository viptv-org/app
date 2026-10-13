package org.viptv.app.features.entry

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

internal enum class TvIptvMode { Xtream, M3u }
internal data class TvIptvDraft(val mode: TvIptvMode = TvIptvMode.Xtream, val address: String = "", val username: String = "", val password: String = "")

@Composable
internal fun TvIptvSignIn(scale: Float, phone: () -> Unit, initial: TvIptvDraft = TvIptvDraft(), connect: ((TvIptvDraft) -> Unit)? = null) {
    var draft by remember { mutableStateOf(initial) }
    var field by remember { mutableStateOf<Int?>(null) }; var unavailable by remember { mutableStateOf(false) }
    val targets = remember { List(4) { FocusRequester() } }
    val tab = remember { FocusRequester() }
    var restore by remember { mutableStateOf<FocusRequester?>(null) }; val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(Unit) { targets[if (initial.mode == TvIptvMode.Xtream && initial.username.isNotEmpty()) 1 else 0].requestFocus() }
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
            TvIptvMode.entries.forEach { mode -> TvAction(Modifier.height(64.dp * scale).focusProperties { down = targets[0] }, scale,
                requester = if (mode == draft.mode) tab else null,
                activate = { draft = draft.copy(mode = mode) }, cornerRadius = 32.dp * scale,
                background = if (draft.mode == mode) Color.White.copy(alpha = .16f) else Color.Transparent,
                contentPadding = PaddingValues(horizontal = 28.dp * scale)) {
                Text(if (mode == TvIptvMode.Xtream) "Xtream Codes" else "M3U playlist",
                    color = if (mode == draft.mode || it == Palette.background) it else Palette.secondary,
                    fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
            } }
        }
        Column(Modifier.offset(96.dp * scale, 350.dp * scale).width(1130.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            TvIptvField(if (draft.mode == TvIptvMode.Xtream) "Server address" else "Playlist URL", draft.address, scale, targets[0], tab) { field = 0 }
            if (draft.mode == TvIptvMode.Xtream) {
                TvIptvField("Username", draft.username, scale, targets[1]) { field = 1 }
                TvIptvField("Password", "•".repeat(draft.password.length), scale, targets[2]) { field = 2 }
            }
        }
        Text("Select a field to type with the on-screen keyboard or your phone.", Modifier.offset(96.dp * scale, 710.dp * scale).width(1130.dp * scale),
            fontSize = (22 * scale).sp, lineHeight = (31.9f * scale).sp, color = Color(0xFF8F8D89))
        Column(Modifier.offset(1290.dp * scale, 350.dp * scale).width(534.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            TvAction(Modifier.fillMaxWidth().height(72.dp * scale), scale, requester = targets[3], activate = {
                if (connect == null) unavailable = true else connect(if (draft.mode == TvIptvMode.M3u) draft.copy(username = "", password = "") else draft)
            }) { Icon(Lucide.ArrowRight, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale)); Text("Connect", color = it, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold) }
            TvAction(Modifier.fillMaxWidth().height(72.dp * scale), scale, activate = phone) {
                Icon(Lucide.Smartphone, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
                Text("Type it on your phone instead", color = it, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
            }
            Text("Provider details stay in this form until connected.", Modifier.padding(top = 14.dp * scale), fontSize = (22 * scale).sp, lineHeight = (31.9f * scale).sp, color = Color(0xFF8F8D89))
        }
    }
    field?.let { index -> TvIptvDialog(if (index == 0) "Provider address" else if (index == 1) "Username" else "Password",
        if (index == 0) draft.address else if (index == 1) draft.username else draft.password, index == 2,
        { restore = targets[index]; field = null }, { value -> draft = when(index) { 0 -> draft.copy(address = value); 1 -> draft.copy(username = value); else -> draft.copy(password = value) } }) }
    if (unavailable) AlertDialog(onDismissRequest = { restore = targets[3]; unavailable = false }, title = { Text("IPTV provider") },
        text = { Text("Provider connections are not available on this connection yet.") }, confirmButton = { TextButton(onClick = { restore = targets[3]; unavailable = false }) { Text("OK") } })
}
