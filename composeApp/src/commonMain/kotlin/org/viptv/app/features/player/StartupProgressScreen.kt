package org.viptv.app.features.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.viptv.app.components.BackControl
import org.viptv.app.components.Motion
import org.viptv.app.components.TitleLogo
import org.viptv.app.state.StartupProgress
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

internal const val StartupArtTag = "startup-art"
internal const val MutedArtAlpha = 0.25f
private const val BackdropScrimAlpha = 0.72f
private const val TitleLineHeight = 1.15f

@Composable
internal fun StartupProgressScreen(
    progress: StartupProgress,
    title: Card?,
    modifier: Modifier = Modifier,
    artWidth: Dp = 440.dp,
    artMaxHeight: Dp = 320.dp,
    titleSize: TextUnit = 40.sp,
    statusSize: TextUnit = 14.sp,
    statusGap: Dp = 28.dp,
    reduceMotion: Boolean = false,
    backdrop: Boolean = false,
    close: (() -> Unit)? = null,
    insets: WindowInsets = WindowInsets(0),
    desktop: Boolean = false,
) {
    val fill by animateFloatAsState(progress.fill.coerceIn(0f, 1f),
        if (reduceMotion) snap() else tween(Motion.arrive, easing = Motion.standard), label = "startup fill")
    Box(modifier.fillMaxSize().background(Palette.background)) {
        val landing = title?.landscape ?: title?.poster
        if (backdrop && landing != null) {
            AsyncImage(landing, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Palette.background.copy(alpha = BackdropScrimAlpha)))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(statusGap)) {
            TitleLogo(title?.logo, title?.title.orEmpty(),
                Modifier.width(artWidth).heightIn(max = artMaxHeight).testTag(StartupArtTag)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val edge = size.width * fill
                        if (edge < size.width) drawRect(Color.Black.copy(alpha = MutedArtAlpha), Offset(edge, 0f),
                            Size(size.width - edge, size.height), blendMode = BlendMode.DstIn)
                    },
                contentAlignment = Alignment.Center, logoAlignment = Alignment.Center, logoKeepsItsShape = true) {
                Text(title?.title.orEmpty(), Modifier.fillMaxWidth(), color = Palette.text, fontSize = titleSize,
                    lineHeight = if (titleSize.isSp) (titleSize.value * TitleLineHeight).sp else titleSize,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            Text(progress.status, color = Palette.tertiary, fontSize = statusSize)
        }
        if (close != null) BackControl(close, Modifier.align(Alignment.TopStart)
            .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(24.dp), desktop = desktop)
    }
}
