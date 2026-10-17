package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import org.viptv.app.components.Brand
import org.viptv.app.components.TvAction
import org.viptv.app.resources.Res
import org.viptv.app.theme.Palette

@Composable
internal fun TvWelcome(scale: Float, signIn: () -> Unit, local: (() -> Unit)?, error: String? = null) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    TvWelcomeBackdrop(scale)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().offset(y = 336.dp * scale), contentAlignment = Alignment.Center) {
            Brand(80.dp * scale, (92 * scale).sp, 20.dp * scale, (-3.68f * scale).sp, (92 * scale).sp)
        }
        Box(Modifier.fillMaxWidth().offset(y = 474.dp * scale), contentAlignment = Alignment.Center) {
            Text("Your shows, channels and progress. All in one place.", color = Palette.text, fontSize = (34 * scale).sp, lineHeight = (42 * scale).sp)
        }
        Row(Modifier.fillMaxWidth().offset(y = 584.dp * scale), horizontalArrangement = Arrangement.spacedBy(20.dp * scale, Alignment.CenterHorizontally)) {
            TvAction(Modifier.width(360.dp * scale).height(72.dp * scale), scale, primary = true, requester = first, activate = signIn) {
                Text("Sign in", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            }
            if (local != null) TvAction(Modifier.width(360.dp * scale).height(72.dp * scale), scale, activate = local) {
                Text("Use without an account", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
        error?.let { message ->
            Box(Modifier.fillMaxWidth().offset(y = 720.dp * scale).padding(horizontal = 96.dp * scale), contentAlignment = Alignment.Center) {
                Text(message, color = Palette.danger, fontSize = (26 * scale).sp, lineHeight = (36 * scale).sp,
                    textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (local != null) Box(Modifier.fillMaxWidth().offset(y = 960.dp * scale), contentAlignment = Alignment.Center) {
            Text("Without an account, everything stays on this TV.", color = Palette.secondary, fontSize = (22 * scale).sp)
        }
    }
}

@Composable
private fun TvWelcomeBackdrop(scale: Float) {
    Box(Modifier.fillMaxSize().clipToBounds()) {
        val posters = TV_WELCOME_POSTERS
        val drift by rememberInfiniteTransition(label = "Welcome artwork").animateFloat(0f, 1f,
            infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Reverse), label = "Poster drift")
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = -500.dp.toPx() * scale * drift }) {
        posters.forEachIndexed { index, poster ->
            AsyncImage(Res.getUri("files/entry/tv-poster-$poster.jpg"), null,
                Modifier.offset(x = (-60 + index % 10 * 252).dp * scale, y = (-100 + index / 10 * 372).dp * scale)
                    .size(236.dp * scale, 354.dp * scale).clip(RoundedCornerShape(10.dp * scale)),
                alpha = 0.32f, contentScale = ContentScale.Crop)
        }
        }
        Box(Modifier.fillMaxSize().drawWithCache {
            val ratio = size.height * 0.7f / (size.width * 0.6f)
            val ellipseCenter = Offset(size.width / 2, size.height / 2)
            val brush = Brush.radialGradient(0f to Palette.background.copy(alpha = 0.92f),
                0.55f to Palette.background.copy(alpha = 0.75f), 1f to Palette.background.copy(alpha = 0.45f),
                center = ellipseCenter, radius = size.width * 0.6f)
            onDrawBehind {
                scale(1f, ratio, pivot = center) { drawRect(brush,
                    topLeft = Offset(0f, center.y * (1 - 1 / ratio)), size = Size(size.width, size.height / ratio)) }
            }
        })
    }
}

private val TV_WELCOME_POSTERS = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 10, 11, 12, 13, 14, 15, 16, 17, 0, 1, 15, 16, 17, 0, 1, 2, 3, 4, 5, 6)
