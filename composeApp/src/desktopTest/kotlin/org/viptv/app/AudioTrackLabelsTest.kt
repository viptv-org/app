package org.viptv.app

import org.viptv.app.player.*
import kotlin.test.*

class AudioTrackLabelsTest {
    @Test fun aTrackNamedAfterItsLanguageShowsTheLanguageOnceOverItsLayout() {
        val track = audioTrack(1, 1, "Japanese", "ja", 2, true)
        assertEquals("Japanese", track.name)
        assertEquals("Stereo", track.detail)
        assertEquals("Japanese · Stereo", track.label)
        assertEquals("ja", track.language)
    }

    @Test fun aNamedTrackKeepsItsLanguageInTheDetail() {
        val track = audioTrack(2, 2, "Commentary", "eng", 2, false)
        assertEquals("Commentary", track.name)
        assertEquals("English · Stereo", track.detail)
    }

    @Test fun anUnnamedTrackUsesItsLanguageAndAnUnknownOneItsOrdinal() {
        assertEquals("Japanese" to "5.1", audioTrack(1, 1, null, "jpn", 6, false).let { it.name to it.detail })
        assertEquals("English" to null, audioTrack(1, 1, " ", "eng", null, false).let { it.name to it.detail })
        val unknown = audioTrack(4, 3, null, "und", 1, false)
        assertEquals("Audio track 3" to "Mono", unknown.name to unknown.detail)
        assertNull(unknown.language)
    }

    @Test fun channelLayoutsReadAsViewersKnowThem() {
        assertEquals(listOf(null, null, "Mono", "Stereo", "3 channels", "5.1", "7.1"),
            listOf(null, 0, 1, 2, 3, 6, 8).map(::channelLayout))
    }

    @Test fun noLabelRepeatsTheLanguageOrShowsARawCode() {
        for ((name, language) in listOf("Japanese" to "ja", "jpn" to "jpn", "English" to "en-US", null to "eng", "Commentary" to "en")) {
            val track = audioTrack(1, 1, name, language, 2, false)
            val words = track.label.split(" · ")
            assertEquals(words.distinct(), words, track.label)
            assertTrue(words.none { it.lowercase() in setOf("ja", "jpn", "en", "eng", "en-us") }, track.label)
        }
    }
}
