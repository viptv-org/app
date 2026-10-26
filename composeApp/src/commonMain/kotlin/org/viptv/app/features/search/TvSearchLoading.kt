package org.viptv.app.features.search

import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.viptv.app.components.rememberTvLoadingShimmer

@Composable
internal fun TvSearchLoading(scale: Float) {
    val shimmer = rememberTvLoadingShimmer(scale)
    Column(Modifier.semantics { contentDescription = "Loading search results" }, verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
        Box(Modifier.height(40.dp * scale)) {
            Box(Modifier.size(300.dp * scale, 36.dp * scale).clip(RoundedCornerShape(6.dp * scale)).then(shimmer))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            items(5) { Box(Modifier.size(212.dp * scale, 318.dp * scale).clip(RoundedCornerShape(12.dp * scale)).then(shimmer)) }
        }
    }
}
