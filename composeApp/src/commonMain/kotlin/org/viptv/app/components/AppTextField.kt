package org.viptv.app.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

private val webFieldHeight = 52.dp

private object InertKeyboardActionScope : KeyboardActionScope {
    override fun defaultKeyboardAction(imeAction: ImeAction) = Unit
}

internal fun KeyboardActions.enterAction(imeAction: ImeAction): (() -> Unit)? {
    val action = when (imeAction) {
        ImeAction.Done -> onDone
        ImeAction.Go -> onGo
        ImeAction.Next -> onNext
        ImeAction.Previous -> onPrevious
        ImeAction.Search -> onSearch
        ImeAction.Send -> onSend
        else -> null
    } ?: onDone ?: onGo ?: onNext ?: onSearch ?: onSend ?: return null
    return { InertKeyboardActionScope.action() }
}

@Composable
internal fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val host = LocalDomTextHost.current
    if (host == null) {
        OutlinedTextField(value, onValueChange, modifier, enabled, textStyle = textStyle,
            label = label?.let { text -> { Text(text) } }, placeholder = placeholder?.let { text -> { Text(text) } },
            leadingIcon = leadingIcon, trailingIcon = trailingIcon, supportingText = supportingText, isError = isError,
            visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = true, shape = shape, colors = colors)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textColor = textStyle.color.takeOrElse {
        when {
            isError -> colors.errorTextColor
            focused -> colors.focusedTextColor
            else -> colors.unfocusedTextColor
        }
    }
    val outline = when {
        isError -> colors.errorIndicatorColor
        focused -> colors.focusedIndicatorColor
        else -> colors.unfocusedIndicatorColor
    }
    val labelColor = when {
        isError -> colors.errorLabelColor
        focused -> colors.focusedLabelColor
        else -> colors.unfocusedLabelColor
    }
    val spec = DomTextSpec(value, onValueChange, label ?: placeholder.orEmpty(), placeholder, enabled,
        visualTransformation is PasswordVisualTransformation, keyboardOptions.keyboardType,
        keyboardActions.enterAction(keyboardOptions.imeAction), textColor, textStyle.fontSize.takeIf { it.isSpecified } ?: 16.sp, interaction)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        label?.let { Text(it, color = labelColor, fontSize = 12.sp) }
        host.Field(spec, Modifier.fillMaxWidth().height(webFieldHeight)
            .border(BorderStroke(if (focused) 2.dp else 1.dp, outline), shape).padding(horizontal = 12.dp)) { input ->
            Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                leadingIcon?.invoke()
                input(Modifier.weight(1f))
                trailingIcon?.invoke()
            }
        }
        supportingText?.let { Box(Modifier.padding(horizontal = 12.dp)) { it() } }
    }
}

@Composable
internal fun AppBasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean = true,
    textStyle: TextStyle = TextStyle.Default,
    cursorBrush: Brush = SolidColor(Color.Black),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource? = null,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = { it() },
) {
    val host = LocalDomTextHost.current
    if (host == null) {
        BasicTextField(value, onValueChange, modifier, enabled, textStyle = textStyle, keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions, singleLine = true, visualTransformation = visualTransformation,
            interactionSource = interactionSource, cursorBrush = cursorBrush, decorationBox = decorationBox)
        return
    }
    val spec = DomTextSpec(value, onValueChange, label, null, enabled, visualTransformation is PasswordVisualTransformation,
        keyboardOptions.keyboardType, keyboardActions.enterAction(keyboardOptions.imeAction), textStyle.color.takeOrElse { Color.White },
        textStyle.fontSize.takeIf { it.isSpecified } ?: 16.sp, interactionSource)
    host.Field(spec, modifier) { input -> decorationBox { input(Modifier) } }
}
