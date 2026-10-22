package org.viptv.app

import kotlinx.serialization.json.*
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.features.settings.profilePreferences
import org.viptv.app.features.settings.tvSettings
import kotlin.test.*

class SubtitleStylePreferencesTest {
    @Test fun serializedPreferencesPreserveEverySupportedRustField() {
        val json = Json { encodeDefaults = true }
        val original = ProfilePreferences("pt-BR", "ja", false, "best", 1080, 1.3, "#FFE16B", "#CC102030")
        val encoded = json.encodeToJsonElement(original).jsonObject
        assertEquals(setOf("preferred_audio_language", "preferred_subtitle_language", "subtitles_enabled", "source_selection", "max_quality_height",
            "subtitle_font_scale", "subtitle_text_color", "subtitle_background_color", "hdr_preference", "subtitle_mode"), encoded.keys)
        assertEquals(original, json.decodeFromJsonElement<ProfilePreferences>(encoded))
        assertEquals(original, original.tvSettings().profilePreferences(original))
    }
    @Test fun changingTvLanguageRetainsCustomStylesAndChangingBackgroundWritesSupportedColor() {
        val original = ProfilePreferences(subtitle_font_scale = .5, subtitle_text_color = "#123456", subtitle_background_color = "#60102030")
        val changed = original.tvSettings().copy(audioLanguage = "French").profilePreferences(original)
        assertEquals(original.copy(preferred_audio_language = "fr"), changed)
        assertEquals("#00000000", original.tvSettings().copy(subtitleBackground = "None").profilePreferences(original).subtitle_background_color)
        assertEquals("#B3000000", ProfilePreferences().tvSettings().copy(subtitleBackground = "Box").profilePreferences().subtitle_background_color)
    }
}
