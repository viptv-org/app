package org.viptv.app.features.settings

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class TvDisplayPreferences(
    val accent: Long = 0xFFF5C542,
    val ratings: Boolean = true,
    val textSize: String = "Default",
    val reduceMotion: String = "Follow TV",
    val fitToScreen: Boolean = false,
)

// A newer build may add fields; an older one must not throw the record away.
private val displayJson = Json { ignoreUnknownKeys = true }

internal fun decodeTvDisplay(raw: String?): TvDisplayPreferences = raw
    ?.let { runCatching { displayJson.decodeFromString(TvDisplayPreferences.serializer(), it) }.getOrNull() }
    ?: TvDisplayPreferences()

internal fun encodeTvDisplay(value: TvDisplayPreferences): String =
    displayJson.encodeToString(TvDisplayPreferences.serializer(), value)

internal fun reducedMotion(display: TvDisplayPreferences, system: Boolean): Boolean = when (display.reduceMotion) {
    "On" -> true
    "Off" -> false
    else -> system
}

internal fun accentColor(display: TvDisplayPreferences): Color = Color(display.accent)

internal fun showRatings(display: TvDisplayPreferences): Boolean = display.ratings

internal fun tvTextScale(display: TvDisplayPreferences): Float = when (display.textSize) {
    "Large" -> 1.15f
    "Larger" -> 1.3f
    else -> 1f
}

internal fun fitToScreen(display: TvDisplayPreferences): Boolean = display.fitToScreen
