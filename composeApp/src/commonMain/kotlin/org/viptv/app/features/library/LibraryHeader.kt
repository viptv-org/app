package org.viptv.app.features.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.*

@Composable
internal fun LibraryHeader(snapshot: LibrarySnapshot?, query: LibraryQuery, change: (LibraryQuery) -> Unit,
    television: Boolean, scale: Float, busy: Boolean, selected: FocusRequester?, first: FocusRequester?) {
    var filters by remember { mutableStateOf(false) }
    val filterButton: @Composable () -> Unit = {
        if (television) TvAction(Modifier.height(64.dp * scale).focusProperties { if (first != null) down = first }, scale,
            enabled = snapshot?.controls?.isNotEmpty() == true, activate = { filters = true },
            cornerRadius = 32.dp * scale, contentPadding = PaddingValues(horizontal = 24.dp * scale)) { tint ->
            Icon(Lucide.SlidersHorizontal, null, Modifier.size(24.dp * scale), tint)
            Spacer(Modifier.width(12.dp * scale)); Text("Filters", color = tint, fontSize = (24 * scale).sp)
        } else OutlinedButton(enabled = snapshot?.controls?.isNotEmpty() == true, onClick = { filters = true }) {
            Icon(Lucide.SlidersHorizontal, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Filters")
        }
    }
    if (television) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        LibraryListPicker(snapshot, query, change, true, scale)
        LibraryTypeFilters(snapshot, query, change, true, scale, selected, first)
        Spacer(Modifier.weight(1f)); filterButton()
    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LibraryListPicker(snapshot, query, change, false, scale)
            Spacer(Modifier.weight(1f)); filterButton()
        }
        LibraryTypeFilters(snapshot, query, change, false, scale, null, null)
    }
    if (filters && snapshot != null) LibraryFilterDialog(query, snapshot.controls, television, scale, { filters = false }, change)
}
