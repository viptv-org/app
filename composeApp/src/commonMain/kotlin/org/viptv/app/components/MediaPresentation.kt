package org.viptv.app.components

import org.viptv.app.connection.airDateLabel
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.NextEpisode
import kotlin.math.roundToInt

internal fun Card.metadataParts(): List<String> = metadata?.let { value -> buildList {
    value.year?.let { add(it.toString()) }
    value.runtime_minutes?.takeIf { it > 0 }?.let { add(if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m") }
    value.genres.take(3).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" / ")) }
    value.rating?.takeIf { it > 0 }?.let { add("★ ${(it * 10).roundToInt() / 10.0}") }
} }.orEmpty().ifEmpty { subtitle?.split('·')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty() }

private val stateLabels = setOf("watched", "new episode", "returning", "ended", "live")

private val newEpisodesCount = Regex("""\+(\d+)""")

internal fun isNewEpisodesBadge(badge: String): Boolean =
    badge == "New episode" || newEpisodesCount.matches(badge)

internal fun newEpisodesDescription(badge: String): String =
    newEpisodesCount.matchEntire(badge)?.groupValues?.get(1)?.let { count ->
        if (count == "1") "1 new episode" else "$count new episodes"
    } ?: badge

internal fun Card.newEpisodesBadge(): String? = badges.firstOrNull(::isNewEpisodesBadge)

internal fun Card.isCaughtUp(): Boolean =
    identity.kind == "series" && identity.episode != null &&
        progress == null && subtitle == null && isWatched()

internal fun EpisodeCoordinates.caption(): String =
    season?.let { "S$it E$episode" } ?: "E$episode"

internal fun Card.caughtUpCaption(): String? =
    identity.episode?.takeIf { isCaughtUp() }?.let { "${it.caption()} · $WATCHED_BADGE" }

internal fun NextEpisode.caughtUpNextCaption(): String? = identity.episode?.let { episode ->
    released_at_millis?.let { "${episode.caption()} · ${airDateLabel(it)}" } ?: "${episode.caption()} coming soon"
}

internal fun Card.cardChip(): String? = newEpisodesBadge() ?: badges.firstOrNull { !it.equals("Simkl", true) && !it.equals(WATCHED_BADGE, true) }

private val capabilityChipOrder = listOf("4K", "HDR", "HD", "5.1", "CC")

private fun canonicalChip(token: String): String? = when (token.trim().lowercase()) {
    "4k", "uhd", "2160p" -> "4K"
    "hdr", "hdr10", "hdr10+", "dv", "dolby vision" -> "HDR"
    "hd", "1080p", "720p" -> "HD"
    "5.1", "5.1ch", "dd5.1", "ac3", "eac3" -> "5.1"
    "cc", "sdh", "closed caption", "closed captions" -> "CC"
    else -> null
}

private val capabilityTokens = setOf(
    "4k", "uhd", "2160p", "hdr", "hdr10", "hdr10+", "dv", "dolby vision",
    "hd", "1080p", "720p", "sd", "480p",
    "5.1", "5.1ch", "dd5.1", "ac3", "eac3", "atmos", "dolby", "dts",
    "cc", "sdh", "closed caption", "closed captions",
)

internal fun isCapabilityBadge(badge: String): Boolean = badge.trim().lowercase() in capabilityTokens

internal fun Card.presentationBadges(): List<String> {
    val tokens = badges.filterNot { it.equals("Simkl", true) }.map { it.trim() }.filter { it.isNotEmpty() }
    val capabilities = capabilityChipOrder.filter { chip -> tokens.any { canonicalChip(it) == chip } }
        // 4K subsumes HD; the boards never draw both.
        .let { chips -> if ("4K" in chips) chips - "HD" else chips }
    val others = tokens.filterNot(::isCapabilityBadge).filterNot { it.lowercase() in stateLabels || isNewEpisodesBadge(it) }.distinct()
    return capabilities + listOfNotNull(metadata?.certification?.takeIf { it.isNotBlank() }) + others
}
