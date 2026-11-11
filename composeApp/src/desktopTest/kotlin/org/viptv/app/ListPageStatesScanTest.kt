package org.viptv.app

import java.io.File
import kotlin.test.*

class ListPageStatesScanTest {
    private val root = File("src/commonMain/kotlin/org/viptv/app")
    private fun source(name: String) = File(root, name).readText()

    private val listPages = listOf(
        "features/library/LibraryScreen.kt",
        "features/library/TvLibraryScreen.kt",
        "features/calendar/CalendarScreen.kt",
        "features/calendar/TvCalendarScreen.kt",
        "features/history/HistoryScreen.kt",
        "features/history/TvHistoryScreen.kt",
        "features/browse/BrowseScreen.kt",
        "features/browse/BrowserBrowseScreen.kt",
        "features/browse/TvBrowseScreen.kt",
        "features/search/SearchScreen.kt")

    @Test fun everyListPageTakesItsBodyFromTheSharedState() {
        assertTrue(root.isDirectory, "run from the composeApp module: ${root.absolutePath}")
        for (page in listPages) {
            val text = source(page)
            assertTrue(Regex("""\blistPageState\(""").containsMatchIn(text), "$page derives its body from listPageState")
        }
    }

    @Test fun everyListPageShowsItsEmptyStateOnlyOnTheAnsweredState() {
        for (page in listPages) {
            assertTrue(source(page).contains("ListPageState.Empty"), "$page shows its empty state only on ListPageState.Empty")
        }
    }

    @Test fun listSkeletonsComeFromTheSharedShapes() {
        for (page in listPages) {
            val text = source(page)
            val drawsSkeleton = listOf("PosterSkeleton(", "ReleaseRowSkeleton(", "ListSkeletonTile(").any(text::contains)
            if (!drawsSkeleton) continue
            assertTrue(text.contains("ListPageState.Loading"), "$page draws its skeleton for ListPageState.Loading")
        }
        assertFalse(File(root, "features/browse").walkTopDown().any { it.isFile && it.readText().contains("BrowseSkeletonGrid") },
            "the browse grid's own skeleton is replaced by PosterSkeleton")
        assertTrue(source("features/library/LibraryScreen.kt").contains("PosterSkeleton("), "My List draws poster tiles")
        assertTrue(source("features/calendar/CalendarScreen.kt").contains("ReleaseRowSkeleton("), "the calendar draws release rows")
    }

    @Test fun continueWatchingReadsItsOwnLoadErrorAndDrawsTheSharedBlock() {
        val route = source("features/continuewatching/ContinueWatchingRoute.kt")
        assertTrue(route.contains("continueWatchingError"), "the page reads its own load failure")
        assertFalse(route.contains("state.error"), "an action's failure never becomes the page state")
        for (screen in listOf("features/continuewatching/ContinueWatchingScreen.kt",
            "features/continuewatching/TvContinueWatchingScreen.kt")) {
            val text = source(screen)
            assertTrue(text.contains("ContinueWatchingState("), "$screen draws the shared page state block")
            assertFalse(text.contains("page.error?.let"), "$screen prints the request's message once, as the block's body")
        }
    }

    @Test fun homeTakesItsPageStateFromTheSameRuleAndBlock() {
        assertTrue(Regex("""\blistPageState\(""").containsMatchIn(source("features/home/HomeCopy.kt")),
            "Home's page state is the shared rule")
        for (page in listOf("features/home/HomeScreen.kt", "features/home/TvHomeScreen.kt")) {
            val text = source(page)
            assertTrue(text.contains("homePageState("), "$page derives its body from the shared page state")
            assertTrue(text.contains("HomeStatus("), "$page draws the shared page state block")
        }
        val block = source("features/home/HomeStatus.kt")
        assertTrue(block.contains("PageState("), "the block is the shared component")
        assertFalse(block.contains("Surface("), "a page state is never a card")
    }

    @Test fun theCalendarDrawsTheSharedBlockForItsPageStates() {
        val block = source("features/calendar/CalendarStatus.kt")
        assertTrue(block.contains("PageState("), "the Calendar's page state is the shared block")
        assertFalse(block.contains("Surface("), "a page state is never a card")
        for (page in listOf("features/calendar/CalendarScreen.kt", "features/calendar/TvCalendarScreen.kt")) {
            val text = source(page)
            assertTrue(text.contains("CalendarStatus("), "$page draws the shared page state block")
            assertFalse(Regex("""Text\(if \(available\) CALENDAR_EMPTY_WEEK""").containsMatchIn(text),
                "$page draws its own empty line and action")
            assertFalse(text.contains("TextButton(manage)"), "$page keeps its own empty action")
        }
        val dayLine = source("features/calendar/TvCalendarScreen.kt")
        assertTrue(dayLine.contains("calendarEmptyDay("), "an empty day inside a non-empty week keeps its inline line")
    }

    @Test fun theOldHomeStatesAreGoneFromEverySource() {
        val old = listOf("Could not load Home", "Retry Home", "No catalogs configured", "Nothing to show yet",
            "Home isn’t available yet", "Your catalogs could not load", "Refreshing Home…")
        val found = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> old.filter { file.readText().contains(it) }.map { "${file.name}: $it" } }.toList()
        assertEquals(emptyList(), found, "copy.md owns Home's empty and failure strings")
    }

    @Test fun everyCatalogPageDrawsTheSameEmptyAndFailedState() {
        assertTrue(source("features/browse/CatalogPageState.kt").contains("PageState("), "the catalog states are the shared block")
        for (page in listOf("features/browse/BrowseScreen.kt", "features/browse/BrowserBrowseScreen.kt",
            "features/browse/TvBrowseScreen.kt")) {
            val text = source(page)
            assertTrue(text.contains("CatalogPageState("), "$page draws the shared catalog state")
            assertFalse(text.contains("Nothing matches these filters\""), "$page never writes the headline itself")
            assertFalse(text.contains("all \$count filters"), "$page never prints a raw filter count")
        }
    }

    @Test fun continueWatchingStatesComeFromTheSharedRule() {
        val page = source("features/continuewatching/ContinueWatchingRoute.kt")
        assertTrue(page.contains("val state get() = listPageState("), "the page state is the shared rule")
        for (screen in listOf("features/continuewatching/ContinueWatchingScreen.kt",
            "features/continuewatching/TvContinueWatchingScreen.kt")) {
            val text = source(screen)
            assertFalse(Regex("""\bpage\.(loading|empty)\b""").containsMatchIn(text), "$screen reads the shared state, not its own flags")
        }
    }
}