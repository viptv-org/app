package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

@Composable
internal fun TvSubtitleColor(value: Long, label: String, selected: Boolean, scale: Float, sidebar: FocusRequester?, choose: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    // Colour circles are custom clickables; register them with the settings
    // column so DOWN at the bottom of the page dead-ends instead of jumping into
    // the sidebar (the TvSettingsRow/TvAction clamp does not cover them).
    val own = remember { FocusRequester() }
    val (columnUp, columnDown) = org.viptv.app.components.rememberTvColumnItem(own)
    Box(Modifier.size(64.dp * scale).focusRequester(own)
        .focusProperties {
            if (sidebar != null) left = sidebar
            columnUp?.let { up = it }
            columnDown?.let { down = it }
        }
        .onFocusChanged { focused = it.isFocused }
        .semantics { contentDescription = label; this.selected = selected; role = Role.RadioButton }
        .then(if (focused || selected) Modifier.border(3.dp * scale, Palette.text, CircleShape) else Modifier)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = choose), contentAlignment = Alignment.Center) {
        Box(Modifier.size(52.dp * scale).background(Color(value), CircleShape))
    }
}
