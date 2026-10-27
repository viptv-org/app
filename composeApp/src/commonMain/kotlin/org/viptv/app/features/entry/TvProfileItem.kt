package org.viptv.app.features.entry

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable

@Composable
internal fun TvProfileItem(name: String, locked: Boolean, size: Dp, scale: Float,
    modifier: Modifier, enabled: Boolean, choose: () -> Unit, artwork: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(size).onFocusChanged { focused = it.isFocused }
        .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = choose),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.tvProfileTileRing(focused, 28.dp, scale)) { artwork() }
        Row(Modifier.padding(top = 20.dp * scale), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            if (locked) Icon(Lucide.Lock, "Locked profile", Modifier.size(24.dp * scale), tint = Palette.secondary)
            Text(name, fontSize = (28 * scale).sp, lineHeight = (34 * scale).sp, color = if (focused) Palette.text else Palette.secondary,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        }
    }
}

/**
 * Tv3Profiles focuses a tile with the board's *inset* ring, not the outline rows
 * use: `inset 0 0 0 4px #FFFFFF, inset 0 0 0 5px rgba(0,0,0,0.35)`. The 4 px white
 * ring sits on the tile edge and the 1 px dark hairline just inside it keeps the
 * ring readable over a bright avatar. Nothing is drawn outside the tile, so a
 * focused 200 px tile is still 200 px wide.
 */
private fun Modifier.tvProfileTileRing(focused: Boolean, radius: Dp, scale: Float = 1f,
    width: Dp = 4.dp): Modifier = if (!focused) this else drawWithContent {
    drawContent()
    val w = width.toPx() * scale
    val hair = 1.dp.toPx() * scale
    val r = radius.toPx() * scale
    val inner = w + hair / 2f
    drawRoundRect(Color.Black.copy(alpha = .35f), Offset(inner, inner),
        Size((size.width - 2 * inner).coerceAtLeast(0f), (size.height - 2 * inner).coerceAtLeast(0f)),
        CornerRadius((r - inner).coerceAtLeast(0f)), style = Stroke(hair))
    drawRoundRect(Color.White, Offset(w / 2f, w / 2f),
        Size((size.width - w).coerceAtLeast(0f), (size.height - w).coerceAtLeast(0f)),
        CornerRadius((r - w / 2f).coerceAtLeast(0f)), style = Stroke(w))
}
