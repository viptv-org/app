package org.viptv.app.features.details

import org.viptv.app.connection.airDateLabel
import org.viptv.app.connection.airDateWithYearLabel
import org.viptv.engine.wire.Card

internal const val EPISODE_UPCOMING_LABEL = "Upcoming"
internal const val EPISODE_UPCOMING_NOTICE = "This episode isn't out yet."

private val EPISODE_ORDER = compareBy<Card>(
    { it.identity.episode?.season ?: Long.MIN_VALUE / 2 },
    { it.identity.episode?.episode ?: Long.MIN_VALUE / 2 },
)

internal fun lastReleasedOrder(episodes: List<Card>, nowMillis: Long): Card? =
    episodes.asSequence()
        .filter { it.released_at_millis != null && it.released_at_millis <= nowMillis }
        .maxWithOrNull(EPISODE_ORDER)

internal fun episodeUpcoming(card: Card, lastReleased: Card?, nowMillis: Long): Boolean {
    val releasedAt = card.released_at_millis
    if (releasedAt != null) return releasedAt > nowMillis
    return lastReleased != null && EPISODE_ORDER.compare(card, lastReleased) > 0
}

internal fun Card.episodeDateText(): String? = released_at_millis?.let(::airDateWithYearLabel)

internal fun Card.episodeUpcomingText(): String =
    released_at_millis?.let { "$EPISODE_UPCOMING_LABEL · Airs ${airDateLabel(it)}" }
        ?: EPISODE_UPCOMING_LABEL