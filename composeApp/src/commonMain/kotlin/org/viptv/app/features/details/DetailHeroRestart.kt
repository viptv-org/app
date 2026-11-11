package org.viptv.app.features.details

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TextButton
import org.viptv.engine.wire.Card

internal const val RESTART_LABEL = "Play from the beginning"

/** The card a hero starts over: the episode it resumes, or the title itself, while that is part way through. */
internal fun restartTarget(card: Card, resumeEpisode: Card?): Card? =
    (resumeEpisode ?: card.takeIf(::resuming))?.takeIf(::resuming)

/** The restart beside a hero's Resume, on every layout that has the width for a label. */
@Composable
internal fun HeroRestart(card: Card, resumeEpisode: Card?, playFromStart: ((Card) -> Unit)?, modifier: Modifier = Modifier, fontSize: TextUnit = 13.sp) {
    val restart = restartTarget(card, resumeEpisode) ?: return
    val start = playFromStart ?: return
    // No content padding: the label starts on the hero's content edge, in line with the Play above it.
    TextButton(onClick = { start(restart) }, modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Text(RESTART_LABEL, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
    }
}
