package org.viptv.app.features.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import com.composables.icons.lucide.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.viptv.app.theme.Palette

@Composable
internal fun CalendarRow(entry: CalendarEntry, phone: Boolean, television: Boolean, scale: Float,
    focus: () -> Unit, open: () -> Unit, selected: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val color = if ((focused || selected) && television) Palette.background else Palette.text
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (phone) 16.dp else 18.dp * scale))
        .background(if ((focused || selected) && television) Palette.text else Palette.surface)
        .onFocusChanged { focused = it.isFocused; if (focused) focus() }.clickable(onClick = open)
        .heightIn(min = if (phone) 100.dp else 0.dp).padding(horizontal = if (phone) 12.dp else 28.dp * scale, vertical = if (phone) 10.dp else 12.dp * scale),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 28.dp * scale)) {
        Text(entry.time, Modifier.width(if (phone) 62.dp else 120.dp * scale), fontSize = (if (phone) 12f else 26 * scale).sp,
            fontWeight = FontWeight.SemiBold, color = color)
        val art = entry.card.poster ?: entry.card.landscape
        if (art != null) AsyncImage(art, entry.card.title, Modifier.width(if (phone) 36.dp else 72.dp * scale).aspectRatio(2f / 3)
            .clip(RoundedCornerShape(6.dp * scale)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (phone) 4.dp else 8.dp * scale)) {
            Text(entry.card.title, fontSize = (if (phone) 14f else 28 * scale).sp, fontWeight = FontWeight.SemiBold, color = color,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.subtitle?.let { Text(it, fontSize = (if (phone) 12f else 24 * scale).sp, color = color.copy(alpha = 0.7f), maxLines = 1) }
            if (phone) entry.status?.let { status ->
                Surface(shape = RoundedCornerShape(6.dp), color = if (entry.aired) Palette.raised else Palette.accent.copy(alpha = 0.14f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(if (entry.aired) Lucide.Check else Lucide.Timer, null, Modifier.size(12.dp), tint = if (entry.aired) Palette.text else Palette.accent)
                        Text(status, fontSize = 11.sp, lineHeight = 14.sp, color = if (entry.aired) Palette.text else Palette.accent)
                    }
                }
            }
        }
        if (!phone) entry.status?.let { Text(it, fontSize = (24 * scale).sp,
            color = if (entry.aired) color.copy(alpha = 0.7f) else Palette.accent) }
    }
}
