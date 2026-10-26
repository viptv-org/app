package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import org.viptv.app.connection.SourceIntegrationSchema

@Composable
internal fun SourceIntegrationForm(schema: SourceIntegrationSchema, busy: Boolean, error: String?, dismiss: () -> Unit,
    keyboardInsets: WindowInsets? = null, save: (JsonObject) -> Unit) {
    val values = remember(schema.plugin_id) { mutableStateMapOf<String, String>() }
    var selected by remember(schema.plugin_id) { mutableStateOf(emptySet<String>()) }
    val valid = schema.credential_fields.all { !it.required || !values[it.id].isNullOrBlank() }
    AlertDialog(onDismissRequest = dismiss,
        modifier = Modifier.composed { windowInsetsPadding(keyboardInsets ?: WindowInsets.ime) },
        title = { Text(schema.name) }, text = {
        org.viptv.app.components.TvFocusScroll(horizontal = false) { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            schema.credential_fields.forEach { field ->
                OutlinedTextField(values[field.id].orEmpty(), { values[field.id] = it }, label = { Text(field.label) },
                    singleLine = true, enabled = !busy,
                    keyboardOptions = if (field.secret) KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false) else KeyboardOptions.Default,
                    visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None)
            }
            if (schema.provider_choices.isNotEmpty()) {
                Text("Providers (leave empty to use the default selection)")
                schema.provider_choices.forEach { provider ->
                    Row {
                        Checkbox(provider.id in selected, { checked ->
                            selected = if (checked) selected + provider.id else selected - provider.id
                        }, enabled = !busy && (provider.id in selected || selected.size < schema.maximum_providers))
                        Text(provider.label, Modifier.padding(top = 12.dp))
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }
    }, confirmButton = {
        TextButton({
            val configuration = buildJsonObject {
                values.forEach { (key, value) -> if (value.isNotBlank()) put(key, value.trim()) }
                if (schema.provider_choices.isNotEmpty()) put("providers", JsonArray(selected.map(::JsonPrimitive)))
            }
            values.clear()
            save(configuration)
        }, enabled = valid && !busy) { Text("Save") }
    }, dismissButton = { TextButton({ values.clear(); dismiss() }, enabled = !busy) { Text("Cancel") } })
}
