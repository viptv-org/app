package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class ProfilePreferences(
    val preferred_audio_language: String? = null,
    val preferred_subtitle_language: String? = null,
    val subtitles_enabled: Boolean = true,
    val source_selection: String = "ask",
    val max_quality_height: Int? = null,
    val subtitle_font_scale: Double = 1.0,
    val subtitle_text_color: String = "#FFFFFF",
    val subtitle_background_color: String = "#00000000",
    val hdr_preference: String = "any",
    val subtitle_mode: String? = null,
)

private val subtitleModes = setOf("always", "foreign", "never")

val ProfilePreferences.subtitleMode: String
    get() = subtitle_mode?.takeIf { it in subtitleModes } ?: if (subtitles_enabled) "always" else "never"

fun ProfilePreferences.withSubtitleMode(mode: String): ProfilePreferences =
    copy(subtitle_mode = mode.takeIf { it in subtitleModes } ?: "always").let { it.copy(subtitles_enabled = it.subtitle_mode != "never") }

@Serializable
data class PreferencesSnapshot(val revision: Long = 0, val preferences: ProfilePreferences = ProfilePreferences())

private val preferencesCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// Servers that predate `hdr_preference` reject unknown fields, so its default is left out.
internal fun profilePreferencesWire(preferences: ProfilePreferences): JsonObject {
    val mode = preferences.subtitleMode
    val encoded = preferencesCodec.encodeToJsonElement(preferences.copy(subtitles_enabled = mode != "never")).jsonObject.toMutableMap()
    if (preferences.hdr_preference == "any") encoded -= "hdr_preference"
    if (mode == "foreign") encoded["subtitle_mode"] = JsonPrimitive(mode) else encoded -= "subtitle_mode"
    return JsonObject(encoded)
}

suspend fun RemoteConnection.profilePreferences(profile: String): PreferencesSnapshot =
    preferencesCodec.decodeFromJsonElement(call("/api/profiles/${profile.encodeURLPathPart()}/preferences"))

suspend fun RemoteConnection.updateProfilePreferences(profile: String, revision: Long, preferences: ProfilePreferences): PreferencesSnapshot =
    preferencesCodec.decodeFromJsonElement(call("/api/profiles/${profile.encodeURLPathPart()}/preferences", HttpMethod.Put,
        buildJsonObject { put("revision", revision); put("preferences", profilePreferencesWire(preferences)) }))
