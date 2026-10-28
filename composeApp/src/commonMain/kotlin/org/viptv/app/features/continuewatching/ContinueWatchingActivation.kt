package org.viptv.app.features.continuewatching

import org.viptv.app.components.isCaughtUp
import org.viptv.engine.wire.Card

/**
 * What activating a Continue Watching card does (app#132). A caught-up card names the episode the
 * viewer already finished, so it has nothing to resume: it opens the show's Details on that
 * episode instead of the source picker for an episode that is watched. Every other card starts
 * what it names, as it always has (app#11).
 */
internal fun continueWatchingActivation(play: (Card) -> Unit, open: (Card) -> Unit): (Card) -> Unit =
    { card -> if (card.isCaughtUp()) open(card) else play(card) }