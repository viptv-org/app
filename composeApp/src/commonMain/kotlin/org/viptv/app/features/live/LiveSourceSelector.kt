package org.viptv.app.features.live

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.components.OutlinedButton
import org.viptv.app.components.DropdownMenuItem

@Composable
internal fun LiveSourceSelector(sources: Map<String, String>, selected: String?, choose: (String) -> Unit,
    enabled: Boolean = true, television: Boolean = false, scale: Float = 1f) {
    var expanded by remember { mutableStateOf(false) }
    val label = "TV source: ${sources[selected] ?: "Choose source"}"
    LaunchedEffect(sources, selected, enabled) { expanded = false }
    Box {
        if (television) TvAction(Modifier.height(52.dp * scale).widthIn(max = 360.dp * scale), scale,
            activate = { expanded = true }, enabled = enabled, contentPadding = PaddingValues(horizontal = 20.dp * scale)) {
            Text(label, color = it, fontSize = (22 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else OutlinedButton({ expanded = true }, enabled = enabled, modifier = Modifier.widthIn(max = 360.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded && enabled, onDismissRequest = { expanded = false }) {
            sources.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; choose(id) }) }
        }
    }
}
