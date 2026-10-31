package org.viptv.app.features.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.viptv.app.components.responseSpec
import org.viptv.app.theme.Palette

@Composable
internal fun HeroIndicators(count: Int, selected: Int, progress: HeroProgress, modifier: Modifier = Modifier, scale: Float = 1f,
    animated: Boolean = true) {
    if (count < 2) return
    val dot = 16.dp * scale
    val pill = 48.dp * scale
    val fillPixels = with(LocalDensity.current) { pill.roundToPx() }
    SideEffect { progress.steps = fillPixels }
    val active = remember(scale) { Animatable(pill.value) }
    val response = responseSpec<Float>()
    var grown by remember { mutableIntStateOf(selected) }
    LaunchedEffect(selected, animated) {
        if (!animated || grown == selected) { grown = selected; return@LaunchedEffect }
        // Snap before claiming the new dot: claiming first lets one frame draw the incoming dot at the outgoing dot's full pill width.
        active.snapTo(dot.value)
        grown = selected
        active.animateTo(pill.value, response)
    }
    Row(modifier.semantics { contentDescription = "Featured ${selected + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        repeat(count) { index ->
            val width = when {
                index != selected -> dot
                !animated -> pill
                grown == selected -> active.value.dp
                else -> dot
            }
            Canvas(Modifier.width(width).height(4.dp * scale)) {
                val radius = CornerRadius(2.dp.toPx() * scale)
                drawRoundRect(Palette.text.copy(alpha = .35f), cornerRadius = radius)
                if (index == selected) drawRoundRect(Palette.text,
                    size = Size(size.width * progress.fraction.coerceIn(0f, 1f), size.height), cornerRadius = radius)
            }
        }
    }
}
