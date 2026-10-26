package org.viptv.app.features.details

import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun TvSeasonSelector(seasons: List<Long?>, active: Long?, scale: Float, seasonFocus: FocusRequester,
    restoreEpisodes: () -> Boolean, select: (Long?) -> Unit, returnHero: () -> Unit,
    entered: () -> Unit, cancelEntry: () -> Unit = {}, focusChanged: (Boolean) -> Unit = {}) {

        LazyRow(modifier = Modifier.onFocusChanged { focusChanged(it.hasFocus) }, contentPadding = PaddingValues(horizontal = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            items(seasons, key = { it?.toString() ?: "unknown" }) { season ->
                var focused by remember { mutableStateOf(false) }
                Box(Modifier.height(52.dp * scale)
                    .then(if (season == active) Modifier.focusRequester(seasonFocus) else Modifier)
                    .onFocusChanged { focused = it.isFocused; if (it.isFocused) entered() }
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key != Key.DirectionDown) cancelEntry()
                        when {
                            it.type != KeyEventType.KeyDown -> false
                            it.key == Key.DirectionUp -> { returnHero(); true }
                            it.key == Key.DirectionDown -> restoreEpisodes()
                            else -> false
                        }
                    }.clip(RoundedCornerShape(26.dp * scale))
                    .background(if (focused) Palette.text else if (active == season) Palette.text.copy(alpha = .16f) else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { select(season) }
                    .padding(horizontal = 22.dp * scale), contentAlignment = Alignment.Center) {
                    Text(season?.let { "Season $it" } ?: "Episodes",
                        color = if (focused) Palette.background else if (active == season) Palette.text else Palette.secondary,
                        fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp,
                        fontWeight = if (active == season) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }

}
