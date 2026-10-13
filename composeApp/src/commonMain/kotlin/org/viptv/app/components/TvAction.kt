package org.viptv.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

@Composable
internal fun TvAction(modifier: Modifier, scale: Float, primary: Boolean = false, round: Boolean = false,
    requester: FocusRequester? = null, activate: () -> Unit, contentPadding: PaddingValues = PaddingValues(0.dp),
    enabled: Boolean = true, background: Color = Palette.text.copy(alpha = 0.16f),
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp * scale,
    arrangement: Arrangement.Horizontal = Arrangement.Center, content: @Composable RowScope.(Color) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val shape = if (round) CircleShape else RoundedCornerShape(cornerRadius)
    val light = enabled && focused
    val fill by animateColorAsState(when { enabled && pressed -> Palette.body; light -> Palette.text; else -> background }, tween(150), label = "TV action fill")
    val ink by animateColorAsState(if (light || enabled && pressed) Palette.background else Palette.text, tween(150), label = "TV action content")
    Row(modifier.then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .onFocusChanged { if (focused != it.isFocused) focused = it.isFocused }
        .graphicsLayer { alpha = if (enabled) 1f else .38f; this.shape = shape; shadowElevation = if (light) 18.dp.toPx() * scale else 0f }
        .clip(shape).background(fill)
        .clickable(enabled = enabled, interactionSource = interactions, indication = null, onClick = activate).padding(contentPadding),
        horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) { content(ink) }
}
