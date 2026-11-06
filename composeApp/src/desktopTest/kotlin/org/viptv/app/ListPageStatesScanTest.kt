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

    @Test fun theContinueWatchingSegmentComesFromTheSharedRule() {
        val page = source("features/continuewatching/ContinueWatchingRoute.kt")
        assertTrue(page.contains("val state get() = listPageState("), "the page state is the shared rule")
        for (screen in listOf("features/library/LibraryScreen.kt", "features/library/TvLibraryScreen.kt")) {
            val text = source(screen)
            assertTrue(text.contains("section?.page?.state"), "$screen draws the segment from the shared state")
            assertFalse(Regex("""\bsection\?\.page\?\.(loading|empty)\b""").containsMatchIn(text),
                "$screen reads the shared state, not its own flags")
        }
    }
}