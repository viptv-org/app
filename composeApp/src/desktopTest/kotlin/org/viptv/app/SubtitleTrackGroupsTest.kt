package org.viptv.app

import org.viptv.app.features.player.groupSubtitleTracks
import org.viptv.app.player.PlaybackTrack
import kotlin.test.*

class SubtitleTrackGroupsTest {
    private fun provider(id: Int, label: String, language: String = "eng", selected: Boolean = false) =
        PlaybackTrack(id, label, selected, language)
    private fun embedded(id: Int, label: String, language: String = "en", selected: Boolean = false) =
        PlaybackTrack(id, label, selected, language)

    @Test fun severalSourcesOfOneLanguageGroupIntoOneRow() {
        val groups = groupSubtitleTracks(listOf(
            provider(Int.MIN_VALUE, "eng · OpenSubtitles v3"), provider(Int.MIN_VALUE + 1, "eng · Subscene", selected = true)))
        assertEquals(1, groups.size)
        assertEquals("English", groups.first().primary)
        assertEquals("OpenSubtitles v3 · Subscene", groups.first().source)
        assertTrue(groups.first().selected, "A group is selected when any source is")
        assertEquals(listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1), groups.first().ids)
    }

    @Test fun anEmbeddedTrackIsItsOwnSingleLineRow() {
        val groups = groupSubtitleTracks(listOf(embedded(1, "English (SDH)"), embedded(2, "English")))
        assertEquals(listOf("English (SDH)", "English"), groups.map { it.primary })
        assertTrue(groups.all { it.source == null }, "Embedded tracks carry no source line")
    }

    @Test fun aProviderTrackAndAnEmbeddedTrackDoNotMerge() {
        val groups = groupSubtitleTracks(listOf(provider(Int.MIN_VALUE, "eng · OpenSubtitles v3"), embedded(3, "English (SDH)")))
        assertEquals(2, groups.size)
        assertEquals(listOf("English", "English (SDH)"), groups.map { it.primary })
    }

    @Test fun aLanguageCodeWithoutACommonNameStillShowsItsName() {
        val groups = groupSubtitleTracks(listOf(provider(Int.MIN_VALUE, "vie · OpenSubtitles v3", language = "vie")))
        assertEquals("Vietnamese", groups.first().primary)
    }
}