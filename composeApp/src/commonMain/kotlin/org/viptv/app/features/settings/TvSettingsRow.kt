package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.tween
import com.composeunstyled.UnstyledSwitch
import com.composeunstyled.Track
import com.composeunstyled.Thumb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette

@Composable
internal fun TvSettingsRow(label: String, subtitle: String? = null, scale: Float, value: String? = null,
    checked: Boolean? = null, requester: FocusRequester? = null, left: FocusRequester? = null, activate: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) Palette.background else Palette.text
    Row(Modifier.fillMaxWidth().height(96.dp * scale).then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .focusProperties { if (left != null) this.left = left }.onFocusChanged { focused = it.isFocused }
        .semantics { if (checked != null) { role = Role.Switch; toggleableState = if (checked) ToggleableState.On else ToggleableState.Off } }
        .background(if (focused) Palette.text else Color(0xFF191919), RoundedCornerShape(14.dp * scale))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = activate)
        .padding(horizontal = 28.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
            Text(label, color = color, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = if (focused) Palette.background.copy(alpha = 0.75f) else Palette.secondary,
                fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp) }
        }
        if (checked != null) TvSettingsSwitch(checked, scale)
        if (value != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            Text(value, color = if (focused) Palette.background.copy(alpha = 0.75f) else Palette.secondary, fontSize = (24 * scale).sp)
            Icon(Lucide.ChevronRight, null, Modifier.size(24.dp * scale), if (focused) Palette.background else Palette.secondary)
        }
    }
}

@Composable
internal fun TvSettingsSwitch(checked: Boolean, scale: Float) {
    UnstyledSwitch(checked, onCheckedChange = null) {
        Track(Modifier.size(68.dp * scale, 38.dp * scale).background(if (checked) Palette.accent else Color(0xFF494949), CircleShape)
            .padding(4.dp * scale)) {
            Thumb(Modifier.size(30.dp * scale).background(if (checked) Palette.background else Palette.text, CircleShape), animationSpec = tween(120))
        }
    }
}
