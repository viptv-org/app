package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.components.ListPageState
import org.viptv.app.components.ListsLoading
import org.viptv.app.components.listPageState
import org.viptv.app.connection.defaultLibraryQuery
import org.viptv.app.features.calendar.CalendarDay
import org.viptv.app.features.calendar.CalendarScreen
import org.viptv.app.features.calendar.TvCalendarScreen
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.features.library.TvLibraryScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Availability
import org.viptv.engine.wire.LibrarySnapshot
import org.viptv.engine.wire.Scope
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ListPageStatesTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val myListEmpty = "Your list is empty."
    private val calendarEmpty = "Nothing is scheduled in these dates."

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }

    private fun texts(scene: ImageComposeScene) = nodes(scene).map { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text } +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
    }

    private fun skeleton(scene: ImageComposeScene) = texts(scene).count { it.contains(ListsLoading) }

    private fun render(width: Int, height: Int, television: Boolean = false, content: @Composable () -> Unit): ImageComposeScene {
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
        } }
        repeat(12) { scene.render(it * 16_666_667L).close() }
        return scene
    }

    private fun page(cards: List<org.viptv.engine.wire.Card> = emptyList(), query: org.viptv.engine.wire.LibraryQuery = defaultLibraryQuery(),
        answered: Boolean) = LibrarySnapshot(Scope("viptv:remote", "account", "1", 1), 1, query,
        cards, next_cursor = null, complete = true, availability = Availability.AVAILABLE).takeIf { answered }

    @Test fun theStateNeverSaysEmptyWhileAnAnswerIsStillComing() {
        assertEquals(ListPageState.Content, listPageState(3, busy = true, error = null), "rows in hand are the page")
        assertEquals(ListPageState.Content, listPageState(3, busy = false, error = "boom"), "rows stay under an error")
        assertEquals(ListPageState.Loading, listPageState(0, busy = true, error = null), "a request is open")
        assertEquals(ListPageState.Loading, listPageState(0, busy = false, error = null, answered = false), "nothing answered yet")
        assertEquals(ListPageState.Loading, listPageState(0, busy = true, error = "boom"), "a retry is a request in flight")
        assertEquals(ListPageState.Failed, listPageState(0, busy = false, error = "boom"), "the failure waits for the answer")
        assertEquals(ListPageState.Empty, listPageState(0, busy = false, error = null), "the answer was empty")
        assertEquals(ListPageState.Empty, listPageState(0, busy = false, error = null, answered = true))
    }

    @Test fun myListPaintsPosterSkeletonsUntilItsFirstAnswer() {
        for (width in listOf(390, 1280)) {
            val loading = render(width, 900) {
                LibraryScreen(emptyList(), available = true, television = false, open = {}, history = {}, home = {},
                    snapshot = page(answered = false), busy = true)
            }
            try {
                assertFalse(texts(loading).any { it.contains(myListEmpty) }, "$width: no empty state while the first request is open")
                assertTrue(skeleton(loading) > 0, "$width: the page draws its poster skeleton")
                assertFalse(texts(loading).any { it.contains("Browse Home") }, "$width: no action before the answer")
            } finally { loading.close() }

            val answered = render(width, 900) {
                LibraryScreen(emptyList(), available = true, television = false, open = {}, history = {}, home = {},
                    snapshot = page(answered = true), busy = false)
            }
            try {
                assertTrue(texts(answered).any { it.contains(myListEmpty) }, "$width: the empty state follows the empty answer")
                assertTrue(texts(answered).any { it.contains("Browse Home") }, "$width: with the action that fixes it")
                assertEquals(0, skeleton(answered), "$width: and no skeleton")
            } finally { answered.close() }
        }
    }

    @Test fun myListKeepsItsAnsweredEmptyStateThroughABackgroundRefresh() {
        val refreshing = render(390, 900) {
            LibraryScreen(emptyList(), available = true, television = false, open = {}, history = {}, home = {},
                snapshot = page(answered = true), busy = true)
        }
        try {
            assertTrue(texts(refreshing).any { it.contains(myListEmpty) }, "a refresh of an answered list does not blink into a skeleton")
            assertFalse(texts(refreshing).any { it.contains("Loading matching titles") })
            assertEquals(0, skeleton(refreshing))
        } finally { refreshing.close() }
    }

    @Test fun myListWaitsForTheAnswerToAnotherFilter() {
        val filtered = render(390, 900) {
            LibraryScreen(emptyList(), available = true, television = false, open = {}, history = {}, home = {},
                snapshot = page(answered = true), busy = true,
                query = defaultLibraryQuery().copy(type = "movie"))
        }
        try {
            assertFalse(texts(filtered).any { it.contains(myListEmpty) || it.contains("No matching titles.") },
                "the previous filter's empty answer is not this filter's")
            assertTrue(skeleton(filtered) > 0, "the filtered list loads behind its own skeleton")
        } finally { filtered.close() }
    }

    @Test fun theCalendarPaintsReleaseRowSkeletonsWhileItsWeekLoads() {
        val days = listOf(CalendarDay("d1", "Mon", "11", "Today", "Mon 11 Nov"), CalendarDay("d2", "Tue", "12", "Tue", "Tue 12 Nov"))
        for (width in listOf(390, 1280)) {
            val loading = render(width, 900) { CalendarScreen(days, emptyList(), television = false, open = {}, manage = {}, busy = true) }
            try {
                assertFalse(texts(loading).any { it.contains(calendarEmpty) }, "$width: no empty state while the week loads")
                assertTrue(skeleton(loading) > 0, "$width: the page draws its release row skeleton")
            } finally { loading.close() }

            val answered = render(width, 900) { CalendarScreen(days, emptyList(), television = false, open = {}, manage = {}, busy = false) }
            try {
                assertTrue(texts(answered).any { it.contains(calendarEmpty) }, "$width: the empty state follows the empty answer")
                assertEquals(0, skeleton(answered), "$width: and no skeleton")
            } finally { answered.close() }
        }
    }

    @Test fun theTvPagesDrawNoEmptyStateBeforeTheirAnswer() {
        val library = render(1920, 1080, television = true) {
            TvLibraryScreen(emptyList(), available = true, open = {}, home = {}, snapshot = page(answered = false), busy = true)
        }
        try {
            assertFalse(texts(library).any { it.contains(myListEmpty) }, "TV has no skeleton, and no empty state either while it loads")
            assertEquals(0, skeleton(library))
        } finally { library.close() }

        val days = listOf(CalendarDay("d1", "Mon", "11", "Today", "Mon 11 Nov"))
        val calendar = render(1920, 1080, television = true) {
            TvCalendarScreen(days, emptyList(), open = {}, manage = {}, busy = true)
        }
        try {
            assertFalse(texts(calendar).any { it.contains(calendarEmpty) })
            assertEquals(0, skeleton(calendar))
        } finally { calendar.close() }
    }
}