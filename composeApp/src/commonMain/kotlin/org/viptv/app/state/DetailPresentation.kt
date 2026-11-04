package org.viptv.app.state

import org.viptv.engine.wire.Card
import org.viptv.engine.wire.CardMetadata
import org.viptv.engine.wire.DetailSnapshot

// A later, sparser payload must not blank or swap what the detail shell already painted.
internal fun DetailSnapshot.withCardPresentation(card: Card): DetailSnapshot {
    val merged = item.mergePresentation(card)
    return if (merged == item) this else copy(item = merged)
}

internal fun Card.mergePresentation(painted: Card): Card = copy(
    title = painted.title.takeIf { it.isNotBlank() } ?: title,
    subtitle = keepFirst(painted.subtitle, subtitle),
    description = bestText(painted.description, description),
    landscape = painted.landscape ?: landscape,
    poster = painted.poster ?: poster,
    logo = painted.logo ?: logo,
    metadata = bestMetadata(metadata, painted.metadata),
    badges = (painted.badges + badges).distinct(),
)

private fun keepFirst(painted: String?, incoming: String?): String? =
    painted?.takeIf { it.isNotBlank() } ?: incoming

private fun bestText(painted: String?, incoming: String?): String? {
    val shown = painted?.takeIf { it.isNotBlank() }
    val loaded = incoming?.takeIf { it.isNotBlank() }
    return when {
        shown == null -> loaded
        loaded == null -> shown
        loaded.length > shown.length -> loaded
        else -> shown
    }
}

private fun bestMetadata(incoming: CardMetadata?, painted: CardMetadata?): CardMetadata? {
    if (incoming == null) return painted
    if (painted == null) return incoming
    if (incoming == painted) return incoming
    return CardMetadata(
        year = painted.year ?: incoming.year,
        runtime_minutes = painted.runtime_minutes ?: incoming.runtime_minutes,
        genres = richerList(painted.genres, incoming.genres),
        rating = painted.rating ?: incoming.rating,
        rating_votes = painted.rating_votes ?: incoming.rating_votes,
        certification = bestText(painted.certification, incoming.certification),
        alternate_titles = richerList(painted.alternate_titles, incoming.alternate_titles),
        cast = richerList(painted.cast, incoming.cast),
    )
}

private fun richerList(painted: List<String>, incoming: List<String>): List<String> = when {
    painted.isEmpty() -> incoming
    incoming.size > painted.size -> incoming
    else -> painted
}