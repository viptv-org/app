package org.viptv.app

import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.player.desktopSubtitleConfig
import kotlin.test.*

class DesktopSubtitlePreferencesTest {
    @Test fun defaultsPreserveAuthoredStylesAndExplicitOverridesCarryArgbAlpha() {
        val defaults = desktopSubtitleConfig(ProfilePreferences())
        assertNull(defaults.style); assertEquals(1f, defaults.fontScale); assertTrue(defaults.typesetting)
        val custom = desktopSubtitleConfig(ProfilePreferences(subtitle_font_scale = 1.5,
            subtitle_text_color = "#80FFC107", subtitle_background_color = "#CC0000FF"))
        assertEquals(1.5f, custom.fontScale)
        assertEquals(0x80FFC107.toInt(), custom.style?.primaryColor)
        assertEquals(0xCC0000FF.toInt(), custom.style?.backgroundColor)
        assertFalse(custom.typesetting, "Explicit colour choices use the renderer which honours them even for ASS")
    }

    @Test fun invalidValuesKeepAuthoredStylesAndScaleRemainsWithinSupportedRange() {
        val invalid = desktopSubtitleConfig(ProfilePreferences(subtitle_font_scale = Double.NaN,
            subtitle_text_color = "yellow", subtitle_background_color = "#GG000000"))
        assertNull(invalid.style); assertEquals(1f, invalid.fontScale)
        assertEquals(.5f, desktopSubtitleConfig(ProfilePreferences(subtitle_font_scale = -10.0)).fontScale)
        assertEquals(2f, desktopSubtitleConfig(ProfilePreferences(subtitle_font_scale = 100.0)).fontScale)
        assertEquals(0xFFFFC107.toInt(), desktopSubtitleConfig(ProfilePreferences(subtitle_text_color = "#FFC107")).style?.primaryColor)
    }

    @Test fun subtitlesOffRetainsStylingWithoutAutomaticSelection() {
        val config = desktopSubtitleConfig(ProfilePreferences(subtitles_enabled = false,
            preferred_subtitle_language = "fr", subtitle_font_scale = 1.25, subtitle_background_color = "#80000000"))
        assertFalse(config.autoSelect); assertFalse(config.autoSelectForced)
        assertTrue(config.preferredLanguages.isEmpty()); assertEquals(1.25f, config.fontScale)
        assertEquals(0x80000000.toInt(), config.style?.backgroundColor)
    }
}
