package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

@Composable
internal fun TvHomeLoading(scale: Float) {
    val shimmer = rememberTvLoadingShimmer(scale)
    Box(Modifier.fillMaxSize().background(Palette.background)) {
        TvSkeleton(Modifier.offset(96.dp * scale, 300.dp * scale).size(360.dp * scale, 150.dp * scale), 12f * scale, shimmer)
        TvSkeleton(Modifier.offset(96.dp * scale, 494.dp * scale).size(520.dp * scale, 30.dp * scale), 6f * scale, shimmer)
        TvSkeleton(Modifier.offset(96.dp * scale, 548.dp * scale).size(760.dp * scale, 26.dp * scale), 6f * scale, shimmer)
        TvSkeleton(Modifier.offset(96.dp * scale, 586.dp * scale).size(600.dp * scale, 26.dp * scale), 6f * scale, shimmer)
        TvSkeleton(Modifier.offset(96.dp * scale, 648.dp * scale).size(220.dp * scale, 72.dp * scale), 12f * scale, shimmer)
        TvSkeleton(Modifier.offset(336.dp * scale, 648.dp * scale).size(72.dp * scale), 36f * scale, shimmer)
        TvSkeleton(Modifier.offset(428.dp * scale, 648.dp * scale).size(72.dp * scale), 36f * scale, shimmer)
        TvSkeleton(Modifier.offset(96.dp * scale, 828.dp * scale).size(300.dp * scale, 32.dp * scale), 6f * scale, shimmer)
        Row(Modifier.offset(96.dp * scale, 876.dp * scale).horizontalScroll(rememberScrollState(), enabled = false), horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
            repeat(5) { TvHomeSkeletonCard(scale, false, shimmer) }
        }
    }
}

@Composable
internal fun TvHomeSkeletonCard(scale: Float, portrait: Boolean, shimmer: Modifier = rememberTvLoadingShimmer(scale)) {
    TvSkeleton(Modifier.width((if (portrait) 232 else 384).dp * scale).aspectRatio(if (portrait) 2f / 3 else 16f / 9), 10f * scale, shimmer)
}

@Composable
private fun TvSkeleton(modifier: Modifier, radius: Float, shimmer: Modifier) {
    Box(modifier.clip(RoundedCornerShape(radius.dp)).then(shimmer))
}
