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
import org.viptv.app.components.DropdownMenuItem

@Composable
internal fun TvLibraryListPicker(snapshot: LibrarySnapshot?, query: LibraryQuery, change: (LibraryQuery) -> Unit, scale: Float) {
    if (snapshot == null || snapshot.sources.size < 2) return
    var expanded by remember { mutableStateOf(false) }
    val label = snapshot.sources.find { it.id == query.source }?.label ?: "My List"
    Box {
        TvAction(Modifier.height(64.dp * scale), scale, activate = { expanded = true },
            contentPadding = PaddingValues(horizontal = 20.dp * scale)) { tint ->
            Text(label, color = tint, fontSize = (24 * scale).sp)
            Spacer(Modifier.width(12.dp * scale)); Icon(Lucide.ChevronDown, null, Modifier.size(24.dp * scale), tint)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            snapshot.sources.forEach { source -> DropdownMenuItem(text = { Text(source.label) },
                enabled = source.availability == Availability.AVAILABLE, onClick = {
                    change(query.forList(source.id)); expanded = false
                }) }
        }
    }
}
