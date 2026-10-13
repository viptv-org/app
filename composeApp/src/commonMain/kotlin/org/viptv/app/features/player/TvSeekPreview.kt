package org.viptv.app.features.player

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import coil3.compose.AsyncImage

@Composable
internal fun TvSeekPreview(point: Long, image: String?, fraction: Float, availableWidth: Dp, scale: Float) {
    val width = (if (image != null) 406 else 140).dp * scale
    val left = (availableWidth * fraction - width / 2).coerceIn(0.dp, availableWidth - width)
    Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
        .offset(x = left, y = (if (image != null) -273 else -44).dp * scale).width(width),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
        if (image != null) Box(Modifier.size(406.dp * scale, 231.dp * scale)
            .dropShadow(RoundedCornerShape(15.dp * scale), Shadow(radius = 60.dp * scale,
                color = Color.Black.copy(alpha = .6f), offset = DpOffset(0.dp, 24.dp * scale)))
            .border(3.dp * scale, Color.White, RoundedCornerShape(15.dp * scale)).padding(3.dp * scale)) {
            AsyncImage(image, "Seek preview", Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp * scale)), contentScale = ContentScale.Crop)
        }
        Text(playbackTime(point), Modifier.width(120.dp * scale), fontSize = (26 * scale).sp,
            lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
    }
}
