package org.viptv.app.features.library

import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.*
import org.viptv.app.components.FilterChip

@Composable
internal fun LibraryTypeFilters(snapshot: LibrarySnapshot?, query: LibraryQuery, change: (LibraryQuery) -> Unit,
    television: Boolean, scale: Float, selected: FocusRequester?, first: FocusRequester?) {
    val types = snapshot?.controls?.find { it.id == "type" }?.choices.orEmpty()
    if (television) Row(Modifier.background(Palette.raised, RoundedCornerShape(32.dp * scale)).padding(6.dp * scale)) {
        types.forEach { type -> TvAction(Modifier.width((if (types.size > 3) 140 else 168).dp * scale).height(52.dp * scale)
            .focusProperties { if (first != null) down = first }, scale,
            requester = if (type.value == query.type) selected else null,
            activate = { change(query.copy(type = type.value, offset = 0)) },
            background = if (query.type == type.value) Palette.background else Color.Transparent,
            cornerRadius = 26.dp * scale) { Text(type.label, color = it, fontSize = (24 * scale).sp) } }
    } else LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        // A chip row pinned in the Library grid: the wheel over it scrolls it sideways (app#59).
        wheelScrollsRow = true) {
        items(types, key = { it.value }) { type -> FilterChip(selected = type.value == query.type,
            onClick = { change(query.copy(type = type.value, offset = 0)) }, label = { Text(type.label, maxLines = 1) }) }
    }
}
