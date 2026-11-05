package org.viptv.app

import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.features.settings.profilePreferences
import org.viptv.app.features.settings.tvSettings
import kotlin.test.*

class SubtitleStylePreferencesTest {
    @Test fun serializedPreferencesPreserveEverySupportedRustField() {
        val json = Json { encodeDefaults = true }
        val original = ProfilePreferences("pt-BR", "ja", false, "best", 1080, 1.3, "#FFE16B", "#CC102030")
        val encoded = json.encodeToJsonElement(original).jsonObject
        assertEquals(setOf("preferred_audio_language", "preferred_subtitle_language", "subtitles_enabled", "source_selection", "max_quality_height",
            "subtitle_font_scale", "subtitle_text_color", "subtitle_background_color", "hdr_preference", "subtitle_mode",
            "next_episode_button", "skip_intros", "remembered_source_reuse", "match_frame_rate"), encoded.keys)
        assertEquals(original, json.decodeFromJsonElement<ProfilePreferences>(encoded))
        assertEquals(original, original.tvSettings().profilePreferences(original))
    }
    @Test fun savingSubtitleSizeLeavesMatchFrameRateAsStored() {
        val json = Json { ignoreUnknownKeys = true }
        val snapshot = json.decodeFromString<PreferencesSnapshot>(
            """{"revision":4,"preferences":{"match_frame_rate":false,"subtitle_font_scale":1.0}}""")
        assertFalse(snapshot.preferences.match_frame_rate)
        val larger = snapshot.preferences.copy(subtitle_font_scale = 1.25)
        val wire = profilePreferencesWire(larger)
        assertFalse(wire.getValue("match_frame_rate").jsonPrimitive.boolean)
        assertEquals(1.25, wire.getValue("subtitle_font_scale").jsonPrimitive.double)
        val stored = json.decodeFromString<ProfilePreferences>(wire.toString())
        assertFalse(stored.match_frame_rate)
        assertEquals(1.25, stored.subtitle_font_scale)
    }
    @Test fun changingTvLanguageRetainsCustomStylesAndChangingBackgroundWritesSupportedColor() {
        val original = ProfilePreferences(subtitle_font_scale = .5, subtitle_text_color = "#123456", subtitle_background_color = "#60102030")
        val changed = original.tvSettings().copy(audioLanguage = "French").profilePreferences(original)
        assertEquals(original.copy(preferred_audio_language = "fr"), changed)
        assertEquals("#00000000", original.tvSettings().copy(subtitleBackground = "None").profilePreferences(original).subtitle_background_color)
        assertEquals("#B3000000", ProfilePreferences().tvSettings().copy(subtitleBackground = "Box").profilePreferences().subtitle_background_color)
    }
}
