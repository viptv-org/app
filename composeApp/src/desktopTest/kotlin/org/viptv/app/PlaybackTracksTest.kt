package org.viptv.app

import org.viptv.app.player.PlaybackTrack
import org.viptv.app.player.PlaybackTracks
import kotlin.test.*

class PlaybackTracksTest {
    private fun subtitle(id: Int, label: String = "eng · OpenSubtitles v3", selected: Boolean = false, language: String? = "eng") =
        PlaybackTrack(id, label, selected, language)

    @Test fun identicalSubtitleTracksCollapseToOne() {
        val tracks = PlaybackTracks(subtitles = listOf(subtitle(1), subtitle(2), subtitle(3), subtitle(4)))
        assertEquals(1, tracks.subtitles.size)
        assertEquals("eng · OpenSubtitles v3", tracks.subtitles.single().label)
    }

    @Test fun tracksThatDifferInLabelOrLanguageStay() {
        val tracks = PlaybackTracks(subtitles = listOf(
            subtitle(1, label = "English"), subtitle(2, label = "English (SDH)"),
            subtitle(3, label = "English", language = "en-US"), subtitle(4, label = "Spanish", language = "spa")))
        assertEquals(listOf("English", "English (SDH)", "English", "Spanish"), tracks.subtitles.map { it.label })
    }

    @Test fun theSelectedDuplicateIsTheOneKept() {
        val tracks = PlaybackTracks(subtitles = listOf(subtitle(1), subtitle(2, selected = true), subtitle(3)))
        assertEquals(2, tracks.subtitles.single().id)
        assertTrue(tracks.subtitles.single().selected)
    }

    @Test fun audioTracksDedupeToo() {
        val tracks = PlaybackTracks(audio = listOf(
            PlaybackTrack(1, "en · 2 channels", false, "en"), PlaybackTrack(2, "en · 2 channels", true, "en"),
            PlaybackTrack(3, "ru · 2 channels", false, "ru")))
        assertEquals(listOf("en · 2 channels", "ru · 2 channels"), tracks.audio.map { it.label })
        assertTrue(tracks.audio.first().selected)
    }

    @Test fun emptyAndSingleTrackListsSurvive() {
        assertEquals(PlaybackTracks(), PlaybackTracks())
        assertEquals(1, PlaybackTracks(audio = listOf(PlaybackTrack(1, "English", false, "en"))).audio.size)
        assertTrue(PlaybackTracks().subtitles.isEmpty())
    }
}