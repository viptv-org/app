package org.viptv.app.features.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.viptv.app.components.TvLoadingIndicator
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun BoxScope.TvPlayerBuffer(title: Card?, extras: TvPlaybackExtras, scale: Float) {
    val opacity = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(200); opacity.animateTo(1f, tween(300, easing = LinearOutSlowInEasing)) }
    if (opacity.value == 0f) return
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = opacity.value }.background(Color.Black.copy(alpha = .55f)))
    Column(Modifier.align(Alignment.Center).graphicsLayer { alpha = opacity.value }, horizontalAlignment = Alignment.CenterHorizontally) {
        TvLoadingIndicator(Modifier.size(72.dp * scale), scale)
        Spacer(Modifier.height(26.dp * scale))
        Text("Starting playback", color = Palette.text, fontSize = (30 * scale).sp,
            lineHeight = (43.5f * scale).sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp * scale))
        val sourcePart = listOfNotNull(extras.sourceLabel, extras.sourceQuality).joinToString(" ").takeIf { it.isNotEmpty() }
        val parts = listOfNotNull(title?.title, title?.identity?.episode?.let { "S${it.season ?: 1} E${it.episode}" }, sourcePart)
        Text(extras.bufferLabel ?: parts.joinToString(" · "),
            fontSize = (24 * scale).sp, lineHeight = (34.8f * scale).sp, color = Palette.secondary)
    }
}
