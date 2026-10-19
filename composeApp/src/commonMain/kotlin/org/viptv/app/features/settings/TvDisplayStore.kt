package org.viptv.app.features.settings

import coil3.PlatformContext
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

/** One record in, one record out; the platform decides where the string lives. */
internal interface TvDisplayStore {
    fun read(): String?
    fun write(value: String)
}

// A newer build may add fields; an older one must not throw the record away.
private val displayJson = Json { ignoreUnknownKeys = true }

/**
 * [context] is the Android Context (SharedPreferences has no other attach point
 * in this module); desktop, iOS and the browser already have a per-user store
 * and ignore it.
 */
internal expect fun tvDisplayStore(context: PlatformContext): TvDisplayStore

/** A missing or unreadable record falls back to the board defaults; never throws. */
internal fun decodeTvDisplay(raw: String?): TvDisplayPreferences = raw
    ?.let { runCatching { displayJson.decodeFromString(TvDisplayPreferences.serializer(), it) }.getOrNull() }
    ?: TvDisplayPreferences()

internal fun encodeTvDisplay(value: TvDisplayPreferences): String =
    displayJson.encodeToString(TvDisplayPreferences.serializer(), value)
