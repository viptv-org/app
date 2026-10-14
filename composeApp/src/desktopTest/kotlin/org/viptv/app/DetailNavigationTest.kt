package org.viptv.app

import kotlinx.coroutines.*
import kotlin.test.*
import org.viptv.app.state.*
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.engine.wire.*

class DetailNavigationTest {
    @Test fun sourceBackReturnsToLiveWithoutStartingAnotherCatalogRequest() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val controller = ApplicationController(scope, store)
        controller.mutable.value = ApplicationState(screen = Screen.Sources, sourceReturnScreen = Screen.Live,
            busy = true, findingSources = true)
        controller.navigateBack()
        assertEquals(Screen.Live, controller.state.value.screen)
        assertFalse(controller.state.value.busy)
        assertFalse(controller.state.value.findingSources)
        assertNull(controller.job)
        scope.cancel()
    }
    @Test fun relatedTitleBackRestoresPriorDetailThenOriginalSearch() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val controller = ApplicationController(scope, store)
        val card = Card(identity = MediaIdentity("title", "series", emptyList()), provider_id = "provider", title = "Title", badges = emptyList())
        val prior = DetailSnapshot(item = card, episodes = emptyList())
        controller.mutable.value = ApplicationState(screen = Screen.Details, searchTerm = "Naruto", results = listOf(card), detailReturnScreen = Screen.Search, detail = prior.copy(item = card.copy(title = "Related")), detailHistory = listOf(prior))
        controller.navigateBack()
        assertEquals(prior, controller.state.value.detail)
        assertEquals(Screen.Details, controller.state.value.screen)
        controller.navigateBack()
        assertEquals(Screen.Search, controller.state.value.screen)
        assertEquals("Naruto", controller.state.value.searchTerm)
        assertEquals(listOf(card), controller.state.value.results)
        scope.cancel()
    }
}
