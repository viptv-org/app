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

/** The server marks a title the profile has finished with this [Card.badges] value. */
internal const val WATCHED_BADGE = "Watched"

internal fun Card.isWatched(): Boolean = badges.any { it.equals(WATCHED_BADGE, ignoreCase = true) }

private val WatchedFill = Color(0xFF0B0B0C).copy(alpha = .78f)

/**
 * Canvas dictionary §A1: "Watched is a check in the corner, and the art is not
 * dimmed." Every board draws the same proportions — TV 34/20/10, desktop
 * 26/15/8, phone 22/13/6 (circle / check / top-right inset) — so one diameter
 * sets all three. The check is the board's 3-unit stroke on a 24-unit grid.
 *
 * [description] is null where the card already announces "Watched" itself
 * (the TV card clears its children's semantics).
 */
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

/**
 * For cards without a platform scale (phone and desktop grids, shelves): size the
 * check from the artwork itself, ~19 % of a 2:3 poster's width like every board,
 * bounded to the phone (22) and desktop (26) sizes so wide landscape art and
 * large desktop posters keep a small corner mark. Place it over the art with
 * `matchParentSize()`.
 */
@Composable
internal fun ArtWatchedBadge(modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val poster = minOf(maxWidth, maxHeight * (2f / 3f))
        WatchedBadge((poster * .19f).coerceIn(18.dp, 26.dp), Modifier.align(Alignment.TopEnd))
    }
}
