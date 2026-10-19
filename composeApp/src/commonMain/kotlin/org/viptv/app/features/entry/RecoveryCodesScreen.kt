package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import org.viptv.app.components.Brand
import org.viptv.app.connection.RecoveryKit
import org.viptv.app.theme.Palette

@Composable
internal fun RecoveryCodesScreen(kit: RecoveryKit?, busy: Boolean, error: String?, proceed: () -> Unit, signIn: () -> Unit,
    television: Boolean = false) {
    var saved by remember(kit) { mutableStateOf(false) }
    if (television) { TvRecoveryCodes(kit, busy, error, saved, { saved = it }, proceed, signIn); return }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Brand()
            Text("Save your recovery code", style = MaterialTheme.typography.headlineLarge)
            Text(if (kit?.recovered == true) "Your password is updated and your previous recovery code no longer works. Save the replacement below."
                else "Your account is created. Keep this code somewhere private so you can recover access if you lose your password.", color = Palette.secondary)
            kit?.codes?.forEach { code ->
                Surface(color = Palette.surface, shape = MaterialTheme.shapes.medium) {
                    SelectionContainer { Text(code, Modifier.fillMaxWidth().padding(20.dp), fontFamily = FontFamily.Monospace) }
                }
            }
            Text("This code is shown only now. VIPTV does not save a copy on this device.", color = Palette.secondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(saved, { saved = it }, enabled = !busy)
                Text("I saved my recovery code")
            }
            error?.let { Text(it, color = Palette.danger) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(proceed, enabled = saved && kit != null && !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (error == null) "Continue" else "Retry sign-in")
            }
            if (error != null) TextButton(signIn, enabled = saved && !busy, modifier = Modifier.fillMaxWidth()) { Text("Return to sign in") }
        }
    }
}


/**
 * TVU-15: no board exists for recovery codes on TV. This keeps the Tv3SignIn
 * idiom (96 px inset, 44/700 heading, 24 #B6B4AF body, bordered code card) and
 * gives the remote one focused Done action; the checkbox is replaced by a real
 * "Saved it" step so the action is reachable with the D-pad.
 */
@Composable
private fun TvRecoveryCodes(kit: RecoveryKit?, busy: Boolean, error: String?, saved: Boolean,
    setSaved: (Boolean) -> Unit, proceed: () -> Unit, signIn: () -> Unit) {
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        repeat(30) { if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect; androidx.compose.runtime.withFrameNanos { } }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 96.dp, top = 96.dp, end = 96.dp)) {
        val scale = maxWidth / 1920.dp * 1f
        Column(Modifier.widthIn(max = 1100.dp * scale), verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
            Text("Save your recovery code", fontSize = (44 * scale).sp, lineHeight = (52 * scale).sp,
                letterSpacing = (-.88f * scale).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(if (kit?.recovered == true) "Your password is updated and your previous recovery code no longer works. Save the replacement below."
                else "Your account is created. Keep this code somewhere private so you can recover access if you lose your password.",
                color = Palette.secondary, fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp)
            kit?.codes?.forEach { code ->
                Box(Modifier.fillMaxWidth().background(Palette.surface, androidx.compose.foundation.shape.RoundedCornerShape(14.dp * scale))
                    .border(1.dp * scale, Palette.text.copy(alpha = .14f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp * scale))
                    .padding(horizontal = 28.dp * scale, vertical = 24.dp * scale)) {
                    Text(code, fontFamily = FontFamily.Monospace, fontSize = (30 * scale).sp, lineHeight = (38 * scale).sp)
                }
            }
            Text("This code is shown only now. VIPTV does not save a copy on this device.", color = Palette.secondary, fontSize = (22 * scale).sp)
            error?.let { Text(it, color = Palette.danger, fontSize = (22 * scale).sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                org.viptv.app.components.TvAction(Modifier.height(72.dp * scale).width(300.dp * scale), scale,
                    primary = true, requester = first, enabled = !busy, activate = {
                        if (saved) proceed() else setSaved(true)
                    }) { tint ->
                    Text(if (!saved) "I saved it" else if (error == null) "Continue" else "Retry sign-in",
                        color = tint, fontSize = (26 * scale).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                if (error != null) org.viptv.app.components.TvAction(Modifier.height(72.dp * scale).width(300.dp * scale), scale,
                    enabled = saved && !busy, activate = signIn) { tint ->
                    Text("Return to sign in", color = tint, fontSize = (26 * scale).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
    }
}
