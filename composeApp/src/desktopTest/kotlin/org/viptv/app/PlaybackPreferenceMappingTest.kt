package org.viptv.app

import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.features.settings.*
import org.viptv.app.player.*
import kotlin.test.*

class PlaybackPreferenceMappingTest {
    private val codec = Json { ignoreUnknownKeys = true }

    @Test fun hdrPreferenceMapsBothWays() {
        mapOf("any" to "No preference", "prefer" to "Prefer", "avoid" to "Avoid").forEach { (value, label) ->
            assertEquals(label, ProfilePreferences(hdr_preference = value).tvSettings().hdr)
            assertEquals(value, ProfilePreferences().tvSettings().copy(hdr = label).profilePreferences().hdr_preference)
            assertEquals(label, hdrPreferenceLabel(value)); assertEquals(value, hdrPreferenceValue(label))
        }
        assertEquals(listOf("No preference", "Prefer", "Avoid"), hdrPreferenceChoices.map { it.second })
    }

    @Test fun originalLanguageIsNoAudioPreference() {
        assertEquals("Original language", ProfilePreferences().tvSettings().audioLanguage)
        assertNull(ProfilePreferences(preferred_audio_language = "fr").tvSettings().copy(audioLanguage = "Original language")
            .profilePreferences(ProfilePreferences(preferred_audio_language = "fr")).preferred_audio_language)
        assertEquals("Original language", phonePreferenceLanguage(null, audio = true))
        assertEquals("Original language", phonePreferenceLanguage("", audio = true))
        assertEquals("Default", phonePreferenceLanguage(null, audio = false))
    }

    @Test fun autoQualityNamesThisDevice() {
        assertEquals("Auto", ProfilePreferences().tvSettings().quality)
        assertNull(ProfilePreferences(max_quality_height = 1080).tvSettings().copy(quality = "Auto").profilePreferences().max_quality_height)
        assertEquals("Auto (this device: 1080p)", autoQualityLabel(1080))
        assertEquals("Auto (this device: 4K)", autoQualityLabel(2160))
        assertEquals("Auto", autoQualityLabel(null))
        assertEquals("Auto (this device: 720p)", tvQualityLabel("Auto", 720))
        assertEquals("1080p", tvQualityLabel("1080p", 2160))
        assertEquals("Auto (this device: 1440p)", qualityLabel(null, 1440))
        assertEquals("4K", qualityLabel(2160, 1080))
    }

    @Test fun olderServersNeitherSendNorRequireTheNewFields() {
        val old = codec.decodeFromString<PreferencesSnapshot>("""{"revision":3,"preferences":{"preferred_audio_language":null,
            "preferred_subtitle_language":"en","subtitles_enabled":false,"source_selection":"best","max_quality_height":null}}""")
        assertEquals("any", old.preferences.hdr_preference)
        assertEquals("never", old.preferences.subtitleMode)
        val wire = profilePreferencesWire(old.preferences)
        assertFalse("hdr_preference" in wire); assertFalse("subtitle_mode" in wire)
        assertFalse(wire.getValue("subtitles_enabled").jsonPrimitive.boolean)
        val chosen = profilePreferencesWire(old.preferences.copy(hdr_preference = "avoid").withSubtitleMode("foreign"))
        assertEquals("avoid", chosen.getValue("hdr_preference").jsonPrimitive.content)
        assertEquals("foreign", chosen.getValue("subtitle_mode").jsonPrimitive.content)
        assertTrue(chosen.getValue("subtitles_enabled").jsonPrimitive.boolean)
        val newer = codec.decodeFromString<ProfilePreferences>("""{"subtitles_enabled":true,"subtitle_mode":"foreign","hdr_preference":"prefer"}""")
        assertEquals("foreign", newer.subtitleMode); assertEquals("prefer", newer.hdr_preference)
    }

    @Test fun subtitleModeDerivesFromLegacyOnOffAndKeepsItInSync() {
        assertEquals("always", ProfilePreferences(subtitles_enabled = true).subtitleMode)
        assertEquals("never", ProfilePreferences(subtitles_enabled = false).subtitleMode)
        assertEquals("never", ProfilePreferences(subtitles_enabled = true, subtitle_mode = "never").subtitleMode)
        assertEquals("always", ProfilePreferences(subtitles_enabled = true, subtitle_mode = "sometimes").subtitleMode)
        listOf("always" to true, "foreign" to true, "never" to false).forEach { (mode, enabled) ->
            val value = ProfilePreferences().withSubtitleMode(mode)
            assertEquals(mode, value.subtitle_mode); assertEquals(enabled, value.subtitles_enabled)
            assertEquals(enabled, profilePreferencesWire(value).getValue("subtitles_enabled").jsonPrimitive.boolean)
        }
    }

    @Test fun tvSubtitleModeRoundTripsAndLabelsNameThePreferredAudio() {
        val original = ProfilePreferences(preferred_audio_language = "fr", subtitles_enabled = false)
        assertEquals("never", original.tvSettings().subtitleMode)
        assertEquals(original, original.tvSettings().profilePreferences(original))
        val foreign = original.tvSettings().copy(subtitleMode = "foreign").profilePreferences(original)
        assertEquals("foreign", foreign.subtitle_mode); assertTrue(foreign.subtitles_enabled)
        assertEquals(listOf("Always", "When audio isn’t French", "Never"), subtitleModeChoices("fr").map { it.second })
        assertEquals("When audio isn’t English", subtitleModeLabel("foreign", null))
        assertEquals("When audio isn’t English", subtitleModeLabel("foreign", "en-US"))
        assertEquals("When audio isn’t ja", subtitleModeLabel("foreign", "ja"))
        listOf("always", "foreign", "never").forEach { assertEquals(it, subtitleModeValue(subtitleModeLabel(it, "es"))) }
        assertEquals("When audio isn’t English", subtitleModeLabel("foreign", ProfilePreferences().tvSettings().audioLanguageTag()))
    }

    @Test fun tvPlaybackHidesUnstoredRowsOnRealConnections() {
        assertEquals(listOf("Next episode button", "Skip intros and recaps", "Default source", "Preferred quality", "HDR and Dolby Vision"),
            tvPlaybackRows(actualPreferencesOnly = true))
        assertEquals(listOf("Next episode button", "Skip intros and recaps", "Default source", "Preferred quality", "HDR and Dolby Vision",
            "Match frame rate"), tvPlaybackRows(actualPreferencesOnly = false))
        assertFalse("Start with subtitles" in tvPlaybackRows(false))
        assertEquals(listOf("Next episode button", autoplayNextRow, "Skip intros and recaps", "Default source", "Preferred quality",
            "HDR and Dolby Vision"), tvPlaybackRows(actualPreferencesOnly = true, deviceRows = true))
        assertEquals(listOf("Audio language", "Subtitles", "Subtitle language", "Subtitle size", "Subtitle background", "Subtitle colour"), tvSubtitleRows)
    }

    @Test fun theNextEpisodeButtonAndSkipIntrosSwitchesRoundTrip() {
        val stored = ProfilePreferences(next_episode_button = false, skip_intros = true)
        assertEquals(stored, stored.tvSettings().profilePreferences(stored))
        assertFalse(stored.tvSettings().nextEpisode)
        assertTrue(stored.tvSettings().skipIntros)
        assertTrue(ProfilePreferences().tvSettings().nextEpisode)
        assertFalse(ProfilePreferences().tvSettings().skipIntros)
        assertEquals(ProfilePreferences(next_episode_button = false, skip_intros = true),
            TvSettingsPreferences(nextEpisode = false, skipIntros = true).profilePreferences())
    }

    @Test fun foreignAudioRuleFollowsTheSpecCases() {
        val tracks = listOf(SubtitleModeTrack("forced-en", "en", forced = true), SubtitleModeTrack("es", "spa"),
            SubtitleModeTrack("en", "eng"), SubtitleModeTrack("en2", "en-US"))
        assertNull(subtitleModeChoice("foreign", null, "en", "eng", tracks))
        assertNull(subtitleModeChoice("foreign", "English", "en", "en-GB", tracks))
        assertEquals("en", subtitleModeChoice("foreign", null, "en", "jpn", tracks)?.key)
        assertEquals("en", subtitleModeChoice("foreign", "en", "English", "ja", tracks)?.key)
        listOf(null, "", "und", "mul", "zxx").forEach { assertNull(subtitleModeChoice("foreign", null, "en", it, tracks), "audio $it") }
        assertNull(subtitleModeChoice("foreign", null, "de", "jpn", tracks))
        assertEquals("es", subtitleModeChoice("foreign", null, null, "jpn", tracks.take(2))?.key)
        assertNull(subtitleModeChoice("foreign", null, null, "jpn", tracks))
        assertEquals("en", subtitleModeChoice("always", null, "en", "eng", tracks)?.key)
        assertNull(subtitleModeChoice("never", null, "en", "jpn", tracks))
        assertEquals(listOf(false, true, false), listOf("en", "ja", "eng").map { audioIsForeign(it, "en") })
        assertTrue(audioIsForeign("eng", "fr"))
    }

    @Test fun languageMatchingAcceptsCodesAndNames() {
        listOf("en", "eng", "en-US", "en_GB", "English", "ENGLISH").forEach { assertEquals("en", subtitleModeLanguage(it), it) }
        assertEquals("ja", subtitleModeLanguage("jpn")); assertEquals("pt", subtitleModeLanguage("pt-BR"))
        assertEquals("tlh", subtitleModeLanguage("tlh"))
        listOf(null, "", " ", "und", "mul", "zxx").forEach { assertNull(subtitleModeLanguage(it), "$it") }
    }
}
