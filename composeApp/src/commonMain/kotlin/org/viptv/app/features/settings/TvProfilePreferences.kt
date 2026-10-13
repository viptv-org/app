package org.viptv.app.features.settings

import org.viptv.app.connection.ProfilePreferences

private val languages = mapOf("English" to "en", "Spanish" to "es", "Portuguese" to "pt", "French" to "fr", "German" to "de")

internal fun ProfilePreferences.tvSettings() = TvSettingsPreferences(
    source = if (source_selection == "best") "Best match" else "Always ask",
    quality = max_quality_height?.let { if (it == 2160) "4K" else "${it}p" } ?: "Best available",
    startSubtitles = if (subtitles_enabled) "On" else "Off",
    subtitleLanguage = languages.entries.find { it.value == preferred_subtitle_language }?.key ?: preferred_subtitle_language ?: "Default",
    audioLanguage = languages.entries.find { it.value == preferred_audio_language }?.key ?: preferred_audio_language ?: "Original",
)

internal fun TvSettingsPreferences.profilePreferences() = ProfilePreferences(
    preferred_audio_language = languages[audioLanguage] ?: audioLanguage.takeUnless { it == "Original" },
    preferred_subtitle_language = languages[subtitleLanguage] ?: subtitleLanguage.takeUnless { it == "Default" },
    subtitles_enabled = startSubtitles == "On",
    source_selection = if (source == "Best match") "best" else "ask",
    max_quality_height = if (quality == "4K") 2160 else quality.removeSuffix("p").toIntOrNull(),
)
