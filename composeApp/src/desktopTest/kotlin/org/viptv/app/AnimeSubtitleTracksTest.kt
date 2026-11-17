package org.viptv.app

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.features.player.groupSubtitleTracks
import org.viptv.app.features.settings.subtitleModeChoices
import org.viptv.app.features.settings.subtitleModeNote
import org.viptv.app.features.settings.subtitleModeValue
import org.viptv.app.player.*
import org.viptv.app.player.mpv.MpvSession
import java.io.File
import kotlin.test.*

class AnimeSubtitleTracksTest {
    private fun track(id: Int, name: String?, forced: Boolean = false, default: Boolean = false, language: String? = "eng") =
        embeddedSubtitleTrack(id, id, name, language, false, forced, default)

    @Test fun signsTracksAreRecognisedByFlagOrName() {
        listOf("Signs & Songs", "Signs/Songs", "signs", "Songs", "Forced", "English [Forced]", "S&S", "Signs (Commie)")
            .forEach { assertTrue(signsOnlySubtitle(it, false), it) }
        listOf(null, "Full Subtitles", "Dialogue", "Full Subtitles (Signs & Songs)", "English", "SDH", "Designs", "Songbird")
            .forEach { assertFalse(signsOnlySubtitle(it, false), "$it") }
        assertTrue(signsOnlySubtitle("Full Subtitles", true), "The forced flag wins over the name")
    }

    @Test fun theFullTrackWinsOverAFlaggedOrNamedSignsTrack() {
        val flagged = listOf(track(1, "Signs & Songs", forced = true, default = true), track(2, "Full Subtitles"))
        assertEquals(2, fullDialogueSubtitle(flagged, "en"))
        val unflagged = listOf(track(1, "Signs & Songs"), track(2, "Full Subtitles"))
        assertEquals(2, fullDialogueSubtitle(unflagged, "en"))
        assertEquals(2, fullDialogueSubtitle(unflagged, null))
        val twoFull = listOf(track(1, "Signs"), track(2, "Dialogue"), track(3, "Honorifics", default = true))
        assertEquals(3, fullDialogueSubtitle(twoFull, "English"), "A default full track beats an earlier full track")
        assertNull(fullDialogueSubtitle(listOf(track(1, "Signs & Songs")), "en"), "Signs alone are not dialogue")
        assertNull(fullDialogueSubtitle(flagged, "es"))
    }

    @Test fun foreignAudioTurnsOnTheFullTrackForJapaneseAudio() {
        val tracks = listOf(SubtitleModeTrack(1, "eng", forced = true, name = "Signs & Songs", default = true),
            SubtitleModeTrack(2, "eng", name = "Full Subtitles"))
        assertEquals(2, subtitleModeChoice("foreign", "en", "en", "jpn", tracks)?.key)
        assertEquals(1, subtitleModeChoice("foreign", "en", "en", "eng", tracks)?.key, "A dub starts Signs & Songs")
    }

    @Test fun aDubStartsTheSignsTrackAndForeignAudioTheFullOne() {
        val tracks = listOf(track(1, "Signs & Songs", forced = true, default = true), track(2, "Full Subtitles"))
        assertEquals(1, dubSignsSubtitle(tracks, "foreign", "en", "en", "eng"))
        assertEquals(1, dubSignsSubtitle(tracks, "foreign", null, null, "en-US"), "No preferences means English audio and subtitles")
        assertNull(dubSignsSubtitle(tracks, "foreign", "en", "en", "jpn"), "Foreign audio is not a dub")
        assertEquals(2, fullDialogueSubtitle(tracks, "en"))
        assertNull(dubSignsSubtitle(tracks, "always", "en", "en", "eng"), "Always keeps full dialogue")
        assertNull(dubSignsSubtitle(tracks, "never", "en", "en", "eng"))
        assertNull(dubSignsSubtitle(tracks, "foreign", "en", "en", "und"), "Untagged audio is not a dub")
        assertNull(dubSignsSubtitle(listOf(track(2, "Full Subtitles")), "foreign", "en", "en", "eng"), "No signs track stays off")
        assertNull(dubSignsSubtitle(tracks, "foreign", "en", "es", "eng"), "Signs only in the subtitle language")
        val two = listOf(track(1, "Signs"), track(2, "Songs", default = true), track(3, "Full Subtitles"))
        assertEquals(2, dubSignsSubtitle(two, "foreign", "en", "en", "eng"))
    }

    @Test fun onlyTheForeignOptionCarriesTheDubNote() {
        assertEquals("Signs & songs on dubs", subtitleModeNote("foreign"))
        assertNull(subtitleModeNote("always"))
        assertNull(subtitleModeNote("never"))
        assertEquals("foreign", subtitleModeValue(subtitleModeChoices("en")[1].second))
    }

    @Test fun eachEmbeddedTrackShowsItsNameWithTheLanguageUnderIt() {
        val tracks = listOf(track(1, "Signs & Songs", forced = true), track(2, "Full Subtitles"), track(3, null),
            track(4, "English"), track(5, null, language = "und"))
        assertEquals(listOf("Signs & Songs · English", "Full Subtitles · English", "English", "English · 2", "Subtitle 5"),
            PlaybackTracks(subtitles = tracks).subtitles.map { it.label })
        val rows = groupSubtitleTracks(PlaybackTracks(subtitles = tracks).subtitles)
        assertEquals(listOf("Signs & Songs", "Full Subtitles", "English", "English · 2", "Subtitle 5"), rows.map { it.primary })
        assertEquals(listOf("English", "English", null, null, null), rows.map { it.source })
    }

    @Test fun mpvReportsBothAnimeTracksByNameAndPicksTheFullOne() = runBlocking {
        val file = File.createTempFile("viptv-anime-", ".mkv").apply {
            deleteOnExit()
            writeBytes(requireNotNull(AnimeSubtitleTracksTest::class.java.getResourceAsStream("/subtitles/anime-signs-full.mkv")).readBytes())
        }
        val session = MpvSession(PlaybackInput("anime", file.path, preferences = ProfilePreferences(preferred_subtitle_language = "en")))
        try {
            session.property("vo", "null")
            session.command("loadfile", file.path, "replace")
            val state = withTimeout(20_000) { session.state.first { it.loaded && it.subtitles.size == 2 } }
            assertEquals(listOf("Signs & Songs · English", "Full Subtitles · English"), state.subtitles.map { it.label })
            assertEquals(listOf(true, false), state.subtitles.map { it.forced })
            assertEquals(listOf("Japanese" to "Mono", "English" to "Mono"), state.audio.map { it.name to it.detail })
            assertEquals(listOf(true, false), state.subtitles.map { it.default })
            assertEquals(state.subtitles.single { it.name == "Full Subtitles" }.id, fullDialogueSubtitle(state.subtitles, "en"))
            assertEquals(state.subtitles.single { it.name == "Signs & Songs" }.id,
                dubSignsSubtitle(state.subtitles, "foreign", "en", "en", state.audio.single { it.name == "English" }.language))
            assertNull(dubSignsSubtitle(state.subtitles, "foreign", "en", "en", state.audio.single { it.name == "Japanese" }.language))
        } finally { session.closeAfterRenderer(); withTimeout(5_000) { session.closed.await() }; file.delete() }
    }
}
