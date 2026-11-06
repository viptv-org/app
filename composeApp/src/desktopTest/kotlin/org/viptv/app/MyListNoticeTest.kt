package org.viptv.app

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.Profile
import org.viptv.app.ListRemovalHost.Companion.continueCard
import org.viptv.app.ListRemovalHost.Companion.libraryCard
import org.viptv.app.connection.PreferencesSnapshot
import org.viptv.app.connection.defaultLibraryQuery
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class MyListNoticeTest {
    private fun CoroutineScope.controller(host: ListRemovalHost, screen: Screen = Screen.Library) =
        ApplicationController(this, host.sessionStore).also {
            it.connection = host.remote
            it.mutable.value = ApplicationState(screen = screen, selectedProfile = "1",
                profiles = listOf(Profile("1", "QA", null)), preferences = PreferencesSnapshot(),
                library = host.libraryCards,
                favoriteKeys = host.libraryCards.map { card -> card.identity.kind to card.identity.id }.toSet(),
                libraryPage = LibrarySnapshot(ListRemovalHost.scope, 1, defaultLibraryQuery(), host.libraryCards,
                    complete = true, availability = Availability.AVAILABLE))
        }

    private suspend fun until(what: String, condition: () -> Boolean) =
        withTimeout(5_000) { while (!condition()) delay(10) }.also { assertTrue(condition(), what) }

    @Test fun aSaveSaysAddedToMyListWithNoAction() = runBlocking<Unit> {
        ListRemovalHost().use { host ->
            val controller = controller(host)
            val fresh = Card(MediaIdentity("tt9000001", "movie", emptyList()), provider_id = "library",
                title = "Fresh", badges = emptyList())
            controller.toggleFavorite(fresh)
            controller.job?.join()

            val notice = assertNotNull(controller.state.value.notice, "the save answers")
            assertEquals("Added to My List", notice.text)
            assertNull(notice.action, "a confirmation has nothing to undo")
            assertEquals(1, host.saves.size)
        }
    }

    @Test fun aRemovalOffersUndoAndPutsTheTitleBackWhereItWas() = runBlocking<Unit> {
        ListRemovalHost().use { host ->
            val controller = controller(host)
            controller.removeFromMyList(libraryCard(2))
            controller.job?.join()

            val notice = assertNotNull(controller.state.value.notice)
            assertEquals("Removed from My List", notice.text)
            assertEquals(NoticeAction.Undo, notice.action)
            assertEquals(1, controller.state.value.myListUndo?.index, "Undo needs the row's old place")
            assertEquals(listOf("Show 01", "Show 03"), controller.state.value.libraryPage?.cards?.map { it.title })

            controller.undoRemoval()
            until("the save") { host.saves.size == 1 }
            until("the restored page") { controller.state.value.libraryPage?.cards?.size == 3 }
            assertEquals(listOf("Show 01", "Show 02", "Show 03"), controller.state.value.libraryPage?.cards?.map { it.title },
                "the title returns to its place, not to the end")
            assertNull(controller.state.value.notice, "Undo dismisses the notice")
            assertNull(controller.state.value.myListUndo)
            assertEquals(libraryCard(2).identity, Json.decodeFromJsonElement(Card.serializer(), host.saves.single().getValue("card")).identity,
                "Undo re-saves the title it took away")
        }
    }

    @Test fun theCardsStarOffersTheSameNoticeAsThePagesRemoveAction() = runBlocking<Unit> {
        ListRemovalHost().use { host ->
            val controller = controller(host, Screen.Details)
            controller.toggleFavorite(libraryCard(1))
            controller.job?.join()

            val notice = assertNotNull(controller.state.value.notice, "every trigger of the write gets the notice")
            assertEquals("Removed from My List", notice.text)
            assertEquals(NoticeAction.Undo, notice.action)
            assertEquals(listOf("series/tt0000001"), host.deletes.toList())
        }
    }

    @Test fun aNewerRemovalReplacesTheOlderUndo() = runBlocking<Unit> {
        ListRemovalHost().use { host ->
            val controller = controller(host)
            controller.removeFromMyList(libraryCard(1))
            controller.job?.join()
            controller.removeFromMyList(libraryCard(3))
            controller.job?.join()

            assertEquals(libraryCard(3), controller.state.value.myListUndo?.card, "one removal is offered at a time")
            assertEquals(2, host.deletes.size)
            assertEquals("Removed from My List", controller.state.value.notice?.text)
        }
    }

    @Test fun aContinueWatchingRemovalAnswersThroughTheSameNotice() = runBlocking<Unit> {
        ListRemovalHost().use { host ->
            val controller = controller(host, Screen.ContinueWatching)
            val cards = (1..2).map(::continueCard)
            controller.mutable.value = controller.state.value.copy(continueWatching = ContinueWatchingList("1", cards, next = null))
            controller.removeFromContinueWatching(cards[0])
            until("the hide") { host.visibility.size == 1 }

            assertEquals("Removed from Continue Watching", controller.state.value.notice?.text)
            assertEquals(NoticeAction.Undo, controller.state.value.notice?.action)
            assertNull(controller.state.value.myListUndo, "the two lists share one offer")

            controller.undoRemoval()
            until("the show request") { host.visibility.size == 2 }
            assertNull(controller.state.value.notice, "Undo dismisses the notice")
            assertEquals(cards, controller.state.value.continueWatching?.cards)
        }
    }

    @Test fun dismissingDropsTheOfferToo() = runBlocking<Unit> {
        ListRemovalHost().use { host ->
            val controller = controller(host)
            controller.removeFromMyList(libraryCard(1))
            controller.job?.join()
            assertNotNull(controller.state.value.myListUndo)

            controller.dismissNotice()
            assertNull(controller.state.value.notice)
            assertNull(controller.state.value.myListUndo, "an expired notice has nothing left to undo")
        }
    }

    @Test fun theNoticeLastsLongerWhenItCarriesUndo() {
        assertEquals(5_000L, org.viptv.app.components.noticeMillis(AppNotice("Added to My List")))
        assertEquals(8_000L, org.viptv.app.components.noticeMillis(AppNotice("Removed from My List", NoticeAction.Undo)))
    }
}