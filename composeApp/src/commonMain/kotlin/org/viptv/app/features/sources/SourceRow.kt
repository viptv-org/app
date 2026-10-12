package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.SourceCandidate

@Composable
internal fun SourceRow(source: SourceCandidate, television: Boolean, choose: (SourceCandidate) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val foreground = if (focused) Palette.background else Palette.text
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (television) 14.dp else 0.dp))
        .background(if (focused) Palette.text else if (television) Palette.raised else Palette.surface)
        .onFocusChanged { focused = it.isFocused }.clickable { choose(source) }
        .padding(horizontal = if (television) 28.dp else 20.dp, vertical = if (television) 24.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(source.label, fontSize = if (television) 24.sp else 14.sp, fontWeight = FontWeight.SemiBold, color = foreground)
            source.detail?.let { Text(it, fontSize = if (television) 18.sp else 12.sp,
                color = foreground.copy(alpha = 0.7f), maxLines = 3) }
        }
        Icon(Lucide.ChevronRight, null, Modifier.size(if (television) 24.dp else 18.dp), tint = foreground)
    }
}
