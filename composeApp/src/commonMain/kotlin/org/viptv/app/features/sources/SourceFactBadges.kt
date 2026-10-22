package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/** Visual style of the release-fact badges: the quality badge's 0.16 fill of the row foreground. */
internal class SourceBadgeStyle(val fontSize: TextUnit, val lineHeight: TextUnit, val radius: Dp, val horizontal: Dp, val vertical: Dp, val gap: Dp)

/**
 * A source row's title line: the provider/release label, then release-fact
 * badges (SRC-BEST-MATCH-001), then an optional trailing tag such as Best match.
 * The label keeps the layout it has without badges; badges flow into the room
 * left on its last line, and trailing badges that do not fit are dropped whole,
 * so a row never grows, wraps a badge or overflows.
 */
@Composable
internal fun SourceTitleLine(label: String, labelStyle: TextStyle, foreground: Color, badges: List<String>, badgeStyle: SourceBadgeStyle,
    modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val style = LocalTextStyle.current.merge(labelStyle).copy(color = foreground)
    val measurer = rememberTextMeasurer()
    Layout({
        Text(label, Modifier.layoutId("label"), style = style)
        badges.forEach { badge ->
            Text(badge, Modifier.layoutId("badge").background(foreground.copy(alpha = 0.16f), RoundedCornerShape(badgeStyle.radius))
                .padding(horizontal = badgeStyle.horizontal, vertical = badgeStyle.vertical),
                color = foreground, fontSize = badgeStyle.fontSize, lineHeight = badgeStyle.lineHeight, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false)
        }
        trailing?.let { content -> androidx.compose.foundation.layout.Box(Modifier.layoutId("trailing")) { content() } }
    }, modifier) { measurables, constraints ->
        val gap = badgeStyle.gap.roundToPx()
        val loose = Constraints()
        val tail = measurables.firstOrNull { it.layoutId == "trailing" }?.measure(loose)
        val bounded = constraints.hasBoundedWidth
        val room = if (bounded) (constraints.maxWidth - (tail?.let { it.width + gap } ?: 0)).coerceAtLeast(0) else Constraints.Infinity
        // The label fills its width, as it did as a weighted Row child; badges use the measured last-line end.
        val text = measurables.first { it.layoutId == "label" }.measure(if (bounded) Constraints.fixedWidth(room) else loose)
        val lines = measurer.measure(label, style, constraints = Constraints(maxWidth = room.coerceAtLeast(1)))
        val last = (lines.lineCount - 1).coerceAtLeast(0)
        val lastRight = if (label.isEmpty()) 0 else kotlin.math.ceil(lines.getLineRight(last)).toInt()
        val lastTop = lines.getLineTop(last).toInt(); val lastBottom = lines.getLineBottom(last).toInt()
        val badgePlaceables = measurables.filter { it.layoutId == "badge" }.map { it.measure(loose) }
        val start = if (lastRight == 0) 0 else lastRight + gap
        val shown = if (!bounded) badgePlaceables.indices.toList()
            else sourceFactBadgesThatFit(badgePlaceables.map { it.width }, gap, (room - start).coerceAtLeast(0))
        val used = shown.sumOf { badgePlaceables[it].width } + gap * (shown.size - 1).coerceAtLeast(0)
        val firstLine = lines.getLineBottom(0).toInt() - lines.getLineTop(0).toInt()
        val height = maxOf(text.height, tail?.height ?: 0)
        val width = if (bounded) constraints.maxWidth else maxOf(text.width, start + used) + (tail?.let { it.width + gap } ?: 0)
        layout(width, height) {
            text.placeRelative(0, 0)
            var x = start
            shown.forEach { index ->
                val badge = badgePlaceables[index]
                badge.placeRelative(x, lastTop + (lastBottom - lastTop - badge.height) / 2); x += badge.width + gap
            }
            tail?.let { it.placeRelative(width - it.width, ((firstLine - it.height) / 2).coerceAtLeast(0)) }
        }
    }
}

/**
 * The leading badges whose widths plus gaps fit in [room]; the first badge that
 * overflows ends the run, so a later narrower badge never jumps ahead of a dropped one.
 */
internal fun sourceFactBadgesThatFit(widths: List<Int>, gap: Int, room: Int): List<Int> {
    var used = 0
    val shown = mutableListOf<Int>()
    for ((index, width) in widths.withIndex()) {
        val next = used + width + if (shown.isEmpty()) 0 else gap
        if (next > room) break
        used = next; shown += index
    }
    return shown
}
