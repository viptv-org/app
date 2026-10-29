package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.connection.DevicePairingDisplay
import org.viptv.app.theme.Palette
import org.viptv.app.components.Button
import org.viptv.app.components.TextButton

@Composable
internal fun PairingBrowser(display: DevicePairingDisplay?, busy: Boolean, error: String?, mobile: Boolean,
    back: () -> Unit, refresh: () -> Unit, changeServer: () -> Unit, autoOpen: Boolean = true) {
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var opened by remember { mutableStateOf<String?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    fun open() { display?.let { session ->
        runCatching { uri.openUri(session.qrUri) }.onSuccess { opened = session.qrUri; openError = null }
            .onFailure { openError = "Could not open your browser. Copy the link to continue." }
    } }
    LaunchedEffect(display?.qrUri) { if (autoOpen && display != null) open() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (busy) CircularProgressIndicator(Modifier.size(32.dp), color = Palette.secondary, strokeWidth = 3.dp)
        Spacer(Modifier.height(if (mobile) 20.dp else 14.dp))
        Text("Finish signing in in your browser", color = Palette.text,
            fontSize = if (mobile) 24.sp else 21.sp, fontWeight = FontWeight.Bold,
            letterSpacing = if (mobile) (-.48).sp else (-.42).sp,
            lineHeight = if (mobile) 27.6.sp else 24.15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(if (mobile) 16.dp else 10.dp))
        val address = display?.verificationUri?.removePrefix("https://") ?: "your server"
        Text(if (display == null) "Create a sign-in link, then approve this device in your browser."
            else if (opened == display.qrUri) "We opened $address. This window connects by itself when you’re done."
            else "Approve this device at $address. This window connects by itself when you’re done.",
            color = Palette.secondary, fontSize = if (mobile) 15.sp else 14.sp, letterSpacing = 0.sp,
            lineHeight = if (mobile) 21.75.sp else 20.3.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = ::open, enabled = display != null, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303033), contentColor = Palette.text)) {
                Icon(Lucide.ExternalLink, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Open browser again", fontSize = 13.sp)
            }
            Button(onClick = { display?.let { clipboard.setText(AnnotatedString(it.qrUri)) } }, enabled = display != null,
                shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303033), contentColor = Palette.text)) {
                Icon(Lucide.Link, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Copy link", fontSize = 13.sp)
            }
        }
        (openError ?: error)?.let { Text(it, Modifier.padding(top = 12.dp), color = Palette.danger, fontSize = 13.sp) }
        if (display == null && !busy) TextButton(refresh) { Text("Create a new code") }
        Spacer(Modifier.height(12.dp))
        PairingBackLink(back, mobile)
        TextButton(changeServer) { Text("Use another server", color = Palette.secondary, fontSize = 12.sp) }
    }
}
