package org.viptv.app.features.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvCalendarPreview(entry: CalendarEntry, action: TvCalendarAction?, scale: Float,
    requester: FocusRequester, left: FocusRequester?, up: FocusRequester, open: (Card) -> Unit, watch: (Card) -> Unit, contentY: androidx.compose.ui.unit.Dp = 118.dp * scale) {
    Column(Modifier.offset(1240.dp * scale, contentY).width(584.dp * scale), verticalArrangement = Arrangement.spacedBy(18.dp * scale)) {
        AsyncImage(entry.card.landscape ?: entry.card.poster, entry.card.title, Modifier.fillMaxWidth().aspectRatio(16f / 9)
            .clip(RoundedCornerShape(16.dp * scale)), contentScale = ContentScale.Crop)
        Text(entry.card.title, fontSize = (34 * scale).sp, lineHeight = (41 * scale).sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        entry.description?.let { Text(it, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, color = Palette.body,
            maxLines = 4, overflow = TextOverflow.Ellipsis) }
        TvAction(Modifier.width(300.dp * scale).height(72.dp * scale)
            .focusProperties { if (left != null) this.left = left; this.up = up; this.right = FocusRequester.Cancel }, scale, requester = requester,
            activate = { if (action == null) open(entry.card) else watch(action.target) }, arrangement = Arrangement.Start,
            contentPadding = PaddingValues(horizontal = 28.dp * scale)) {
            Icon(if (action == null) Lucide.Info else Lucide.Play, null, Modifier.size(26.dp * scale), it)
            Spacer(Modifier.width(14.dp * scale))
            Text(action?.label ?: "View details", color = it, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        action?.providerLabel?.let { Text(it, color = Palette.secondary, fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp) }
    }
}
