package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.ServerMessages
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette
import org.viptv.app.components.TextButton

internal data class TvEmailCodeChallenge(val status: String, val initialCode: String = "", val sessionId: String)

@Composable
internal fun TvEmailCode(scale: Float, challenge: TvEmailCodeChallenge,
    verify: ((String) -> Unit)? = null, resend: (() -> Unit)? = null, busy: Boolean = false) {
    var code by remember(challenge.sessionId) { mutableStateOf(challenge.initialCode.filter(Char::isDigit).take(6)) }
    var notice by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }; val newCode = remember { FocusRequester() }
    var noticeTarget by remember { mutableStateOf(first) }
    var restore by remember { mutableStateOf<FocusRequester?>(null) }; val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(Unit) { first.requestFocus() }
    LaunchedEffect(notice, restore, windowFocused) {
        if (notice == null && windowFocused) restore?.let { withFrameNanos { }; it.requestFocus(); restore = null }
    }
    val submit = { if (code.length == 6) { if (verify == null) { noticeTarget = first; notice = "Code verification is not available on this connection yet." } else verify(code) }; Unit }
    val digit: (String) -> Unit = { value -> if (code.length < 6) { code += value; if (code.length == 6 && verify != null) verify(code) } }
    val numbers = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    Box(Modifier.fillMaxSize().onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown || busy) false else when {
            it.key in numbers -> { digit(numbers.indexOf(it.key).toString()); true }
            it.key == Key.Backspace -> { code = code.dropLast(1); true }
            it.key == Key.Enter -> { submit(); true }
            else -> false
        }
    }) {
        Column(Modifier.offset(96.dp * scale, 96.dp * scale), verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            Text("Email me a code", fontSize = (24 * scale).sp, lineHeight = (34.8f * scale).sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8F8D89))
            Text("Enter the code we emailed you", fontSize = (52 * scale).sp, lineHeight = (59.8f * scale).sp, letterSpacing = (-1.04f * scale).sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.offset(96.dp * scale, 260.dp * scale), horizontalArrangement = Arrangement.spacedBy(18.dp * scale)) {
            repeat(6) { index ->
                val shape = RoundedCornerShape(16.dp * scale)
                Box(Modifier.size(104.dp * scale, 124.dp * scale).background(Color.White.copy(alpha = .08f), shape)
                    .border((if (index == code.length) 2 else 1).dp * scale, if (index == code.length) Palette.text else Color.White.copy(alpha = .14f), shape),
                    contentAlignment = Alignment.Center) {
                    Text(code.getOrNull(index)?.toString().orEmpty(), fontSize = (56 * scale).sp, lineHeight = (68 * scale).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(ServerMessages.human(challenge.status, ServerMessages.EMAIL_STATUS), Modifier.offset(96.dp * scale, 410.dp * scale), fontSize = (24 * scale).sp, lineHeight = (34.8f * scale).sp, color = Palette.secondary)
        Column(Modifier.offset(1180.dp * scale, 250.dp * scale), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "Erase")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) { row.forEach { label ->
                    if (label.isEmpty()) Spacer(Modifier.size(140.dp * scale, 76.dp * scale))
                    else TvPinKey(label, scale, !busy, if (label == "5") first else null, width = 140, numberSize = 36) {
                        if (label == "Erase") code = code.dropLast(1) else digit(label)
                    }
                } }
            }
        }
        TvAction(Modifier.offset(96.dp * scale, 500.dp * scale).size(360.dp * scale, 72.dp * scale), scale, requester = newCode, enabled = !busy, activate = {
            if (resend == null) { noticeTarget = newCode; notice = "Resending email codes is not available on this connection yet." } else resend()
        }) {
            Icon(Lucide.RotateCcw, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
            Text("Send a new code", color = it, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
        }
    }
    if (notice != null) AlertDialog(onDismissRequest = { restore = noticeTarget; notice = null }, title = { Text("Email sign-in") },
        text = { Text(requireNotNull(notice)) }, confirmButton = { TextButton(onClick = { restore = noticeTarget; notice = null }) { Text("OK") } })
}
