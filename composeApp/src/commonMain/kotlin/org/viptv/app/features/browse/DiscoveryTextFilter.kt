package org.viptv.app.features.browse

import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.engine.wire.QueryControl

/** Keep a draft while typing; an explicit apply avoids a request for each incomplete value. */
@Composable
internal fun DiscoveryTextFilter(control: QueryControl, value: String?, modifier: Modifier, apply: (String) -> Unit) {
    var draft by remember(control.id, value) { mutableStateOf(value.orEmpty()) }
    OutlinedTextField(draft, { draft = it }, modifier.width(180.dp), label = { Text(control.label) }, singleLine = true,
        trailingIcon = { TextButton({ apply(draft.trim()) }, enabled = draft != value.orEmpty()) { Text("Apply") } })
}
