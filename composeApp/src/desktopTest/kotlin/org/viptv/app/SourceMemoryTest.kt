package org.viptv.app

import org.viptv.app.connection.RankedSources
import org.viptv.app.connection.interimSourceGroups
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class SourceMemoryTest {
    private class Store(var raw: String? = null) : SourceMemoryStore {
        override fun read(): String? = raw
        override fun write(value: String) { raw = value }
    }

    private val series = Card(MediaIdentity("show", "series", emptyList()), provider_id = "addon:1", title = "Show", badges = emptyList())
    private val other = Card(MediaIdentity("other", "series", emptyList()), provider_id = "addon:1", title = "Other", badges = emptyList())
    private fun episode(season: Long, number: Long) = series.copy(
        identity = MediaIdentity("show:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = "Episode $number")
    private fun row(id: String, provider: String = "addon:$id", languages: List<String> = emptyList(), releaseType: String? = null) =
        SourceCandidate(id, provider, "Source $id", audio_languages = languages, release_type = releaseType)
    private fun ranked(vararg rows: SourceCandidate) = RankedSources(rows.toList(), interimSourceGroups(rows.toList()))

    @Test fun aRememberedChoiceSurvivesAProcessRestart() {
        val store = Store()
        SourceMemoryBook(store).remember("default", episode(1, 1), row("a", languages = listOf("en")))
        assertEquals(RememberedSource("addon:a", listOf("en")), SourceMemoryBook(store).entry("default", episode(1, 1)))
    }

    @Test fun anEpisodeIsRememberedAsItsSeries() {
        val store = Store()
        SourceMemoryBook(store).remember("default", episode(1, 1), row("a", languages = listOf("ja")))
        val restarted = SourceMemoryBook(store)
        assertEquals(RememberedSource("addon:a", listOf("ja")), restarted.entry("default", episode(1, 2)))
        assertEquals(RememberedSource("addon:a", listOf("ja")), restarted.entry("default", series))
    }

    @Test fun anotherProfileOrTitleHasNoEntry() {
        val store = Store()
        SourceMemoryBook(store).remember("default", episode(1, 1), row("a", languages = listOf("en")))
        val book = SourceMemoryBook(store)
        assertNull(book.entry("guest", episode(1, 1)))
        assertNull(book.entry("default", other))
        assertNull(book.entry("default", Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "addon:1", title = "Movie", badges = emptyList())))
    }

    @Test fun theRememberedAddonWinsThenAnyRowInTheRememberedAudio() {
        val memory = RememberedSource("addon:a", listOf("en"))
        assertEquals("a2", ranked(row("other", "addon:b", listOf("en")), row("a2", "addon:a", listOf("en"))).matchingSource(memory)?.id)
        assertEquals("other", ranked(row("other", "addon:b", listOf("en")), row("a", "addon:a", listOf("ja"))).matchingSource(memory)?.id)
    }

    @Test fun aRowWithoutTheRememberedAudioIsNeverASubstitute() {
        val memory = RememberedSource("addon:a", listOf("en"))
        assertNull(ranked(row("a", "addon:a", listOf("ja")), row("b", "addon:b", listOf("ja"))).matchingSource(memory))
        assertNull(ranked(row("a", "addon:a")).matchingSource(memory))
    }

    @Test fun aChoiceWithNoAudioEvidenceReusesItsAddon() {
        val memory = RememberedSource("addon:a", emptyList())
        assertEquals("a", ranked(row("b", "addon:b", listOf("en")), row("a", "addon:a", listOf("ja"))).matchingSource(memory)?.id)
        assertNull(ranked(row("b", "addon:b", listOf("en"))).matchingSource(memory))
    }

    @Test fun hardFilteredRowsAreNeverAMatch() {
        val memory = RememberedSource("addon:a", listOf("en"))
        assertNull(ranked(row("a", "addon:a", listOf("en"), releaseType = "cam")).matchingSource(memory))
        assertEquals("b", ranked(row("a", "addon:a", listOf("en"), releaseType = "cam"), row("b", "addon:b", listOf("en")))
            .matchingSource(memory)?.id)
    }

    @Test fun audioTagsMatchAcrossRegionAndCase() {
        val memory = RememberedSource("addon:a", listOf("en-US"))
        assertEquals("b", ranked(row("b", "addon:b", listOf("EN"))).matchingSource(memory)?.id)
    }

    @Test fun entriesStayBounded() {
        val store = Store()
        val book = SourceMemoryBook(store)
        (1..SourceMemoryBook.MAX_ENTRIES + 20).forEach { index ->
            book.remember("default", Card(MediaIdentity("show$index", "series", emptyList()), provider_id = "addon:1", title = "Show $index", badges = emptyList()),
                row("source$index"))
        }
        val restarted = SourceMemoryBook(store)
        assertNull(restarted.entry("default", Card(MediaIdentity("show1", "series", emptyList()), provider_id = "addon:1", title = "Show 1", badges = emptyList())))
        assertNotNull(restarted.entry("default", Card(MediaIdentity("show${SourceMemoryBook.MAX_ENTRIES + 20}", "series", emptyList()),
            provider_id = "addon:1", title = "Newest", badges = emptyList())))
    }

    @Test fun theProviderAffinitySurvivesARestartAndStaysPerProfile() {
        val store = Store()
        SourceMemoryBook(store).rememberAffinity("default", "addon:7")
        val restarted = SourceMemoryBook(store)
        assertEquals("addon:7", restarted.affinity("default"))
        assertNull(restarted.affinity("guest"))
        restarted.rememberAffinity("default", "debrid:tb")
        assertEquals("debrid:tb", SourceMemoryBook(store).affinity("default"), "The latest presented start wins")
        assertEquals("debrid:tb", restarted.affinity("default"))
    }

    @Test fun providerAffinityStaysBoundedAndIgnoresInvalidProviders() {
        val store = Store()
        val book = SourceMemoryBook(store)
        for (invalid in listOf("", "  ", "addon\n7", "a".repeat(SourceMemoryBook.MAX_AFFINITY_LENGTH + 1))) book.rememberAffinity("default", invalid)
        assertNull(book.affinity("default"))
        (1..SourceMemoryBook.MAX_PROFILES + 4).forEach { index -> book.rememberAffinity("profile$index", "addon:$index") }
        val restarted = SourceMemoryBook(store)
        assertNull(restarted.affinity("profile1"), "The oldest profile entry is dropped")
        assertEquals("addon:${SourceMemoryBook.MAX_PROFILES + 4}", restarted.affinity("profile${SourceMemoryBook.MAX_PROFILES + 4}"))
    }

    @Test fun anUnreadableRecordStartsEmpty() {
        assertEquals(SourceMemoryRecord(), decodeSourceMemory("{not json"))
        assertEquals(SourceMemoryRecord(), decodeSourceMemory(null))
    }
}
