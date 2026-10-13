package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

@Composable
internal fun TvEmailSignIn(scale: Float, phone: () -> Unit, initialEmail: String = "", initialSuffix: Boolean = false,
    requestCode: ((String) -> Unit)? = null) {
    var email by remember { mutableStateOf(initialEmail) }
    var unavailable by remember { mutableStateOf(false) }
    val nextFocus = remember { FocusRequester() }
    var restore by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(unavailable, restore, windowFocused) {
        if (!unavailable && restore && windowFocused) { withFrameNanos { }; nextFocus.requestFocus(); restore = false }
    }
    val next = { if (email.isNotBlank()) { if (requestCode == null) unavailable = true else requestCode(email) }; Unit }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown) false else when (it.key) {
            Key.Backspace -> { email = email.dropLast(1); true }
            Key.Enter -> { next(); true }
            else -> false
        }
    }) {
        Column(Modifier.offset(96.dp * scale, 96.dp * scale), verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            Text("Step 1 of 2", fontSize = (22 * scale).sp, lineHeight = (31.9f * scale).sp,
                fontWeight = FontWeight.SemiBold, color = Color(0xFF8F8D89))
            Text("Enter your email", fontSize = (52 * scale).sp, lineHeight = (59.8f * scale).sp,
                letterSpacing = (-1.04f * scale).sp, fontWeight = FontWeight.Bold)
        }
        val shape = RoundedCornerShape(14.dp * scale)
        Row(Modifier.offset(96.dp * scale, 250.dp * scale).size(1130.dp * scale, 92.dp * scale)
            .background(Color.White.copy(alpha = .08f), shape).border(2.dp * scale, Palette.text, shape)
            .padding(horizontal = 28.dp * scale), verticalAlignment = Alignment.CenterVertically) {
            Text(email.ifEmpty { "name@email.com" }, Modifier.widthIn(max = 1068.dp * scale),
                fontSize = (36 * scale).sp, lineHeight = (44 * scale).sp, fontWeight = FontWeight.Medium,
                color = if (email.isEmpty()) Palette.secondary else Palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.padding(start = 3.dp * scale).size(3.dp * scale, 44.dp * scale).background(Palette.accent))
        }
        Box(Modifier.offset(96.dp * scale, 382.dp * scale).width(1130.dp * scale)) {
            TvEntryKeyboard(scale, initialSuffix) { email = (email + it).take(320) }
        }
        Column(Modifier.offset(1290.dp * scale, 382.dp * scale).width(534.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            TvAction(Modifier.fillMaxWidth().height(72.dp * scale), scale, requester = nextFocus, enabled = email.isNotBlank(), activate = next) {
                Icon(Lucide.ArrowRight, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
                Text("Next", color = it, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
            }
            TvAction(Modifier.fillMaxWidth().height(72.dp * scale), scale, activate = phone) {
                Icon(Lucide.Smartphone, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
                Text("Use your phone instead", color = it, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
            }
            Text("Typing with a remote is slow. Scanning the code on the previous screen is quicker.", Modifier.padding(top = 18.dp * scale),
                fontSize = (22 * scale).sp, lineHeight = (31.9f * scale).sp, color = Color(0xFF8F8D89))
            TvAction(Modifier.fillMaxWidth().height(72.dp * scale), scale, activate = { email = email.dropLast(1) }) {
                Icon(Lucide.Delete, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
                Text("Delete character", color = it, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (unavailable) AlertDialog(onDismissRequest = { restore = true; unavailable = false }, title = { Text("Email sign-in") },
        text = { Text("Email codes are not available on this connection yet.") },
        confirmButton = { TextButton(onClick = { restore = true; unavailable = false }) { Text("OK") } })
}
