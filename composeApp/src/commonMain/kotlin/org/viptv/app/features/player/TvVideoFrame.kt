package org.viptv.app.features.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun rememberTvVideoFrame(showNext: Boolean, scale: Float): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(showNext) {
        progress.animateTo(if (showNext) 1f else 0f,
            tween(600, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)))
    }
    val fraction = progress.value
    return Modifier.offset(96.dp * scale * fraction, 96.dp * scale * fraction)
        .size((1920 - 1040 * fraction).dp * scale, (1080 - 585 * fraction).dp * scale)
        .clip(RoundedCornerShape(12.dp * scale * fraction))
}
