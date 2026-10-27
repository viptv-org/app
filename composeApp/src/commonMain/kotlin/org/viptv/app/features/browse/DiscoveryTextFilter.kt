package org.viptv.app.features.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.viptv.engine.wire.QueryControl
import org.viptv.app.components.TextButton

/**
 * Keep a draft while typing; an explicit apply avoids a request for each
 * incomplete value.
 *
 * The Apply control sits *next to* the field, not inside it: inside Material's
 * `trailingIcon` slot it read as placeholder text in the input.
 */
@Composable
internal fun DiscoveryTextFilter(control: QueryControl, value: String?, modifier: Modifier, apply: (String) -> Unit) {
    var draft by remember(control.id, value) { mutableStateOf(value.orEmpty()) }
    val dirty = draft.trim() != value.orEmpty()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(draft, { draft = it }, Modifier.width(180.dp), label = { Text(control.label) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { apply(draft.trim()) }))
        if (dirty) TextButton({ apply(draft.trim()) }) { Text("Apply") }
    }
}
