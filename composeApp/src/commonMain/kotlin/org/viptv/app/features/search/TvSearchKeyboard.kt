package org.viptv.app.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable

@Composable
internal fun TvSearchKeyboard(edit: ((String) -> String) -> Unit, scale: Float, requestInitialFocus: Boolean = true, first: FocusRequester? = null) {
    val initial = remember(first) { first ?: FocusRequester() }
    org.viptv.app.components.TvRequestedFocus(initial, Unit, requestInitialFocus)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            TvKeyboardKey(Modifier.weight(1f), scale, "Space", activate = { edit { it + " " } }) { Icon(Lucide.Space, "Space", Modifier.size(28.dp * scale), it) }
            TvKeyboardKey(Modifier.weight(1f), scale, "Delete", activate = { edit { it.dropLast(1) } }) { Icon(Lucide.Delete, "Delete", Modifier.size(28.dp * scale), it) }
        }
        "abcdefghijklmnopqrstuvwxyz1234567890".chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                row.forEach { character ->
                    TvKeyboardKey(Modifier.weight(1f).then(if (character == 'h') Modifier.focusRequester(initial) else Modifier),
                        scale, character.toString(), activate = { edit { it + character } }) {
                        Text(character.toString(), color = it, fontSize = (28 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvKeyboardKey(modifier: Modifier, scale: Float, label: String, activate: () -> Unit, content: @Composable (androidx.compose.ui.graphics.Color) -> Unit) {
    var focus by remember { mutableStateOf(false) }
    Box(modifier.height(68.dp * scale).onFocusChanged { focus = it.isFocused }
        .clip(RoundedCornerShape(12.dp * scale)).background(if (focus) Palette.text else Palette.raised)
        .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = label, onClick = activate, shape = RoundedCornerShape(12.dp * scale)), contentAlignment = Alignment.Center) {
        content(if (focus) Palette.background else Palette.text)
    }
}
