package org.viptv.app

import org.viptv.app.player.*
import kotlin.test.*

class BrowserSubtitleDecisionTest {
    @Test fun foreignOnlyOffForEnglishAndOnForJapaneseAudio() {
        assertFalse(subtitleModeEnabled("foreign", "en", "en"))
        assertFalse(subtitleModeEnabled("foreign", "en", "eng"))
        assertFalse(subtitleModeEnabled("foreign", null, "en-GB"))
        assertTrue(subtitleModeEnabled("foreign", "en", "ja"))
        assertTrue(subtitleModeEnabled("foreign", "en", "jpn"))
        assertTrue(subtitleModeEnabled("foreign", null, "ja"))
        listOf(null, "", "und", "mul", "zxx").forEach { assertFalse(subtitleModeEnabled("foreign", "en", it), "audio $it") }
    }

    @Test fun alwaysAndNeverDoNotLookAtTheAudio() {
        assertTrue(subtitleModeEnabled("always", null, null))
        assertTrue(subtitleModeEnabled("always", "en", "ja"))
        assertFalse(subtitleModeEnabled("never", null, null))
        assertFalse(subtitleModeEnabled("never", "en", "ja"))
    }

    @Test fun twoAudioRenditionsBothList() {
        val tracks = PlaybackTracks(audio = listOf(
            browserAudioTrack(0, "English", "eng", false),
            browserAudioTrack(1, "Japanese description", "jpn", true),
        ))
        assertEquals(listOf("English", "Japanese description"), tracks.audio.map { it.label })
        assertEquals(listOf("eng", "jpn"), tracks.audio.map { it.language })
        assertEquals(listOf(false, true), tracks.audio.map { it.selected })
    }

    @Test fun unnamedRenditionUsesTheLanguageDisplayName() {
        val tracks = PlaybackTracks(audio = listOf(
            browserAudioTrack(0, null, "eng", false),
            browserAudioTrack(1, "", "jpn", true),
        ))
        assertEquals(listOf("English", "Japanese"), tracks.audio.map { it.label })
        assertEquals(listOf("eng", "jpn"), tracks.audio.map { it.language })
    }

    @Test fun unnamedRenditionFallsBackToOrdinalAndUnknownLanguageIsNull() {
        val tracks = PlaybackTracks(audio = listOf(browserAudioTrack(3, "", "und", false)))
        assertEquals("Audio track 4", tracks.audio.single().label)
        assertNull(tracks.audio.single().language)
    }
}
