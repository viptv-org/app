package org.viptv.app.features.live

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

@Composable
internal fun TvLiveFilters(categories: List<String>, selected: String, choose: (String) -> Unit, scale: Float,
    filter: FocusRequester, first: FocusRequester?, modifier: Modifier, labels: Map<String, String> = emptyMap()) {
    Row(modifier.focusProperties { onEnter = { filter.requestFocus() } }.focusGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
        (listOf("All") + categories).distinct().forEach { option ->
            TvAction(Modifier.height(52.dp * scale).focusProperties { if (first != null) down = first }, scale,
                requester = if (option == selected) filter else null, activate = { choose(option) }, cornerRadius = 26.dp * scale,
                background = if (option == selected) Palette.text.copy(alpha = .16f) else androidx.compose.ui.graphics.Color.Transparent,
                contentPadding = PaddingValues(horizontal = 22.dp * scale)) {
                Text(labels[option] ?: option, color = if (option == selected || it == Palette.background) it else Palette.secondary,
                    fontSize = (22 * scale).sp, lineHeight = (26 * scale).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
