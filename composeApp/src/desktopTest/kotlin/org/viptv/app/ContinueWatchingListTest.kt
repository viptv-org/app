package org.viptv.app

import kotlinx.coroutines.*
import org.viptv.app.ContinueWatchingHost.Companion.card
import org.viptv.app.ContinueWatchingHost.Companion.home
import org.viptv.app.connection.PreferencesSnapshot
import org.viptv.app.state.*
import org.viptv.engine.wire.ContinuationPage
import org.viptv.engine.wire.Scope
import kotlin.test.*

class ContinueWatchingListTest {
    private fun CoroutineScope.controller(host: ContinueWatchingHost, shown: Int, screen: Screen = Screen.Home) =
        ApplicationController(this, host.sessionStore).also {
            it.connection = host.remote
            it.mutable.value = ApplicationState(screen = screen, selectedProfile = "1", home = home(shown), preferences = PreferencesSnapshot())
        }
    private suspend fun until(what: String, condition: () -> Boolean) =
        withTimeout(5_000) { while (!condition()) delay(10) }.also { assertTrue(condition(), what) }

    @Test fun theEndCardAppearsOnlyWhenTheListHoldsMoreThanTwelve() = runBlocking<Unit> {
        for ((total, expected) in listOf(5 to null, 12 to null, 13 to ContinueWatchingMore(13), 40 to ContinueWatchingMore(null))) {
            ContinueWatchingHost(total).use { host ->
                val controller = controller(host, shown = total)
                controller.checkContinueWatching()
                if (total < 12) {
                    delay(100)
                    assertTrue(host.reads.isEmpty(), "a row of $total is the whole list: nothing to read")
                } else until("$total: the list's first page landed") { controller.state.value.continueWatching != null }
                assertEquals(expected, controller.state.value.continueWatchingMore(), "$total titles in progress")
                controller.checkContinueWatching(); delay(50)
                assertTrue(host.reads.size <= 1, "${host.reads}")
            }
        }
    }

    @Test fun theLocalEngineAndOtherProfilesNeverShowTheEndCard() {
        val list = ContinueWatchingList("1", (1..40).map(::card), next = "24")
        val state = ApplicationState(selectedProfile = "1", home = home(12), continueWatching = list)
        assertEquals(ContinueWatchingMore(null), state.continueWatchingMore())
        assertNull(state.copy(selectedProfile = "2").continueWatchingMore(), "another profile's list")
        assertNull(state.copy(continueWatching = null).continueWatchingMore())
    }

    @Test fun thePagePagesThroughFortyEntriesWithTheServerCursor() = runBlocking<Unit> {
        ContinueWatchingHost(40).use { host ->
            val controller = controller(host, shown = 12)
            controller.navigate(Screen.ContinueWatching)
            until("the first page") { controller.state.value.continueWatching != null && !controller.state.value.busy }
            assertEquals((1..24).map(::card), controller.state.value.continueWatching?.cards)
            assertEquals("24", controller.state.value.continueWatching?.next)
            controller.continueWatchingMore()
            until("the second page") { controller.state.value.continueWatching?.cards?.size == 40 && !controller.state.value.busy }
            assertEquals((1..40).map(::card), controller.state.value.continueWatching?.cards)
            assertNull(controller.state.value.continueWatching?.next, "the list is complete")
            controller.continueWatchingMore(); delay(50)
            assertEquals(listOf("0+24", "24+24"), host.reads.toList(), "one read per page, and none past the end")
            assertNull(controller.state.value.error)
        }
    }

    @Test fun openingThePageAfterHomeReadTheListShowsItAtOnce() = runBlocking<Unit> {
        ContinueWatchingHost(30).use { host ->
            val controller = controller(host, shown = 12)
            controller.checkContinueWatching()
            until("Home's read") { controller.state.value.continueWatching != null }
            controller.navigate(Screen.ContinueWatching)
            assertFalse(controller.state.value.busy, "no loading cover: the cards are already here")
            assertEquals(24, controller.state.value.continueWatching?.cards?.size)
            delay(50)
            assertEquals(listOf("0+24"), host.reads.toList())
        }
    }

    @Test fun activatingAnEntryResumesThatEpisodeAndBackReturnsToThePage() = runBlocking<Unit> {
        ContinueWatchingHost(40).use { host ->
            val controller = controller(host, shown = 12)
            controller.navigate(Screen.ContinueWatching)
            until("the first page") { controller.state.value.continueWatching != null && !controller.state.value.busy }
            controller.continueWatchingMore()
            until("the second page") { controller.state.value.continueWatching?.cards?.size == 40 }
            val entry = controller.state.value.continueWatching!!.cards[29]
            controller.playTitle(entry)
            val state = controller.state.value
            assertEquals(Screen.Sources, state.screen, "the card opens its sources at once (app#11)")
            assertEquals(card(30), state.sourceTitle, "sources are for exactly that episode, not the series' first")
            assertEquals(Screen.ContinueWatching, state.detailReturnScreen)
            assertEquals(Screen.Details, state.sourceReturnScreen)
            controller.navigateBack()
            assertEquals(Screen.Details, controller.state.value.screen, "Back from sources opens the title's Details")
            controller.navigateBack()
            assertEquals(Screen.ContinueWatching, controller.state.value.screen, "Back again returns to the page")
            assertEquals(40, controller.state.value.continueWatching?.cards?.size, "with every loaded page")
            controller.job?.cancel()
        }
    }

    @Test fun backFromThePageReturnsHome() = runBlocking<Unit> {
        ContinueWatchingHost(40).use { host ->
            val controller = controller(host, shown = 12)
            controller.navigate(Screen.ContinueWatching)
            assertTrue(controller.state.value.canNavigateBack())
            assertTrue(controller.state.value.systemBackStaysInApp(), "Android and TV Back stay in the app")
            controller.navigateBack()
            assertEquals(Screen.Home, controller.state.value.screen)
            controller.mutable.value = controller.state.value.copy(screen = Screen.ContinueWatching, navBack = emptyList())
            controller.navigateBack()
            assertEquals(Screen.Home, controller.state.value.screen)
            controller.job?.cancel()
        }
    }

    @Test fun aQuietRefreshUpdatesThePageWithoutALoadingCover() = runBlocking<Unit> {
        ContinueWatchingHost(40).use { host ->
            val controller = controller(host, shown = 12, screen = Screen.ContinueWatching)
            controller.loadContinueWatching()
            until("the first page") { controller.state.value.continueWatching != null && !controller.state.value.busy }
            controller.continueWatchingMore()
            until("the second page") { controller.state.value.continueWatching?.cards?.size == 40 && !controller.state.value.busy }
            host.count = 39
            val busy = mutableListOf<Boolean>()
            val watch = launch { controller.state.collect { busy += it.busy } }
            controller.reloadVisibleContinueWatching(QuietOwner(host.remote, null, "1"))
            watch.cancel()
            assertEquals((1..39).map(::card), controller.state.value.continueWatching?.cards, "the loaded pages re-read in one request")
            assertEquals("0+40", host.reads.last())
            assertFalse(busy.any { it }, "the cards never hide behind a loading state")
            controller.checkContinueWatching(); delay(50)
            assertEquals(3, host.reads.size)
        }
    }

    @Test fun aRefreshKeepsPagesPastTheServerCeiling() {
        val shown = ContinueWatchingList("1", (1..130).map(::card), next = "130")
        val page = ContinuationPage(Scope("viptv:remote", "account", "1", 2), 2, (2..101).map(ContinueWatchingHost::continueEntry), "100", false)
        val refreshed = shown.refreshedBy("1", page, 100)
        assertEquals((2..130).map(::card), refreshed.cards, "the fresh head, then the tail the viewer paged to")
        assertEquals("130", refreshed.next)
        val complete = page.copy(next_cursor = null, complete = true)
        assertEquals((2..101).map(::card), shown.refreshedBy("1", complete, 100).cards, "a complete fresh list replaces it")
        assertEquals((2..101).map(::card), (null as ContinueWatchingList?).refreshedBy("1", page, 100).cards)
    }
}
