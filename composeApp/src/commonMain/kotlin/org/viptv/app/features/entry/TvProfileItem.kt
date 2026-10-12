package org.viptv.app.features.entry

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette

@Composable
internal fun TvProfileItem(name: String, locked: Boolean, size: Dp, scale: Float,
    modifier: Modifier, enabled: Boolean, choose: () -> Unit, artwork: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val frame by animateFloatAsState(if (focused) 1f else 0f, tween(120), label = "Profile focus")
    Column(modifier.width(size).onFocusChanged { focused = it.isFocused }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = choose),
        horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = RoundedCornerShape(28.dp * scale)
        Box(Modifier.border(4.dp * scale, Color.White.copy(alpha = frame), shape)
            .border(5.dp * scale, Color.Black.copy(alpha = .35f * frame), shape)) { artwork() }
        Row(Modifier.padding(top = 20.dp * scale), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            if (locked) Icon(Lucide.Lock, "Locked profile", Modifier.size(24.dp * scale), tint = Palette.secondary)
            Text(name, fontSize = (28 * scale).sp, lineHeight = (34 * scale).sp, color = if (focused) Palette.text else Palette.secondary,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        }
    }
}
