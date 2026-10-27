package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable

@Composable
internal fun TvEntryField(label: String, value: String, hint: String, scale: Float, active: Boolean,
    masked: Boolean = false, select: () -> Unit) {
    Text(label, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, color = Palette.secondary, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp * scale))
    val shape = RoundedCornerShape(14.dp * scale)
    Row(Modifier.fillMaxWidth().height(84.dp * scale).background(Color.White.copy(alpha = .08f), shape)
        .border((if (active) 2 else 1).dp * scale, if (active) Palette.text else Color.White.copy(alpha = .14f), shape)
        .onFocusChanged { if (it.isFocused) select() }
        .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = select)
        .padding(horizontal = 28.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        Text(value.ifEmpty { hint }, Modifier.widthIn(max = 1064.dp * scale), fontSize = (34 * scale).sp, lineHeight = (41 * scale).sp,
            letterSpacing = (if (masked && value.isNotEmpty()) 6.8f * scale else 0f).sp,
            color = if (value.isEmpty()) Palette.secondary else Palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (active) Box(Modifier.padding(start = 8.dp * scale).width(2.dp * scale).height(40.dp * scale).background(Palette.accent))
    }
}
