package org.viptv.app.features.entry

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette

internal data class TvSignedInIdentity(val sessionId: String, val displayName: String, val email: String, val avatar: Profile)

@Composable
internal fun TvSignedIn(identity: TvSignedInIdentity, complete: (() -> Unit)? = null) {
    val progress = remember(identity.sessionId) { Animatable(0f) }
    val currentComplete by rememberUpdatedState(complete)
    LaunchedEffect(identity.sessionId) { progress.animateTo(1f, tween(2500, easing = LinearEasing)); currentComplete?.invoke() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        TvGreetingPart(Modifier.align(Alignment.TopCenter).padding(top = 300.dp * scale), identity.sessionId, 0, scale) {
            ProfileAvatar(identity.avatar, 168.dp * scale, 84.dp * scale)
        }
        TvGreetingPart(Modifier.align(Alignment.TopCenter).padding(top = 510.dp * scale), identity.sessionId, 100, scale) {
            Text("Welcome back, ${identity.displayName}", fontSize = (56 * scale).sp, lineHeight = (64.4f * scale).sp,
                letterSpacing = (-1.12f * scale).sp, fontWeight = FontWeight.Bold)
        }
        TvGreetingPart(Modifier.align(Alignment.TopCenter).padding(top = 590.dp * scale), identity.sessionId, 180, scale) {
            Text("Signed in as ${identity.email}", fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp, color = Palette.secondary)
        }
        Box(Modifier.align(Alignment.TopCenter).padding(top = 700.dp * scale).size(200.dp * scale, 4.dp * scale)
            .clip(RoundedCornerShape(2.dp * scale)).background(Color.White.copy(alpha = .12f))) {
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = progress.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f) }
                .background(Palette.text))
        }
    }
}

@Composable
private fun TvGreetingPart(modifier: Modifier, sessionId: String, delayMillis: Long, scale: Float, content: @Composable () -> Unit) {
    val progress = remember(sessionId) { Animatable(0f) }
    val offsetPixels = with(androidx.compose.ui.platform.LocalDensity.current) { (16.dp * scale).toPx() }
    LaunchedEffect(sessionId) { delay(delayMillis); progress.animateTo(1f, tween(500, easing = CubicBezierEasing(.2f, 0f, 0f, 1f))) }
    Box(modifier.graphicsLayer { alpha = progress.value; translationY = offsetPixels * (1f - progress.value) }) { content() }
}
