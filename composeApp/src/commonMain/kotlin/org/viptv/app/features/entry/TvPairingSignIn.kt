package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette
import org.viptv.app.components.TextButton

internal data class TvPairingSession(val qrUrl: String, val browserAddress: String, val code: String, val expiryLabel: String)

@Composable
internal fun TvPairingSignIn(scale: Float, email: () -> Unit, session: TvPairingSession? = null, refresh: (() -> Unit)? = null,
    busy: Boolean = false, error: String? = null) {
    val first = remember { FocusRequester() }; var notice by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }; val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(Unit) { first.requestFocus() }
    LaunchedEffect(notice, restore, windowFocused) { if (!notice && restore && windowFocused) { withFrameNanos { }; first.requestFocus(); restore = false } }
    Box(Modifier.fillMaxSize()) {
        Text("Sign in to VIPTV", Modifier.offset(96.dp * scale, 96.dp * scale), fontSize = (56 * scale).sp,
            lineHeight = (64.4f * scale).sp, letterSpacing = (-1.12f * scale).sp, fontWeight = FontWeight.Bold)
        TvPairingQr(session, scale, busy)
        Box(Modifier.offset(959.dp * scale, 250.dp * scale).size(2.dp * scale, 560.dp * scale).background(Color.White.copy(alpha = .12f)))
        Box(Modifier.offset(930.dp * scale, 500.dp * scale).size(60.dp * scale).background(Palette.background, CircleShape)
            .border(2.dp * scale, Color.White.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
            Text("or", fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, color = Palette.secondary)
        }
        TvPairingBrowser(session, scale, first, busy) { if (refresh == null) notice = true else refresh() }
        Row(Modifier.offset(96.dp * scale, 900.dp * scale), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
            if (session != null) Box(Modifier.size(12.dp * scale).background(Palette.accent, CircleShape))
            Text(error ?: if (session == null) { if (busy) "Creating your sign-in code…" else "Request a sign-in code to continue." } else "Waiting for you to sign in…", fontSize = (24 * scale).sp,
                modifier = Modifier.width(860.dp * scale), maxLines = 2,
                lineHeight = (35 * scale).sp, color = if (error == null) Palette.secondary else Palette.danger)
        }
        TvAction(Modifier.offset(1404.dp * scale, 880.dp * scale).size(420.dp * scale, 72.dp * scale), scale, primary = true, activate = email) {
            Icon(Lucide.Mail, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
            Text("Sign in with email instead", color = it, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
        }
    }
    if (notice) AlertDialog(onDismissRequest = { restore = true; notice = false }, title = { Text("Device pairing") },
        text = { Text("Creating pairing codes is not available on this connection yet.") },
        confirmButton = { TextButton(onClick = { restore = true; notice = false }) { Text("OK") } })
}
