package org.viptv.app.components

import org.viptv.engine.wire.Card
import kotlin.math.roundToInt

internal fun Card.metadataParts(): List<String> = metadata?.let { value -> buildList {
    value.year?.let { add(it.toString()) }
    value.runtime_minutes?.takeIf { it > 0 }?.let { add(if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m") }
    value.genres.take(3).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" / ")) }
    value.rating?.takeIf { it > 0 }?.let { add("★ ${(it * 10).roundToInt() / 10.0}") }
} }.orEmpty().ifEmpty { subtitle?.split('·')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty() }

/** Card *state* labels: drawn on the art or as meta text, never as a meta chip (Tv3CardStates). */
private val stateLabels = setOf("watched", "new episode", "returning", "ended", "live")

private val newEpisodesCount = Regex("""\+\d+ new episodes""")

/**
 * Continue Watching labels a series with episodes released since the profile
 * last finished one: "New episode", then "+2 new episodes" while more arrive
 * (backend `continuation::new_episodes_badge`). Exact server strings only.
 */
internal fun isNewEpisodesBadge(badge: String): Boolean =
    badge == "New episode" || newEpisodesCount.matches(badge)

internal fun Card.newEpisodesBadge(): String? = badges.firstOrNull(::isNewEpisodesBadge)

/** The one label a phone/desktop/web card draws on its art: new episodes first. */
internal fun Card.cardChip(): String? = newEpisodesBadge() ?: badges.firstOrNull { !it.equals("Simkl", true) && !it.equals(WATCHED_BADGE, true) }

/** Capability chips in the order every board draws them: 4K, HDR, HD, 5.1, CC. */
private val capabilityChipOrder = listOf("4K", "HDR", "HD", "5.1", "CC")

/**
 * DATA-04: one defined mapping from a token a provider already puts in [Card.badges]
 * to the chip the boards draw. No alias invents a chip the boards never show, so a
 * title carrying no capability token draws no chip at all (Tv3CardStates: a missing
 * value draws nothing).
 */
private fun canonicalChip(token: String): String? = when (token.trim().lowercase()) {
    "4k", "uhd", "2160p" -> "4K"
    "hdr", "hdr10", "hdr10+", "dv", "dolby vision" -> "HDR"
    "hd", "1080p", "720p" -> "HD"
    "5.1", "5.1ch", "dd5.1", "ac3", "eac3" -> "5.1"
    "cc", "sdh", "closed caption", "closed captions" -> "CC"
    else -> null
}

/** Tokens describing picture, audio or subtitle capability rather than card state. */
private val capabilityTokens = setOf(
    "4k", "uhd", "2160p", "hdr", "hdr10", "hdr10+", "dv", "dolby vision",
    "hd", "1080p", "720p", "sd", "480p",
    "5.1", "5.1ch", "dd5.1", "ac3", "eac3", "atmos", "dolby", "dts",
    "cc", "sdh", "closed caption", "closed captions",
)

internal fun isCapabilityBadge(badge: String): Boolean = badge.trim().lowercase() in capabilityTokens

/**
 * Certification trails the capability chips, per Tv3CardStates ("… then the
 * certification chip"), Tv3Home and Tv3Movie. Tv3Title/D3Title/Tv3Browse draw it
 * first; that is one slot on a board, not a second mapping, so it is left to a
 * design decision rather than a per-call-site override.
 */
internal fun Card.presentationBadges(): List<String> {
    val tokens = badges.filterNot { it.equals("Simkl", true) }.map { it.trim() }.filter { it.isNotEmpty() }
    val capabilities = capabilityChipOrder.filter { chip -> tokens.any { canonicalChip(it) == chip } }
        // 4K subsumes HD; the boards never draw both (Tv3Home "4K HDR 5.1").
        .let { chips -> if ("4K" in chips) chips - "HD" else chips }
    val others = tokens.filterNot(::isCapabilityBadge).filterNot { it.lowercase() in stateLabels || isNewEpisodesBadge(it) }.distinct()
    return capabilities + listOfNotNull(metadata?.certification?.takeIf { it.isNotBlank() }) + others
}
