package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

/**
 * The W3Device eight-slot code input: digits grouped in blocks of four
 * with a dash separator. A hidden single-line field captures input; the
 * next empty slot carries the cream focus ring from the design. Compact
 * slots are 34x39 per W3DeviceMobile.
 */
@Composable
internal fun DeviceCodeInput(
    code: String,
    changed: (String) -> Unit,
    submit: () -> Unit,
    error: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val slotWidth = if (compact) 34.dp else 56.dp
    val slotHeight = if (compact) 39.dp else 64.dp
    val gap = if (compact) 6.dp else 8.dp
    val digitSize = if (compact) 20.sp else 26.sp
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(interactionSource = interaction, indication = null) { focus.requestFocus() },
    ) {
        listOf(0, 4).forEach { group ->
            if (group == 4) Text("-", color = Palette.tertiary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(4) { index ->
                    val position = group + index
                    val digit = code.getOrNull(position)?.toString() ?: ""
                    Box(
                        Modifier
                            .size(slotWidth, slotHeight)
                            .background(
                                when {
                                    error -> Color(0x29FF7A6E)
                                    else -> Color(0x0FFFFFFF)
                                },
                                RoundedCornerShape(10.dp),
                            )
                            .border(
                                when {
                                    error -> androidx.compose.foundation.BorderStroke(1.dp, Palette.danger)
                                    position == code.length && enabled -> androidx.compose.foundation.BorderStroke(2.dp, Palette.text)
                                    else -> androidx.compose.foundation.BorderStroke(1.dp, Color(0x24FFFFFF))
                                },
                                RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) { Text(digit, color = Palette.text, fontSize = digitSize, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
    Box(Modifier.width(0.dp).height(0.dp)) {
        BasicTextField(
            value = code,
            onValueChange = { raw -> changed(raw.filter(Char::isDigit).take(8)) },
            textStyle = TextStyle(Color.Transparent, fontSize = 1.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            singleLine = true,
            modifier = Modifier
                .focusRequester(focus)
                .background(Color.Transparent)
                .width(0.dp)
                .height(0.dp),
            enabled = enabled,
        )
    }
}
