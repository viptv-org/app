package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun SignInInput(
    value: String,
    change: (String) -> Unit,
    hint: String,
    transformation: VisualTransformation = VisualTransformation.None,
    height: Dp = 44.dp,
    fontSize: TextUnit = 14.sp,
    secret: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(value, change, Modifier.fillMaxWidth().height(height)
        .onFocusChanged { focused = it.isFocused }
        .background(Color.White.copy(alpha = .06f), shape)
        .border(if (focused) 2.dp else 1.dp, if (focused) Palette.text else Color.White.copy(alpha = .14f), shape),
        singleLine = true, visualTransformation = transformation,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false) else KeyboardOptions.Default,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.text, fontSize = fontSize, letterSpacing = 0.sp),
        cursorBrush = SolidColor(Palette.text), decorationBox = { inner ->
            Row(Modifier.fillMaxSize().padding(horizontal = if (height >= 48.dp) 13.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, color = Palette.secondary, fontSize = fontSize, letterSpacing = 0.sp)
                    inner()
                }
                trailing?.invoke()
            }
        })
}
