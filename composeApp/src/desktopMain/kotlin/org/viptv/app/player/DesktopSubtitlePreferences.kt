package org.viptv.app.player

import io.github.yuroyami.kiteplayer.SubtitleConfig
import io.github.yuroyami.kiteplayer.subtitle.SubtitleStyleOverride
import org.viptv.app.connection.ProfilePreferences

/** Keep authored styles for defaults; apply only explicit supported viewer overrides. */
internal fun desktopSubtitleConfig(preferences: ProfilePreferences): SubtitleConfig {
    val foreground = desktopCaptionColor(preferences.subtitle_text_color)?.takeUnless { it == -1 }
    val background = desktopCaptionColor(preferences.subtitle_background_color)?.takeUnless { it ushr 24 == 0 }
    val style = if (foreground != null || background != null)
        SubtitleStyleOverride(primaryColor = foreground, backgroundColor = background) else null
    return SubtitleConfig(
        preferredLanguages = if (preferences.subtitles_enabled) listOfNotNull(preferences.preferred_subtitle_language) else emptyList(),
        autoSelect = preferences.subtitles_enabled,
        autoSelectForced = preferences.subtitles_enabled,
        fontScale = preferences.subtitle_font_scale.takeIf { it.isFinite() }?.coerceIn(.5, 2.0)?.toFloat() ?: 1f,
        style = style,
        // Kite's libass tier ignores colour overrides. Its built-in text renderer
        // honours them; retain authored typesetting when no colour is overridden.
        typesetting = style == null,
    )
}

/** Preference wire format is #RRGGBB or Android #AARRGGBB, not CSS RGBA. */
private fun desktopCaptionColor(value: String): Int? {
    if (value.length !in setOf(7, 9) || value.firstOrNull() != '#' ||
        value.drop(1).any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    val hex = value.drop(1).toLongOrNull(16) ?: return null
    return (if (value.length == 7) hex or 0xFF000000L else hex).toInt()
}
