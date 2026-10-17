package org.viptv.app

import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class ScopedLibraryMembershipTest {
    private val first = MetadataOrigin("stremio", "addon:1", "a".repeat(32))
    private val second = MetadataOrigin("stremio", "addon:2", "b".repeat(32))
    private fun card(origin: MetadataOrigin? = first, imdb: String? = null) = Card(
        MediaIdentity("private:one", "movie", imdb?.let { listOf(ExternalId("imdb", it)) }.orEmpty()),
        provider_id = origin?.connection_id ?: "addon:1", title = "Title", badges = emptyList(), metadata_origin = origin)
    private fun membership(vararg cards: Card) = LibraryMembershipSnapshot(emptyList(), 1, true,
        scoped_identities = cards.map { ScopedMediaIdentity(it.identity, requireNotNull(it.metadata_origin)) })

    @Test fun privateMembershipSeparatesProvidersAndConnectorReinstallations() {
        val saved = card()
        val state = ApplicationState(libraryMembership = membership(saved))
        assertTrue(state.isSaved(saved))
        assertFalse(state.isSaved(card(second)))
        assertFalse(state.isSaved(card(first.copy(incarnation = "c".repeat(32)))))
        assertFalse(state.isSaved(card(null)))
    }

    @Test fun sameRawLookupRemappedToAnotherSharedIdentityDoesNotShareSavedState() {
        val saved = card(imdb = "tt1")
        val state = ApplicationState(libraryMembership = membership(saved))
        assertTrue(state.isSaved(saved))
        assertFalse(state.isSaved(card(imdb = "tt2")))
        assertFalse(state.isSaved(card()))
    }

    @Test fun publicMembershipDoesNotPromoteAnUnverifiedPrivatePreviewAlias() {
        val public = MediaIdentity("tt1", "movie", emptyList())
        val state = ApplicationState(libraryMembership = LibraryMembershipSnapshot(listOf(public), 1, true))
        assertTrue(state.isSaved(card(second).copy(identity = public)))
        assertFalse(state.isSaved(card(imdb = "tt1")))
    }

    @Test fun legacyRowsRemainSavedOnlyForOriginlessLegacyCards() {
        val legacy = card(null, "tt1")
        val state = ApplicationState(libraryMembership = LibraryMembershipSnapshot(emptyList(), 1, true,
            legacy_identities = listOf(legacy.identity.copy(ids = emptyList()))))
        assertTrue(state.isSaved(legacy))
        assertFalse(state.isSaved(card(imdb = "tt1")))
    }

    @Test fun cardKeysRetainLineageAndClaimsWhileIgnoringPresentationAndIdOrder() {
        val a = card(imdb = "tt1").let { it.copy(identity = it.identity.copy(ids = it.identity.ids + ExternalId("tmdb", "10"))) }
        val reordered = a.copy(identity = a.identity.copy(ids = a.identity.ids.reversed()), title = "New display text", progress = .5)
        assertEquals(cardLookupKey(a), cardLookupKey(reordered))
        assertNotEquals(cardLookupKey(a), cardLookupKey(card(second, "tt1")))
        assertNotEquals(cardLookupKey(a), cardLookupKey(card(imdb = "tt2")))
        assertNotEquals(cardLookupKey(a), cardLookupKey(a.copy(metadata_origin = first.copy(incarnation = "c".repeat(32)))))
    }

    @Test fun legacyMembershipWireKeepsNewListsOptionalAndSelectorsOmitArtwork() {
        val old = Json.decodeFromString<LibraryMembershipSnapshot>("""{"identities":[],"revision":1,"complete":true}""")
        assertTrue(old.scoped_identities.isEmpty()); assertTrue(old.legacy_identities.isEmpty())
        val selected = card(imdb = "tt1").copy(poster = "https://images.example/signed", description = "Long description").lookupSelector()
        assertNull(selected.poster); assertNull(selected.description)
        assertEquals(first, selected.metadata_origin); assertEquals("tt1", selected.identity.ids.single().value)
    }
}
