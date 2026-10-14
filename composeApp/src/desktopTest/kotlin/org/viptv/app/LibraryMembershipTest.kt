package org.viptv.app

import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class LibraryMembershipTest {
    @Test fun completeMembershipDoesNotRequireHydratingEverySavedCard() = runBlocking {
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val controller = ApplicationController(this, store)
        controller.mutable.value = ApplicationState(home = HomeSnapshot(Scope("server", "owner", "profile", 1), 1, rows = emptyList(), complete = true))
        val cards = (1..100).map { Card(MediaIdentity("title-$it", "movie", emptyList()), provider_id = "metadata", title = "Title $it", badges = emptyList()) }
        val older = cards.first().copy(identity = MediaIdentity("older-than-first-page", "movie", emptyList()))
        controller.updateLibrary(cards, (cards + older).map { it.identity })
        val state = controller.state.value
        assertTrue(state.isSaved(older))
        assertEquals(100, state.library.size)
        assertEquals(12, state.home?.rows?.single { it.id == "favorites" }?.cards?.size)
        assertEquals(101, state.favoriteKeys?.size)
    }
}
