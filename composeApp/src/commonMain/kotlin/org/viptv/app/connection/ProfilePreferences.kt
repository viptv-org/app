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
)

@Serializable
data class PreferencesSnapshot(val revision: Long = 0, val preferences: ProfilePreferences = ProfilePreferences())

private val preferencesCodec = Json { ignoreUnknownKeys = true }

suspend fun RemoteConnection.profilePreferences(profile: String): PreferencesSnapshot =
    preferencesCodec.decodeFromJsonElement(call("/api/v3/profiles/${profile.encodeURLPathPart()}/preferences"))

suspend fun RemoteConnection.updateProfilePreferences(profile: String, revision: Long, preferences: ProfilePreferences): PreferencesSnapshot =
    preferencesCodec.decodeFromJsonElement(call("/api/v3/profiles/${profile.encodeURLPathPart()}/preferences", HttpMethod.Put,
        buildJsonObject { put("revision", revision); put("preferences", preferencesCodec.encodeToJsonElement(preferences)) }))
