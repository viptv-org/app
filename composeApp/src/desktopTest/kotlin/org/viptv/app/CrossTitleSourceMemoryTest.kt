package org.viptv.app

import kotlin.test.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*

class CrossTitleSourceMemoryTest {
    private class Store(var raw: String? = null) : SourceMemoryStore {
        override fun read() = raw
        override fun write(value: String) { raw = value }
    }
    private fun card(id: String) = Card(MediaIdentity(id, "movie", emptyList()), provider_id = "catalog", title = id, badges = emptyList())
    private fun source(id: String) = SourceCandidate("expiring-$id", "connection", id, audio_languages = listOf("en"),
        presentation = SourcePresentation(provider_name = id, source_id = "sdk-$id"))

    @Test fun enablingAdoptsLastManualChoiceAndTitleChoiceKeepsPrecedence() {
        val store = Store()
        val book = SourceMemoryBook(store)
        book.remember("profile", card("one"), source("A"))
        assertNull(book.choice("profile", card("two")))
        book.setAcross("profile", true)
        assertEquals("sdk-A", book.choice("profile", card("two"))?.source_id)
        book.remember("profile", card("two"), source("B"))
        assertEquals("sdk-A", book.choice("profile", card("one"))?.source_id)
        assertEquals("sdk-B", book.choice("profile", card("three"))?.source_id)
        assertNull(book.choice("guest", card("three")))
        val restarted = SourceMemoryBook(store)
        assertTrue(restarted.across("profile"))
        assertEquals("sdk-B", restarted.context("profile", card("three")).across?.sourceId)
        assertFalse(store.raw!!.contains("expiring-"))
    }

    @Test fun exceptionsWhileDisabledDoNotReplaceCrossTitleChoice() {
        val book = SourceMemoryBook(Store())
        book.setAcross("profile", true)
        book.remember("profile", card("one"), source("A"))
        book.setAcross("profile", false)
        book.remember("profile", card("two"), source("B"))
        assertNull(book.choice("profile", card("three")))
        book.setAcross("profile", true)
        assertEquals("sdk-A", book.choice("profile", card("three"))?.source_id)
        assertEquals("sdk-B", book.choice("profile", card("two"))?.source_id)
    }
}
