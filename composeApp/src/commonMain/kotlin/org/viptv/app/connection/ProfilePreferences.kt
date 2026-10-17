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
)

@Serializable
data class PreferencesSnapshot(val revision: Long = 0, val preferences: ProfilePreferences = ProfilePreferences())

private val preferencesCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

suspend fun RemoteConnection.profilePreferences(profile: String): PreferencesSnapshot =
    preferencesCodec.decodeFromJsonElement(call("/api/profiles/${profile.encodeURLPathPart()}/preferences"))

suspend fun RemoteConnection.updateProfilePreferences(profile: String, revision: Long, preferences: ProfilePreferences): PreferencesSnapshot =
    preferencesCodec.decodeFromJsonElement(call("/api/profiles/${profile.encodeURLPathPart()}/preferences", HttpMethod.Put,
        buildJsonObject { put("revision", revision); put("preferences", preferencesCodec.encodeToJsonElement(preferences)) }))
