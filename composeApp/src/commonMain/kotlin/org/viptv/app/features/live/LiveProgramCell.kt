package org.viptv.app.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette

@Composable
internal fun LiveProgramCell(program: LiveProgram, now: Int?, start: Int, television: Boolean, scale: Float,
    modifier: Modifier, focus: () -> Unit, activate: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val foreground = if (focused) { if (television) Color(0xFF111113) else Palette.background } else Palette.text
    Box(modifier.clip(RoundedCornerShape((if (television) 10 else 8).dp * scale))
        .background(if (focused) Palette.text else if (television) Color.White.copy(alpha = .09f) else Palette.raised)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) focus() }.clickable(onClick = activate)) {
        if (television && now != null && now in program.start until program.end) Box(Modifier.fillMaxHeight()
            .fillMaxWidth(liveProgress(program, now)).background(if (focused) Color(0xFF111113).copy(alpha = .08f) else Color.White.copy(alpha = .05f)))
        Column(Modifier.fillMaxSize().padding(horizontal = (if (television) 18 else 12).dp * scale, vertical = 12.dp * scale),
            verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (television && program.start < start) {
                    Icon(Lucide.ChevronLeft, "Started earlier", Modifier.size(18.dp * scale), foreground)
                    Spacer(Modifier.width(6.dp * scale))
                }
                Text(program.title, fontSize = (if (television) 22 * scale else 12f).sp, fontWeight = FontWeight.SemiBold,
                    color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = (if (television) 26 * scale else 16f).sp)
            }
            if (!television) Text(liveTime(program.start), fontSize = 10.sp, lineHeight = 14.sp, color = Palette.secondary)
        }
    }
}
