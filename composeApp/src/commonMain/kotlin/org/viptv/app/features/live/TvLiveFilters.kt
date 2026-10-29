package org.viptv.app.features.live

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.components.tvRingReach
import org.viptv.app.components.tvRingRoom
import org.viptv.app.theme.Palette

@Composable
internal fun TvLiveFilters(categories: List<String>, selected: String, choose: (String) -> Unit, scale: Float,
    filter: FocusRequester, first: FocusRequester?, modifier: Modifier, labels: Map<String, String> = emptyMap(), favoritesSelected: Boolean = false, favorites: (() -> Unit)? = null,
    sources: Map<String, String> = emptyMap(), selectedSource: String? = null, sourceChanged: ((String) -> Unit)? = null, busy: Boolean = false) {
    val reach = tvRingReach(scale)
    org.viptv.app.components.TvFocusScroll(horizontal = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = reach)) { Row(modifier.tvRingRoom(reach, vertical = false).horizontalScroll(rememberScrollState()).padding(horizontal = reach)
        .focusProperties { onEnter = { filter.requestFocus() } }.focusGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
        if (sources.size > 1 && sourceChanged != null) LiveSourceSelector(sources, selectedSource, sourceChanged, enabled = !busy, television = true, scale = scale)
        (listOf("All") + categories).distinct().forEach { option ->
            TvAction(Modifier.height(52.dp * scale).focusProperties { if (first != null) down = first }, scale,
                requester = if (option == selected && !favoritesSelected) filter else null, activate = { choose(option) }, cornerRadius = 26.dp * scale,
                background = if (option == selected && !favoritesSelected) Palette.text.copy(alpha = .16f) else androidx.compose.ui.graphics.Color.Transparent,
                contentPadding = PaddingValues(horizontal = 22.dp * scale)) {
                Text(labels[option] ?: option, color = if (option == selected && !favoritesSelected || it == Palette.background) it else Palette.secondary,
                    fontSize = (22 * scale).sp, lineHeight = (26 * scale).sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (favorites != null) TvAction(Modifier.height(52.dp * scale).focusProperties { if (first != null) down = first }, scale,
            requester = if (favoritesSelected) filter else null, activate = favorites, cornerRadius = 26.dp * scale,
            background = if (favoritesSelected) Palette.text.copy(alpha = .16f) else androidx.compose.ui.graphics.Color.Transparent,
            contentPadding = PaddingValues(horizontal = 22.dp * scale)) {
            Text("Favourites", color = it, fontSize = (22 * scale).sp, lineHeight = (26 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
    } }
}
