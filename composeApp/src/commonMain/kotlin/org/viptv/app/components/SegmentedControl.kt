package org.viptv.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

private val SegmentInset = 20.dp

// One mode switch inside a page (components.md §2): a surface-1 track whose selected segment is the off-white fill.
@Composable
internal fun SegmentedControl(labels: List<String>, selected: Int, select: (Int) -> Unit,
    modifier: Modifier = Modifier, down: FocusRequester? = null, up: FocusRequester? = null,
    // The caller's handle on the selected segment: the row a remote lands back on when it returns to the switch.
    entry: FocusRequester? = null) {
    BoxWithConstraints(modifier) {
        val tv = LocalTvFocusAnchoring.current
        val scale = if (tv) LocalTvScale.current else 1f
        val full = !tv && maxWidth < 640.dp
        val height = when { tv -> 56.dp * scale; full -> 44.dp; else -> 40.dp }
        val inset = 4.dp * scale
        val textSize = if (tv) (22 * scale).sp else 14.sp
        val insetDp = if (tv) 28.dp * scale else SegmentInset
        // A full-width track splits by label, so the longer name is never cut to fit half a phone.
        val weights = if (!full) emptyList() else {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val pad = with(density) { insetDp.roundToPx() } * 2
            val style = TextStyle(fontSize = textSize, fontWeight = FontWeight.SemiBold)
            val widths = labels.map { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width + pad }
            val total = widths.sum().coerceAtLeast(1)
            widths.map { it.toFloat() / total }
        }
        val own = remember(labels.size) { List(labels.size) { FocusRequester() } }
        Row(Modifier.then(if (full) Modifier.fillMaxWidth() else Modifier).height(height)
            .background(Palette.surface, RoundedCornerShape(height / 2)).padding(inset)
            .then(if (tv) Modifier.focusGroup() else Modifier), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                Segment(label, index == selected, tv, scale, weights.getOrNull(index), insetDp, height - inset * 2,
                    if (index == selected) entry ?: own[index] else own[index], down, up) { select(index) }
            }
        }
    }
}

@Composable
private fun RowScope.Segment(label: String, selected: Boolean, tv: Boolean, scale: Float, weight: Float?, inset: Dp,
    height: Dp, own: FocusRequester?, down: FocusRequester?, up: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val fill by animateColorAsState(if (selected || focused) Palette.text else Color.Transparent,
        tween(120), label = "Segment fill")
    val ink by animateColorAsState(if (selected || focused) Palette.background else Palette.secondary,
        tween(120), label = "Segment ink")
    Row(Modifier.then(if (weight != null) Modifier.weight(weight) else Modifier).height(height)
        .then(if (tv && own != null) Modifier.focusRequester(own)
            .then(if (down == null && up == null) Modifier else Modifier.focusProperties {
                down?.let { this.down = it }
                up?.let { this.up = it }
            })
            .onFocusChanged { focused = it.isFocused }
            .tvFocusOutline(focused, height / 2, scale) else Modifier)
        .pointerSelectable(selected, role = Role.Tab, shape = shape, onClick = onClick)
        .clip(shape).background(fill).padding(horizontal = inset),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(label, color = ink, fontSize = if (tv) (22 * scale).sp else 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false)
    }
}