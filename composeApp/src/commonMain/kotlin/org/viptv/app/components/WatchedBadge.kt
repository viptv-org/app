package org.viptv.app.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

internal const val WATCHED_BADGE = "Watched"

internal fun Card.isWatched(): Boolean = badges.any { it.equals(WATCHED_BADGE, ignoreCase = true) }

private val WatchedFill = Color(0xFF0B0B0C).copy(alpha = .78f)

@Composable
internal fun WatchedBadge(diameter: Dp, modifier: Modifier = Modifier, description: String? = WATCHED_BADGE) {
    Box(modifier.padding(diameter * (10f / 34f)).size(diameter)
        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
        .background(WatchedFill, CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter * (20f / 34f))) {
            val unit = size.width / 24f
            val check = Path().apply { moveTo(20 * unit, 6 * unit); lineTo(9 * unit, 17 * unit); lineTo(4 * unit, 12 * unit) }
            drawPath(check, Palette.text, style = Stroke(3 * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
internal fun ArtWatchedBadge(modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val poster = minOf(maxWidth, maxHeight * (2f / 3f))
        WatchedBadge((poster * .19f).coerceIn(18.dp, 26.dp), Modifier.align(Alignment.TopEnd))
    }
}
