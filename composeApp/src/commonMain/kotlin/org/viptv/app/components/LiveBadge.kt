package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Star
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_star_filled
import org.viptv.app.theme.Palette

internal val LiveRed = Color(0xFFFF5A4E)

// The badge already says LIVE: a card's own LIVE tag in the row beside it would say it twice.
internal fun List<String>.withoutLiveTag() = filterNot { it.equals("LIVE", true) }

// The glass fill is what grounds the word: a solid badge in the same red reads under 4.5:1.
@Composable
internal fun LiveBadge(modifier: Modifier = Modifier, scale: Float = 1f, television: Boolean = false) {
    val text = if (television) 18 * scale else 11f
    val padding = if (television) 10.dp * scale else 8.dp
    val vertical = if (television) 4.dp * scale else 3.dp
    Box(modifier.background(Palette.background.copy(alpha = .72f), RoundedCornerShape(6.dp * scale))
        .padding(horizontal = padding, vertical = vertical), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (television) 8.dp * scale else 6.dp)) {
            Box(Modifier.size(if (television) 9.dp * scale else 6.dp).background(LiveRed, CircleShape))
            Text("LIVE", color = Palette.text, fontSize = text.sp, lineHeight = (text * 1.22f).sp,
                letterSpacing = if (television) (1.08f * scale).sp else 0.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun favouriteStarPainter(saved: Boolean): Painter =
    if (saved) painterResource(Res.drawable.ic_star_filled) else rememberVectorPainter(Lucide.Star)

@Composable
internal fun FavouriteStar(saved: Boolean, size: Dp, ink: Color, modifier: Modifier = Modifier) {
    Icon(favouriteStarPainter(saved), null, modifier.size(size), tint = if (saved) Palette.accent else ink)
}
