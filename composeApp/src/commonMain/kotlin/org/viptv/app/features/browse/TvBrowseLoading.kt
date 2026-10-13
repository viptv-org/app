package org.viptv.app.features.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.viptv.app.components.rememberTvLoadingShimmer

@Composable
internal fun TvBrowseLoading(scale: Float) {
    val shimmer = rememberTvLoadingShimmer(scale)
    LazyVerticalGrid(GridCells.Fixed(7), Modifier.width(1720.dp * scale).fillMaxHeight()
        .semantics { contentDescription = "Loading catalog" }, horizontalArrangement = Arrangement.spacedBy(16.dp * scale),
        verticalArrangement = Arrangement.spacedBy(36.dp * scale)) {
        items(14) { Box(Modifier.size(232.dp * scale, 348.dp * scale).clip(RoundedCornerShape(12.dp * scale)).then(shimmer)) }
    }
}
