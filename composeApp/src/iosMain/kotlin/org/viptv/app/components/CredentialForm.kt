package org.viptv.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal actual fun CredentialForm(
    fields: List<CredentialField>,
    submitLabel: String,
    valid: Boolean,
    busy: Boolean,
    error: String?,
    onChange: (Int, String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier,
    showProgress: Boolean,
    secondaryLabel: String?,
    onSecondary: (() -> Unit)?,
    storePasswordOnSuccess: Boolean,
) {
    val focus = remember(fields.size) { List(fields.size) { FocusRequester() } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        fields.forEachIndexed { index, field ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = field.value,
                    onValueChange = { onChange(index, it) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus[index]),
                    enabled = !busy,
                    singleLine = true,
                    label = { Text(field.label) },
                    isError = field.errorText != null,
                    visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field.secret) KeyboardType.Password else KeyboardType.Text,
                        imeAction = field.imeAction,
                        autoCorrectEnabled = !field.secret,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = if (field.imeAction == ImeAction.Next && index < fields.lastIndex) ({ focus[index + 1].requestFocus() }) else null,
                        onGo = if (field.imeAction == ImeAction.Go || field.imeAction == ImeAction.Done || field.imeAction == ImeAction.Send || field.imeAction == ImeAction.Search) ({ onSubmit() }) else null,
                    ),
                )
                field.errorText?.let { Text(it, color = Palette.danger) }
                if (field.errorText == null) field.hintBelow?.let { Text(it, fontSize = 13.sp, lineHeight = 17.sp, color = Palette.secondary) }
            }
        }
        error?.let { Text(it, color = Palette.danger) }
        if (busy && showProgress) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSubmit, enabled = valid && !busy) { Text(submitLabel) }
            if (secondaryLabel != null && onSecondary != null) TextButton(onSecondary, enabled = !busy) { Text(secondaryLabel) }
        }
    }
}