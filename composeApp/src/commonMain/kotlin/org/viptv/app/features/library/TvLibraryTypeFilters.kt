package org.viptv.app.features.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@Composable
internal fun TvLibraryTypeFilters(snapshot: LibrarySnapshot?, query: LibraryQuery, change: (LibraryQuery) -> Unit,
    scale: Float, selected: FocusRequester?, first: FocusRequester?) {
    val types = snapshot?.controls?.find { it.id == "type" }?.choices.orEmpty()
    Row(Modifier.background(Palette.raised, RoundedCornerShape(32.dp * scale)).padding(6.dp * scale)) {
        types.forEach { type -> TvAction(Modifier.width((if (types.size > 3) 140 else 168).dp * scale).height(52.dp * scale)
            .focusProperties { if (first != null) down = first }, scale,
            requester = if (type.value == query.type) selected else null,
            activate = { change(query.copy(type = type.value, offset = 0)) },
            background = if (query.type == type.value) Palette.background else Color.Transparent,
            cornerRadius = 26.dp * scale) { Text(type.label, color = it, fontSize = (24 * scale).sp) } }
    }
}
