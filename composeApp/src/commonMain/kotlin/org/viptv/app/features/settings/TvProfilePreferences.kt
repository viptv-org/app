package org.viptv.app.features.settings

import org.viptv.app.connection.ProfilePreferences

private val languages = mapOf("English" to "en", "Spanish" to "es", "Portuguese" to "pt", "French" to "fr", "German" to "de")

internal fun ProfilePreferences.tvSettings() = TvSettingsPreferences(
    source = if (source_selection == "best") "Best match" else "Always ask",
    quality = max_quality_height?.let { if (it == 2160) "4K" else "${it}p" } ?: "Best available",
    startSubtitles = if (subtitles_enabled) "On" else "Off",
    subtitleLanguage = languages.entries.find { it.value == preferred_subtitle_language }?.key ?: preferred_subtitle_language ?: "Default",
    audioLanguage = languages.entries.find { it.value == preferred_audio_language }?.key ?: preferred_audio_language ?: "Original",
    subtitleSize = when (subtitle_font_scale) { .75 -> "Small"; 1.0 -> "Medium"; 1.25 -> "Large"; 1.5 -> "Extra large"; else -> "$subtitle_font_scale×" },
    subtitleColor = subtitle_text_color.removePrefix("#").toLongOrNull(16)?.or(0xFF000000) ?: 0xFFFFFFFF,
    subtitleBackground = if (subtitle_background_color == "#00000000") "None" else "Box",
    subtitleBackgroundColor = subtitle_background_color,
)

internal fun TvSettingsPreferences.profilePreferences(base: ProfilePreferences = ProfilePreferences()) = base.copy(
    preferred_audio_language = languages[audioLanguage] ?: audioLanguage.takeUnless { it == "Original" },
    preferred_subtitle_language = languages[subtitleLanguage] ?: subtitleLanguage.takeUnless { it == "Default" },
    subtitles_enabled = startSubtitles == "On",
    source_selection = if (source == "Best match") "best" else "ask",
    max_quality_height = if (quality == "4K") 2160 else quality.removeSuffix("p").toIntOrNull(),
    subtitle_font_scale = when (subtitleSize) { "Small" -> .75; "Medium" -> 1.0; "Large" -> 1.25; "Extra large" -> 1.5; else -> subtitleSize.removeSuffix("×").toDoubleOrNull() ?: base.subtitle_font_scale },
    subtitle_text_color = "#" + (subtitleColor and 0xFFFFFF).toString(16).padStart(6, '0').uppercase(),
    subtitle_background_color = if (subtitleBackground == "None") "#00000000" else
        subtitleBackgroundColor.takeUnless { it == "#00000000" } ?: "#B3000000",
)
