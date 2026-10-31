package org.viptv.app.features.details

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.viptv.app.components.Button
import org.viptv.app.components.Motion
import org.viptv.app.components.motionSpec
import org.viptv.app.theme.Palette

// The meta fills the below-hero placeholders in, and the new content fades in over the shape the skeleton left.
@Composable
internal fun rememberContentReveal(loaded: Boolean): State<Float> {
    val alpha = remember { Animatable(if (loaded) 1f else 0f) }
    val arrive = motionSpec<Float>(tween(Motion.arrive, easing = Motion.standard))
    LaunchedEffect(loaded, arrive) { if (loaded) alpha.animateTo(1f, arrive) else alpha.snapTo(0f) }
    return alpha.asState()
}

@Composable
internal fun DetailPlaceholder(modifier: Modifier) {
    Box(modifier.background(Palette.raised, RoundedCornerShape(8.dp)))
}

@Composable
internal fun DetailBelowSkeleton(phone: Boolean, desktop: Boolean) {
    Column(Modifier.semantics { contentDescription = "Loading title details" }, verticalArrangement = Arrangement.spacedBy(if (phone) 18.dp else 24.dp)) {
        repeat(3) { DetailPlaceholder(Modifier.fillMaxWidth(0.85f).height(18.dp)) }
        Spacer(Modifier.height(8.dp))
        DetailPlaceholder(Modifier.width(220.dp).height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(if (phone) 2 else 4) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailPlaceholder(Modifier.fillMaxWidth().aspectRatio(if (desktop) 16f / 6.4f else 2f / 3))
                    DetailPlaceholder(Modifier.fillMaxWidth(0.7f).height(14.dp))
                }
            }
        }
    }
}

@Composable
internal fun DetailInlineFailure(error: String?, retry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        error?.let { Text(it, color = Palette.danger) }
        retry?.let { again -> Button(onClick = again) { Text("Try again") } }
    }
}

@Composable
internal fun TvDetailBelowSkeleton(scale: Float) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp * scale)) {
        DetailPlaceholder(Modifier.padding(start = 96.dp * scale).width(420.dp * scale).height(32.dp * scale))
        Row(Modifier.padding(start = 96.dp * scale, top = 24.dp * scale), horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
            repeat(2) {
                Column {
                    DetailPlaceholder(Modifier.width(448.dp * scale).aspectRatio(16f / 9))
                    Spacer(Modifier.height(14.dp * scale))
                    DetailPlaceholder(Modifier.width(300.dp * scale).height(22.dp * scale))
                }
            }
        }
        DetailPlaceholder(Modifier.padding(start = 96.dp * scale, top = 48.dp * scale).width(300.dp * scale).height(30.dp * scale))
        Row(Modifier.padding(start = 96.dp * scale, top = 20.dp * scale), horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            repeat(4) { DetailPlaceholder(Modifier.width(232.dp * scale).aspectRatio(2f / 3)) }
        }
    }
}
