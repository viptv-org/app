package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

@Composable
internal fun TvAction(modifier: Modifier, scale: Float, primary: Boolean = false, round: Boolean = false,
    requester: FocusRequester? = null, activate: () -> Unit, contentPadding: PaddingValues = PaddingValues(0.dp),
    enabled: Boolean = true, background: Color = Palette.text.copy(alpha = 0.16f),
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp * scale,
    arrangement: Arrangement.Horizontal = Arrangement.Center, content: @Composable RowScope.(Color) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = if (round) CircleShape else RoundedCornerShape(cornerRadius)
    val light = enabled && (primary || focused)
    Row(modifier.then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .onFocusChanged { focused = it.isFocused }.clip(shape)
        .background(if (light) Palette.text else background)
        .then(if (focused && primary) Modifier.border(3.dp * scale, Palette.text, shape) else Modifier)
        .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = activate).padding(contentPadding),
        horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
        content(if (!enabled) Palette.secondary else if (light) Palette.background else Palette.text)
    }
}
