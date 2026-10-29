package org.viptv.app.features.continuewatching

import org.viptv.app.components.isCaughtUp
import org.viptv.engine.wire.Card

internal fun continueWatchingActivation(play: (Card) -> Unit, open: (Card) -> Unit): (Card) -> Unit =
    { card -> if (card.isCaughtUp()) open(card) else play(card) }
