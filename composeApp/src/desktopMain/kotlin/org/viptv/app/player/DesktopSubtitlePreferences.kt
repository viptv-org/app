package org.viptv.app.player

import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.connection.subtitleMode

internal data class SubtitleStyleOverride(val primaryColor: Int?, val backgroundColor: Int?)
internal data class SubtitleConfig(val preferredLanguages: List<String>, val autoSelect: Boolean, val autoSelectForced: Boolean, val fontScale: Float, val style: SubtitleStyleOverride?, val typesetting: Boolean)

internal fun desktopSubtitleConfig(preferences: ProfilePreferences): SubtitleConfig {
    val foreground = captionTextArgb(preferences.subtitle_text_color).takeUnless { it == CAPTION_WHITE }
    val background = captionBoxArgb(preferences.subtitle_background_color)
    val style = if (foreground != null || background != null)
        SubtitleStyleOverride(primaryColor = foreground, backgroundColor = background) else null
    val enabled = preferences.subtitleMode != "never"
    return SubtitleConfig(
        preferredLanguages = if (enabled) listOfNotNull(preferences.preferred_subtitle_language) else emptyList(),
        autoSelect = enabled,
        autoSelectForced = enabled,
        fontScale = preferences.subtitle_font_scale.takeIf { it.isFinite() }?.coerceIn(.5, 2.0)?.toFloat() ?: 1f,
        style = style,
        // Explicit overrides use libass forced styles; defaults preserve authored ASS.
        typesetting = style == null,
    )
}
