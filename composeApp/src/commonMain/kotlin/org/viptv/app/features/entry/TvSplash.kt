package org.viptv.app.features.entry

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import org.viptv.app.components.Brand
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvRequestedFocus
import org.viptv.app.components.TvStartupProgress
import org.viptv.app.theme.Palette

@Composable
internal fun TvSplash(changeConnection: (() -> Unit)? = null) {
    val opacity = remember { Animatable(0f) }
    LaunchedEffect(Unit) { opacity.animateTo(1f, tween(600, easing = CubicBezierEasing(0f, 0f, .58f, 1f))) }
    BoxWithConstraints(Modifier.fillMaxSize().semantics { contentDescription = "Starting VIPTV" }) {
        val scale = maxWidth / 1920.dp
        Box(Modifier.align(Alignment.TopCenter).padding(top = 440.dp * scale).height(104.dp * scale).graphicsLayer { alpha = opacity.value }) {
            Brand(88.dp * scale, (104 * scale).sp, 22.dp * scale, (-4.16f * scale).sp, (104 * scale).sp)
        }
        TvStartupProgress(scale, Modifier.align(Alignment.TopCenter).padding(top = 600.dp * scale))
        if (changeConnection != null) {
            // Keep safe startup usable while secure storage/auth/bootstrap are
            // pending. Neither action nor status waits for the brand animation.
            val first = remember { FocusRequester() }
            var placed by remember { mutableStateOf(false) }
            TvRequestedFocus(first, placed, enabled = placed)
            Text("Getting VIPTV ready…", Modifier.align(Alignment.TopCenter).padding(top = 664.dp * scale),
                color = Palette.secondary, fontSize = (26 * scale).sp, lineHeight = (34 * scale).sp)
            TvAction(Modifier.align(Alignment.TopCenter).padding(top = 736.dp * scale)
                .width(360.dp * scale).height(72.dp * scale).onGloballyPositioned { placed = true },
                scale, requester = first, activate = changeConnection) {
                Text("Change connection", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
