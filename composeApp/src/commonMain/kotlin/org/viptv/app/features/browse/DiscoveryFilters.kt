package org.viptv.app.features.browse

import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.*
import org.viptv.engine.wire.*

/** Controls come from the selected profile's provider; the view never chooses a provider. */
@Composable
internal fun DiscoveryFilters(controls: DiscoveryControls, query: BrowseQuery, showType: Boolean,
    changed: (String, String) -> Unit, first: FocusRequester? = null) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val visible = controls.controls.filter { (showType || it.id != "type") && it.id != "search" }
        if (visible.isEmpty()) Text("No browsing filters", Modifier.then(if (first != null) Modifier.focusRequester(first) else Modifier).focusable())
        visible.forEachIndexed { index, control ->
            val value = when (control.id) { "type" -> query.type; "catalog" -> query.catalog; "genre" -> query.genre; else -> query.extras[control.id] }
            val modifier = if (index == 0 && first != null) Modifier.focusRequester(first) else Modifier
            when (control.kind) {
                ControlKind.SINGLECHOICE -> {
                    var expanded by remember(control.id) { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = modifier) {
                            Text(control.choices.find { it.value == value }?.label ?: control.label)
                        }
                        DropdownMenu(expanded, { expanded = false }) {
                            if (!control.required) DropdownMenuItem(text = { Text("All ${control.label.lowercase()}") }, onClick = { changed(control.id, ""); expanded = false })
                            control.choices.forEach { choice -> DropdownMenuItem(text = { Text(choice.label) }, onClick = { changed(control.id, choice.value); expanded = false }) }
                        }
                    }
                }
                ControlKind.TEXT, ControlKind.NUMBER, ControlKind.DATE -> OutlinedTextField(value.orEmpty(), { changed(control.id, it) },
                    modifier.width(180.dp), label = { Text(control.label) }, singleLine = true)
                ControlKind.TOGGLE -> FilterChip(value == "true", { changed(control.id, (value != "true").toString()) }, label = { Text(control.label) }, modifier = modifier)
                ControlKind.MULTICHOICE -> Unit
            }
        }
    }
}
