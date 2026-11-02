package org.viptv.app.features.details

import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable

private sealed interface SeasonStripEntry {
    data class Season(val number: Long?) : SeasonStripEntry
    data object Jump : SeasonStripEntry
}

@Composable
internal fun TvSeasonSelector(seasons: List<Long?>, active: Long?, scale: Float, seasonFocus: FocusRequester,
    restoreEpisodes: () -> Boolean, select: (Long?) -> Unit, returnHero: () -> Unit,
    entered: () -> Unit, cancelEntry: () -> Unit = {}, focusChanged: (Boolean) -> Unit = {},
    jumpFocus: FocusRequester? = null, openJump: (() -> Unit)? = null) {
    val keys: (KeyEvent) -> Boolean = {
        if (it.type == KeyEventType.KeyDown && it.key != Key.DirectionDown) cancelEntry()
        when {
            it.type != KeyEventType.KeyDown -> false
            it.key == Key.DirectionUp -> { returnHero(); true }
            it.key == Key.DirectionDown -> restoreEpisodes()
            else -> false
        }
    }
    val jumpAfter = if (openJump == null) -1 else seasons.indexOf(active).takeIf { it >= 0 } ?: seasons.lastIndex
    val strip = remember(seasons, jumpAfter) { buildList {
        seasons.forEachIndexed { index, season ->
            add(SeasonStripEntry.Season(season))
            if (index == jumpAfter) add(SeasonStripEntry.Jump)
        }
        if (jumpAfter < 0 && openJump != null) add(SeasonStripEntry.Jump)
    } }

        LazyRow(modifier = Modifier.onFocusChanged { focusChanged(it.hasFocus) }, contentPadding = PaddingValues(horizontal = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            items(strip, key = { entry -> when (entry) {
                is SeasonStripEntry.Season -> entry.number?.toString() ?: "unknown"
                SeasonStripEntry.Jump -> "episode-jump"
            } }) { entry ->
                when (entry) {
                    is SeasonStripEntry.Season -> {
                        val season = entry.number
                        var focused by remember { mutableStateOf(false) }
                        Box(Modifier.height(52.dp * scale)
                            .then(if (season == active) Modifier.focusRequester(seasonFocus) else Modifier)
                            .onFocusChanged { focused = it.isFocused; if (it.isFocused) entered() }
                            .onPreviewKeyEvent(keys).clip(RoundedCornerShape(26.dp * scale))
                            .background(if (focused) Palette.text else if (active == season) Palette.text.copy(alpha = .16f) else Color.Transparent)
                            .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, shape = RoundedCornerShape(26.dp * scale)) { select(season) }
                            .padding(horizontal = 22.dp * scale), contentAlignment = Alignment.Center) {
                            Text(season?.let { "Season $it" } ?: "Episodes",
                                color = if (focused) Palette.background else if (active == season) Palette.text else Palette.secondary,
                                fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp,
                                fontWeight = if (active == season) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                    SeasonStripEntry.Jump -> {
                        val open = openJump
                        var focused by remember { mutableStateOf(false) }
                        if (open != null) Box(Modifier.height(52.dp * scale)
                            .then(if (jumpFocus != null) Modifier.focusRequester(jumpFocus) else Modifier)
                            .onFocusChanged { focused = it.isFocused; if (it.isFocused) entered() }
                            .onPreviewKeyEvent(keys).clip(RoundedCornerShape(26.dp * scale))
                            .background(if (focused) Palette.text else Palette.text.copy(alpha = .08f))
                            .semantics(mergeDescendants = true) { contentDescription = EPISODE_JUMP_CHIP_DESCRIPTION }
                            .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                                role = Role.Button, shape = RoundedCornerShape(26.dp * scale)) { open() }
                            .padding(horizontal = 22.dp * scale), contentAlignment = Alignment.Center) {
                            Text(EPISODE_JUMP_CHIP_LABEL, color = if (focused) Palette.background else Palette.secondary,
                                fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

}
