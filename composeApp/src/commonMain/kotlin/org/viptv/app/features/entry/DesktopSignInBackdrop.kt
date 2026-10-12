package org.viptv.app.features.entry

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.*

private val posters = listOf(
    Res.drawable.signin_poster_01, Res.drawable.signin_poster_02,
    Res.drawable.signin_poster_03, Res.drawable.signin_poster_04,
    Res.drawable.signin_poster_05, Res.drawable.signin_poster_06,
    Res.drawable.signin_poster_07, Res.drawable.signin_poster_08,
    Res.drawable.signin_poster_09, Res.drawable.signin_poster_10,
    Res.drawable.signin_poster_11, Res.drawable.signin_poster_12,
)

@Composable
internal fun DesktopSignInBackdrop() {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0B0B0C))) {
        repeat(4) { row ->
            Row(Modifier.offset(x = (-30).dp, y = (-80 + row * 224).dp).alpha(.25f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(9) { column ->
                    val art: DrawableResource = posters[(row * 5 + column) % posters.size]
                    Image(painterResource(art), null,
                        Modifier.size(width = 140.dp, height = 210.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val width = with(density) { maxWidth.toPx() }
            val height = with(density) { maxHeight.toPx() }
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(
                0f to Color(0xFF0B0B0C).copy(alpha = .55f),
                .75f to Color(0xFF0B0B0C),
                center = Offset(width / 2, height / 2), radius = width * .6f)))
        }
    }
}
