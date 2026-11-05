package org.viptv.app

import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.player.PlaybackTrack
import org.viptv.app.player.mpv.mpvSubtitlesOn
import kotlin.test.*

class MpvSubtitleModeTest {
    private fun audio(id: Int, language: String?, selected: Boolean) = PlaybackTrack(id, language ?: "Audio $id", selected, language)

    @Test fun foreignModeStartsOffForEnglishAudioAndReDecidesToOnForJapanese() {
        val preferences = ProfilePreferences(preferred_audio_language = "en", subtitle_mode = "foreign")
        val englishSelected = listOf(audio(1, "eng", selected = true), audio(2, "jpn", selected = false))
        val japaneseSelected = listOf(audio(1, "eng", selected = false), audio(2, "jpn", selected = true))
        assertFalse(mpvSubtitlesOn(englishSelected, preferences))
        assertTrue(mpvSubtitlesOn(japaneseSelected, preferences))
    }

    @Test fun foreignModeTreatsUntaggedAndUnknownAudioAsNotForeign() {
        val preferences = ProfilePreferences(preferred_audio_language = "en", subtitle_mode = "foreign")
        listOf(null, "", "und", "mul", "zxx").forEach { language ->
            assertFalse(mpvSubtitlesOn(listOf(audio(1, language, selected = true)), preferences), "audio $language")
        }
    }

    @Test fun alwaysAndNeverDoNotLookAtTheAudio() {
        val selectedEnglish = listOf(audio(1, "eng", selected = true), audio(2, "jpn", selected = false))
        val selectedJapanese = listOf(audio(1, "eng", selected = false), audio(2, "jpn", selected = true))
        for (tracks in listOf(selectedEnglish, selectedJapanese)) {
            assertTrue(mpvSubtitlesOn(tracks, ProfilePreferences(preferred_audio_language = "en", subtitle_mode = "always")))
            assertFalse(mpvSubtitlesOn(tracks, ProfilePreferences(preferred_audio_language = "en", subtitle_mode = "never")))
        }
    }
}