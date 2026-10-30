package org.viptv.app

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.ListRemovalHost.Companion.continueCard
import org.viptv.app.ListRemovalHost.Companion.libraryCard
import org.viptv.app.ListRemovalHost.Companion.series
import org.viptv.app.connection.PreferencesSnapshot
import org.viptv.app.connection.defaultLibraryQuery
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class ListRemovalTest {
    private fun CoroutineScope.controller(host: ListRemovalHost, screen: Screen) =
        ApplicationController(this, host.sessionStore).also {
            it.connection = host.remote
            it.mutable.value = ApplicationState(screen = screen, selectedProfile = "1", preferences = PreferencesSnapshot())
        }

    private suspend fun until(what: String, condition: () -> Boolean) =
        withTimeout(5_000) { while (!condition()) delay(10) }.also { assertTrue(condition(), what) }

    private fun libraryPage(host: ListRemovalHost) = LibrarySnapshot(ListRemovalHost.scope, 1,
        defaultLibraryQuery(), host.libraryCards, complete = true, availability = Availability.AVAILABLE)

    @Test fun removingATitleFromMyListSendsOneDeleteAndDropsTheCard() = runBlocking<Unit> {
        ListRemovalHost().use { host ->
            val controller = controller(host, Screen.Library)
            controller.mutable.value = controller.state.value.copy(library = host.libraryCards, libraryPage = libraryPage(host))
            controller.removeFromMyList(libraryCard(2))
            controller.job?.join()
            assertEquals(listOf("series/tt0000002"), host.deletes.toList(), "exactly one DELETE, for that title")
            val state = controller.state.value
            assertEquals(listOf("Show 01", "Show 03"), state.libraryPage?.cards?.map { it.title })
            assertEquals(listOf("Show 01", "Show 03"), state.library.map { it.title })
            assertFalse(state.isSaved(libraryCard(2)), "the removed title is no longer saved")
            assertNull(state.error)
        }
    }

    @Test fun removingATitleFromContinueWatchingHidesItAndUndoRestoresItsPlace() = runBlocking<Unit> {
        ListRemovalHost().use { host ->
            val controller = controller(host, Screen.ContinueWatching)
            controller.mutable.value = controller.state.value.copy(
                continueWatching = ContinueWatchingList("1", host.continueCards, next = null))
            controller.removeFromContinueWatching(continueCard(2))
            until("the hide request") { host.visibility.size == 1 }
            val removed = controller.state.value
            assertEquals(listOf("Show 01", "Show 03", "Show 04"), removed.continueWatching?.cards?.map { it.title },
                "the card leaves the list without a reload")
            assertEquals(continueCard(2), removed.continueUndo?.card)
            assertEquals(1, removed.continueUndo?.index, "Undo needs the position the card had")
            val hide = host.visibility.single()
            assertEquals(continueCard(2).identity, Json.decodeFromJsonElement<MediaIdentity>(hide["identity"]!!))
            assertEquals(series(2), Json.decodeFromJsonElement<MediaIdentity>(hide["parent_identity"]!!))
            assertTrue(hide["hidden"]!!.jsonPrimitive.boolean)
            assertEquals(continueCard(2).metadata_origin, Json.decodeFromJsonElement<MetadataOrigin>(hide["metadata_origin"]!!),
                "the host needs the scoped selector for an origin-bearing entry")

            controller.undoRemoveFromContinueWatching()
            until("the show request") { host.visibility.size == 2 }
            val restored = controller.state.value
            assertEquals(host.continueCards, restored.continueWatching?.cards, "Undo puts the card back at its position")
            assertNull(restored.continueUndo)
            val show = host.visibility[1]
            assertEquals(series(2), Json.decodeFromJsonElement<MediaIdentity>(show["identity"]!!),
                "Undo shows the title the host resolved")
            assertFalse(show["hidden"]!!.jsonPrimitive.boolean)
        }
    }

    @Test fun aRefusedHidePutsTheCardBackAndReportsIt() = runBlocking<Unit> {
        ListRemovalHost().use { host ->
            val controller = controller(host, Screen.ContinueWatching)
            controller.mutable.value = controller.state.value.copy(
                continueWatching = ContinueWatchingList("1", host.continueCards, next = null))
            host.refuseVisibility = true
            controller.removeFromContinueWatching(continueCard(2))
            until("the failure") { controller.state.value.error != null }
            assertEquals(host.continueCards, controller.state.value.continueWatching?.cards,
                "a refused hide restores the card")
            assertNull(controller.state.value.continueUndo)
        }
    }

    @Test fun theHomeContinueRowFollowsTheRemovalAndTheUndo() = runBlocking<Unit> {
        ListRemovalHost().use { host ->
            val controller = controller(host, Screen.ContinueWatching)
            val row = HomeRow(browse = HomeBrowseAction(HomeBrowseTarget.CONTINUE), id = "continue",
                title = "Continue Watching", provider_id = "library", state = RowState.READY, cards = host.continueCards)
            controller.mutable.value = controller.state.value.copy(
                continueWatching = ContinueWatchingList("1", host.continueCards, next = null),
                home = HomeSnapshot(ListRemovalHost.scope, 1, rows = listOf(row), complete = true))
            controller.removeFromContinueWatching(continueCard(2))
            until("the hide") { host.visibility.size == 1 }
            assertEquals(listOf("Show 01", "Show 03", "Show 04"),
                controller.state.value.home?.rows?.single()?.cards?.map { it.title }, "Home stops offering the title")
            controller.undoRemoveFromContinueWatching()
            assertEquals(host.continueCards, controller.state.value.home?.rows?.single()?.cards,
                "and Undo puts it back where it was")
        }
    }

    @Test fun aSecondRemovalReplacesTheUndoOffer() = runBlocking<Unit> {
        ListRemovalHost().use { host ->
            val controller = controller(host, Screen.ContinueWatching)
            controller.mutable.value = controller.state.value.copy(
                continueWatching = ContinueWatchingList("1", host.continueCards, next = null))
            controller.removeFromContinueWatching(continueCard(2))
            until("the first hide") { host.visibility.size == 1 }
            controller.removeFromContinueWatching(continueCard(3))
            until("the second hide") { host.visibility.size == 2 }
            assertEquals(continueCard(3), controller.state.value.continueUndo?.card)
            assertEquals(listOf("Show 01", "Show 04"), controller.state.value.continueWatching?.cards?.map { it.title })
        }
    }
}
