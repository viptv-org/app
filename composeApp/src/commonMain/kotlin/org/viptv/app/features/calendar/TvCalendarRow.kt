package org.viptv.app.features.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

@Composable
internal fun TvCalendarRow(entry: CalendarEntry, scale: Float, selected: Boolean, first: FocusRequester?, up: FocusRequester?,
    focus: () -> Unit, open: () -> Unit) {
    TvAction(Modifier.fillMaxWidth().height(132.dp * scale).onFocusChanged { if (it.isFocused) focus() }
        .focusProperties { if (up != null) this.up = up }, scale, primary = selected, requester = first, activate = open,
        background = Color.White.copy(alpha = .06f), cornerRadius = 18.dp * scale, arrangement = Arrangement.Start,
        contentPadding = PaddingValues(horizontal = 28.dp * scale)) { color ->
        val secondary = if (color == Palette.background) Color(0xFF4A4945) else Palette.secondary
        Text(entry.time, Modifier.width(120.dp * scale), color = color, fontSize = (28 * scale).sp, lineHeight = (34 * scale).sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(28.dp * scale))
        AsyncImage(entry.card.poster ?: entry.card.landscape, entry.card.title, Modifier.size(72.dp * scale, 108.dp * scale)
            .clip(RoundedCornerShape(8.dp * scale)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(28.dp * scale))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
            Text(entry.card.title, color = color, fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.subtitle?.let { Text(it, color = secondary, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        entry.status?.let { Text(it, Modifier.padding(start = 16.dp * scale), color = if (entry.aired) secondary else Palette.accent,
            fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.Bold) }
    }
}
