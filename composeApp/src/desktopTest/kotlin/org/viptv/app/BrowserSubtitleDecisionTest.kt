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
            audioTrack(0, 1, "English", "eng", 2, false),
            audioTrack(1, 2, "Japanese description", "jpn", 6, true),
        ))
        assertEquals(listOf("English · Stereo", "Japanese description · Japanese · 5.1"), tracks.audio.map { it.label })
        assertEquals(listOf("English", "Japanese description"), tracks.audio.map { it.name })
        assertEquals(listOf("Stereo", "Japanese · 5.1"), tracks.audio.map { it.detail })
        assertEquals(listOf("eng", "jpn"), tracks.audio.map { it.language })
        assertEquals(listOf(false, true), tracks.audio.map { it.selected })
    }

    @Test fun unnamedRenditionUsesTheLanguageDisplayName() {
        val tracks = PlaybackTracks(audio = listOf(
            audioTrack(0, 1, null, "eng", null, false),
            audioTrack(1, 2, "", "jpn", null, true),
        ))
        assertEquals(listOf("English", "Japanese"), tracks.audio.map { it.label })
        assertEquals(listOf("eng", "jpn"), tracks.audio.map { it.language })
    }

    @Test fun unnamedRenditionFallsBackToOrdinalAndUnknownLanguageIsNull() {
        val tracks = PlaybackTracks(audio = listOf(audioTrack(3, 4, "", "und", null, false)))
        assertEquals("Audio track 4", tracks.audio.single().label)
        assertNull(tracks.audio.single().language)
    }
}
