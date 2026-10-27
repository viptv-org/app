package org.viptv.app.features.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Device-local Display choices (Tv3SetDisplay). They are not profile
 * preferences: overscan and motion belong to the panel, so two TVs on one
 * account keep their own. Stored as one JSON record in platform key-value
 * storage instead of the profile preference API.
 */
@Serializable
internal data class TvDisplayPreferences(
    val accent: Long = 0xFFF5C542,
    val ratings: Boolean = true,
    val textSize: String = "Default",
    val reduceMotion: String = "Follow TV",
    val screenSaver: String = "After 5 minutes",
    val fitToScreen: Boolean = false,
)

// A newer build may add fields; an older one must not throw the record away.
private val displayJson = Json { ignoreUnknownKeys = true }

/** A missing or unreadable record falls back to the board defaults; never throws. */
internal fun decodeTvDisplay(raw: String?): TvDisplayPreferences = raw
    ?.let { runCatching { displayJson.decodeFromString(TvDisplayPreferences.serializer(), it) }.getOrNull() }
    ?: TvDisplayPreferences()

internal fun encodeTvDisplay(value: TvDisplayPreferences): String =
    displayJson.encodeToString(TvDisplayPreferences.serializer(), value)

/** Reduce motion: `On` and `Off` decide; `Follow TV` follows the device's own setting. */
internal fun reducedMotion(display: TvDisplayPreferences, system: Boolean): Boolean = when (display.reduceMotion) {
    "On" -> true
    "Off" -> false
    else -> system
}
