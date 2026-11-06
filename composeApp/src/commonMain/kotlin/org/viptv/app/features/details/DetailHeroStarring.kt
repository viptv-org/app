package org.viptv.app.features.details

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

internal const val STARRING_LIMIT = 3

/** The top-billed names the hero line carries, in billing order; null when the title names nobody. */
internal fun Card.heroStarring(): String? = metadata?.cast.orEmpty()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .take(STARRING_LIMIT)
    .takeIf { it.isNotEmpty() }
    ?.joinToString(", ")

/** The one Starring line: under the description on every hero, one muted line, omitted without a cast. */
@Composable
internal fun HeroStarring(card: Card, fontSize: TextUnit, lineHeight: TextUnit, modifier: Modifier = Modifier) {
    val names = card.heroStarring() ?: return
    Text("Starring $names", modifier, color = Palette.secondary, fontSize = fontSize, lineHeight = lineHeight,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}