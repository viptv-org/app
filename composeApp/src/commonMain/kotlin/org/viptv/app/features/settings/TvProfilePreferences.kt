package org.viptv.app.features.settings

import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.connection.skipCredits
import org.viptv.app.connection.subtitleMode
import org.viptv.app.connection.withSubtitleMode
import org.viptv.app.player.languageName
import org.viptv.app.player.languageTag

internal fun ProfilePreferences.tvSettings() = TvSettingsPreferences(
    nextEpisode = next_episode_button,
    skipIntros = skip_intros,
    skipCredits = skipCredits,
    rememberSource = remembered_source_reuse,
    source = if (source_selection == "best") "Best match" else "Always ask",
    quality = max_quality_height?.let { if (it == 2160) "4K" else "${it}p" } ?: "Auto",
    hdr = hdrPreferenceLabel(hdr_preference),
    subtitleMode = subtitleMode,
    subtitleLanguage = languageName(preferred_subtitle_language) ?: preferred_subtitle_language ?: "Default",
    audioLanguage = languageName(preferred_audio_language) ?: preferred_audio_language ?: "Original language",
    subtitleSize = when (subtitle_font_scale) { .75 -> "Small"; 1.0 -> "Medium"; 1.25 -> "Large"; 1.5 -> "Extra large"; else -> "$subtitle_font_scale×" },
    subtitleColor = subtitle_text_color.removePrefix("#").toLongOrNull(16)?.or(0xFF000000) ?: 0xFFFFFFFF,
    subtitleBackground = if (subtitle_background_color == "#00000000") "None" else "Box",
    subtitleBackgroundColor = subtitle_background_color,
)

internal fun TvSettingsPreferences.profilePreferences(base: ProfilePreferences = ProfilePreferences()) = base.copy(
    next_episode_button = nextEpisode,
    skip_intros = skipIntros,
    skip_credits = skipCredits.takeUnless { base.skip_credits == null && it },
    remembered_source_reuse = rememberSource,
    preferred_audio_language = base.preferred_audio_language.takeIf { languageName(it) == audioLanguage } ?: languageTag(audioLanguage),
    preferred_subtitle_language = base.preferred_subtitle_language.takeIf { languageName(it) == subtitleLanguage } ?: languageTag(subtitleLanguage),
    source_selection = if (source == "Best match") "best" else "ask",
    max_quality_height = if (quality == "4K") 2160 else quality.removeSuffix("p").toIntOrNull(),
    hdr_preference = hdrPreferenceValue(hdr),
    subtitle_font_scale = when (subtitleSize) { "Small" -> .75; "Medium" -> 1.0; "Large" -> 1.25; "Extra large" -> 1.5; else -> subtitleSize.removeSuffix("×").toDoubleOrNull() ?: base.subtitle_font_scale },
    subtitle_text_color = "#" + (subtitleColor and 0xFFFFFF).toString(16).padStart(6, '0').uppercase(),
    subtitle_background_color = if (subtitleBackground == "None") "#00000000" else
        subtitleBackgroundColor.takeUnless { it == "#00000000" } ?: "#B3000000",
).let { if (it.subtitleMode == subtitleMode) it else it.withSubtitleMode(subtitleMode) }

internal fun TvSettingsPreferences.audioLanguageTag(): String? = languageTag(audioLanguage)
