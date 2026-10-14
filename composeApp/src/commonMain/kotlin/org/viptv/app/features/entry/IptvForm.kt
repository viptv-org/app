package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.*
import org.viptv.app.theme.Palette

@Composable
internal fun IptvForm(back: () -> Unit, busy: Boolean, error: String?, connect: IptvEntryActions?,
    mobile: Boolean, initial: IptvCredentials = IptvCredentials()) {
    var mode by remember { mutableStateOf(initial.mode) }
    var xtream by remember { mutableStateOf(if (initial.mode == IptvMode.Xtream) initial else IptvCredentials()) }
    var m3u by remember { mutableStateOf(if (initial.mode == IptvMode.M3u) initial else IptvCredentials(IptvMode.M3u)) }
    val draft = if (mode == IptvMode.Xtream) xtream else m3u
    val update: (IptvCredentials) -> Unit = { if (mode == IptvMode.Xtream) xtream = it else m3u = it }
    val latestActions by rememberUpdatedState(connect)
    DisposableEffect(Unit) { onDispose { latestActions?.cancel?.invoke() } }
    Column(Modifier.fillMaxWidth()) {
        PairingBackLink(back, mobile)
        Spacer(Modifier.height(if (mobile) 16.dp else 12.dp))
        Text("Sign in with your IPTV provider", color = Palette.text, fontSize = if (mobile) 26.sp else 23.sp,
            lineHeight = if (mobile) 29.9.sp else 26.45.sp, letterSpacing = if (mobile) (-.52).sp else (-.46).sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(if (mobile) 14.dp else 12.dp))
        Row(Modifier.fillMaxWidth().height(if (mobile) 38.dp else 34.dp)
            .background(Color.White.copy(alpha = .12f), RoundedCornerShape(if (mobile) 19.dp else 17.dp))
            .selectableGroup().padding(4.dp)) {
            IptvMode.entries.forEach { option ->
                Box(Modifier.width(if (mobile) 130.dp else 117.dp).fillMaxHeight()
                    .background(if (mode == option) Palette.background else Color.Transparent,
                        RoundedCornerShape(if (mobile) 15.dp else 13.dp))
                    .selectable(selected = mode == option, enabled = !busy, role = Role.Tab,
                        onClick = { mode = option }), contentAlignment = Alignment.Center) {
                    Text(if (option == IptvMode.Xtream) "Xtream Codes" else "M3U playlist", color = if (mode == option) Palette.text else Palette.secondary,
                        fontSize = if (mobile) 14.sp else 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
                }
            }
        }
        Spacer(Modifier.height(if (mobile) 14.dp else 12.dp))
        IptvInputs(draft, mobile, update)
        error?.let { Text(it, Modifier.padding(top = 12.dp), color = Palette.danger, fontSize = 13.sp) }
        Spacer(Modifier.height(if (mobile) 14.dp else 12.dp))
        Button(onClick = { connect?.connect?.invoke(draft) }, enabled = !busy && draft.complete && connect != null,
            modifier = Modifier.fillMaxWidth().height(if (mobile) 48.dp else 43.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
            Text(if (busy) "Connecting…" else "Connect", fontSize = if (mobile) 16.sp else 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("Provider details are saved on this device for your selected profile.", color = Palette.secondary,
            fontSize = if (mobile) 13.sp else 12.sp, lineHeight = if (mobile) 18.85.sp else 17.4.sp, letterSpacing = 0.sp)
    }
}
