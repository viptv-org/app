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
import org.viptv.engine.wire.*

/**
 * TV My List: the list picker, the type segments and Filters. Phone, tablet, desktop and web
 * use the shared filter bar ([LibraryFilterBar], app#117); TV moves to it in a follow-up.
 */
@Composable
internal fun TvLibraryHeader(snapshot: LibrarySnapshot?, query: LibraryQuery, change: (LibraryQuery) -> Unit,
    scale: Float, selected: FocusRequester?, first: FocusRequester?) {
    var filters by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        TvLibraryListPicker(snapshot, query, change, scale)
        TvLibraryTypeFilters(snapshot, query, change, scale, selected, first)
        Spacer(Modifier.weight(1f))
        TvAction(Modifier.height(64.dp * scale).focusProperties { if (first != null) down = first }, scale,
            enabled = snapshot?.controls?.isNotEmpty() == true, activate = { filters = true },
            cornerRadius = 32.dp * scale, contentPadding = PaddingValues(horizontal = 24.dp * scale)) { tint ->
            Icon(Lucide.SlidersHorizontal, null, Modifier.size(24.dp * scale), tint)
            Spacer(Modifier.width(12.dp * scale)); Text("Filters", color = tint, fontSize = (24 * scale).sp)
        }
    }
    if (filters && snapshot != null) TvLibraryFilterSheet(query, snapshot.controls, scale, { filters = false }, change)
}
