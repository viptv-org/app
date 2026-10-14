package org.viptv.app.features.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.QueryControl

@Composable
internal fun LibraryFilterField(control: QueryControl, value: String, change: (String) -> Unit,
    television: Boolean, scale: Float, first: FocusRequester? = null) {
    var menu by remember { mutableStateOf(false) }
    val selected = control.choices.find { it.value == value }?.label ?: value.ifBlank { "Any" }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        Text(control.label, fontSize = (if (television) 22 * scale else 13f).sp, color = Palette.secondary, fontWeight = FontWeight.Medium)
        Box {
            if (control.choices.isEmpty()) OutlinedTextField(value, change, Modifier.fillMaxWidth(), singleLine = true)
            else {
                if (television) TvAction(Modifier.fillMaxWidth().height(64.dp * scale), scale, requester = first,
                    activate = { menu = true }, contentPadding = PaddingValues(horizontal = 18.dp * scale)) { tint ->
                    Text(selected, Modifier.weight(1f), color = tint, fontSize = (24 * scale).sp)
                    Icon(Lucide.ChevronDown, null, Modifier.size(24.dp * scale), tint)
                } else OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                    Text(selected, Modifier.weight(1f), color = Palette.text)
                    Icon(Lucide.ChevronDown, null, Modifier.size(18.dp))
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }, modifier = Modifier.widthIn(min = 260.dp * scale, max = 600.dp * scale).heightIn(max = 480.dp * scale)) {
                    control.choices.forEach { choice -> DropdownMenuItem(
                        text = { Text(choice.label, fontSize = (if (television) 24 * scale else 14f).sp) },
                        leadingIcon = { if (value == choice.value) Icon(Lucide.Check, null) },
                        modifier = Modifier.heightIn(min = if (television) 64.dp * scale else 44.dp),
                        onClick = { change(choice.value); menu = false }) }
                }
            }
        }
    }
}
