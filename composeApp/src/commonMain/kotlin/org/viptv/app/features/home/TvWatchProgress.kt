package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

@Composable
internal fun TvWatchProgress(progress: Double, modifier: Modifier, scale: Float, rounded: Boolean = false) {
    val fraction = if (progress.isFinite()) progress.toFloat().coerceIn(0f, 1f) else 0f
    Box(modifier.then(if (rounded) Modifier.clip(RoundedCornerShape(2.dp * scale)) else Modifier)
        .background(Color.White.copy(alpha = .25f))
        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Palette.accent))
    }
}
