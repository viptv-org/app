package org.viptv.app.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import org.viptv.app.images.rememberLogoRequest

internal const val TITLE_LOGO_TAG = "title-logo"

// The text title stays drawn until the logo has actually decoded; a dead logo link never blanks it.
@Composable
internal fun TitleLogo(
    logo: String?,
    title: String,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.CenterStart,
    logoModifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    logoAlignment: Alignment = Alignment.CenterStart,
    titleContent: @Composable BoxScope.() -> Unit,
) {
    val request = logo?.let { rememberLogoRequest(it) }
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsState()
    val ready = request != null && state is AsyncImagePainter.State.Success
    val spec = motionSpec<Float>(tween(Motion.arrive, easing = Motion.standard))
    val textAlpha by animateFloatAsState(if (ready) 0f else 1f, spec, label = "TitleLogo text")
    val logoAlpha by animateFloatAsState(if (ready) 1f else 0f, spec, label = "TitleLogo logo")
    Box(modifier.testTag(TITLE_LOGO_TAG), contentAlignment = contentAlignment) {
        Box(Modifier.graphicsLayer { alpha = textAlpha }) { titleContent() }
        if (request != null) {
            Image(painter, title, logoModifier.graphicsLayer { alpha = logoAlpha }, contentScale = contentScale,
                alignment = logoAlignment)
        }
    }
}