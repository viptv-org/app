package org.viptv.app.features.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.engine.wire.*

@Composable
internal fun LibraryListPicker(snapshot: LibrarySnapshot?, query: LibraryQuery, change: (LibraryQuery) -> Unit,
    television: Boolean, scale: Float) {
    if (snapshot == null || snapshot.sources.size < 2) return
    var expanded by remember { mutableStateOf(false) }
    val label = snapshot.sources.find { it.id == query.source }?.label ?: "My List"
    Box {
        if (television) TvAction(Modifier.height(64.dp * scale), scale, activate = { expanded = true },
            contentPadding = PaddingValues(horizontal = 20.dp * scale)) { tint ->
            Text(label, color = tint, fontSize = (24 * scale).sp)
            Spacer(Modifier.width(12.dp * scale)); Icon(Lucide.ChevronDown, null, Modifier.size(24.dp * scale), tint)
        } else OutlinedButton(onClick = { expanded = true }) {
            Text(label); Spacer(Modifier.width(8.dp)); Icon(Lucide.ChevronDown, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            snapshot.sources.forEach { source -> DropdownMenuItem(text = { Text(source.label) },
                enabled = source.availability == Availability.AVAILABLE, onClick = {
                    change(query.copy(source = source.id, offset = 0, show = "all", status = null,
                        genre = null, rated = null, added = null, episodes_left_max = null)); expanded = false
                }) }
        }
    }
}
