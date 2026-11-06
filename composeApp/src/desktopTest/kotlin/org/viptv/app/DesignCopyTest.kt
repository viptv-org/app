package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.connection.defaultLibraryQuery
import org.viptv.app.features.calendar.CALENDAR_EMPTY_DAY_ALL
import org.viptv.app.features.calendar.CALENDAR_EMPTY_DAY_YOUR_SHOWS
import org.viptv.app.features.calendar.CALENDAR_EMPTY_WEEK
import org.viptv.app.features.calendar.CALENDAR_FAILED_TITLE
import org.viptv.app.features.calendar.CALENDAR_MANAGE
import org.viptv.app.features.calendar.CALENDAR_UNAVAILABLE
import org.viptv.app.features.calendar.CalendarDay
import org.viptv.app.features.calendar.CalendarScreen
import org.viptv.app.features.calendar.calendarEmptyDay
import org.viptv.app.features.calendar.CalendarFilter
import org.viptv.app.features.calendar.TvCalendarScreen
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.features.library.MY_LIST_ARRIVING_BODY
import org.viptv.app.features.library.MY_LIST_ARRIVING_TITLE
import org.viptv.app.features.library.MY_LIST_EMPTY_BODY
import org.viptv.app.features.library.MY_LIST_EMPTY_TITLE
import org.viptv.app.features.library.MY_LIST_FAILED_TITLE
import org.viptv.app.features.library.MY_LIST_FILTERED_BODY
import org.viptv.app.features.library.MY_LIST_FILTERED_TITLE
import org.viptv.app.features.library.MY_LIST_UNAVAILABLE_BODY
import org.viptv.app.features.library.MY_LIST_UNAVAILABLE_TITLE
import org.viptv.app.features.library.libraryEmptyState
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DesignCopyTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun texts(scene: ImageComposeScene) = nodes(scene).map { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text } +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
    }
    private fun shows(scene: ImageComposeScene, text: String) = texts(scene).any { it.contains(text) }

    private fun render(width: Int, height: Int, television: Boolean = false, content: @Composable () -> Unit): ImageComposeScene {
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
        } }
        repeat(12) { scene.render(it * 16_666_667L).close() }
        return scene
    }

    private val days = listOf(CalendarDay("d1", "Mon", "11", "Today", "Mon 11 Nov"), CalendarDay("d2", "Tue", "12", "Tue", "Tue 12 Nov"))

    @Test fun myListUsesTheDesignSystemsEmptyLine() {
        val empty = libraryEmptyState(null, defaultLibraryQuery(), available = true)
        assertEquals(MY_LIST_EMPTY_TITLE, empty.title)
        assertEquals("Add titles with the + button.", empty.message, "copy.md §Empty states, My List")
        assertEquals(MY_LIST_FILTERED_BODY, libraryEmptyState(null, defaultLibraryQuery().copy(type = "movie"), true).message)
        assertEquals(MY_LIST_FILTERED_TITLE, libraryEmptyState(null, defaultLibraryQuery().copy(type = "movie"), true).title)
        val arriving = libraryEmptyState(LibrarySnapshot(Scope("s", "a", "1", 1), 1, defaultLibraryQuery(), emptyList(),
            complete = true, availability = Availability.AVAILABLE, refresh_after_millis = 4_000), defaultLibraryQuery(), true)
        assertEquals(MY_LIST_ARRIVING_TITLE to MY_LIST_ARRIVING_BODY, arriving.title to arriving.message)
        val unavailable = libraryEmptyState(null, defaultLibraryQuery(), available = false)
        assertEquals(MY_LIST_UNAVAILABLE_TITLE to MY_LIST_UNAVAILABLE_BODY, unavailable.title to unavailable.message)
    }

    @Test fun everyMyListLayoutDrawsThatLine() {
        val answered = LibrarySnapshot(Scope("viptv:remote", "account", "1", 1), 1, defaultLibraryQuery(), emptyList(),
            complete = true, availability = Availability.AVAILABLE)
        for ((width, television, name) in listOf(Triple(390, false, "phone"), Triple(1280, false, "desktop"), Triple(1920, true, "TV"))) {
            val scene = render(width, 1080, television) {
                LibraryScreen(emptyList(), available = true, television = television, open = {}, history = {}, home = {},
                    snapshot = answered)
            }
            try {
                assertTrue(shows(scene, MY_LIST_EMPTY_TITLE), "$name: the title follows copy.md")
                assertTrue(shows(scene, MY_LIST_EMPTY_BODY), "$name: the empty line follows copy.md")
                assertFalse(shows(scene, "Tap + on any movie or series"), "$name: the old line is gone")
            } finally { scene.close() }
        }
    }

    @Test fun theCalendarUsesTheDesignSystemsLines() {
        assertEquals("Nothing is scheduled in these dates.", CALENDAR_EMPTY_WEEK)
        assertEquals("The calendar needs series in My List or a Simkl connection.", CALENDAR_UNAVAILABLE)
        assertEquals("None of your shows air this day.", calendarEmptyDay(CalendarFilter.YourShows))
        assertEquals("No releases for this day.", calendarEmptyDay(CalendarFilter.All))
        assertEquals("No releases for this day.", calendarEmptyDay(null))
        assertEquals(CALENDAR_EMPTY_DAY_YOUR_SHOWS, calendarEmptyDay(CalendarFilter.YourShows))
        assertEquals(CALENDAR_EMPTY_DAY_ALL, calendarEmptyDay(CalendarFilter.All))
        assertEquals("Couldn’t load the calendar", CALENDAR_FAILED_TITLE)
        assertEquals("Manage integrations", CALENDAR_MANAGE)
    }

    @Test fun everyCalendarLayoutDrawsItsEmptyLine() {
        for ((width, name) in listOf(390 to "phone", 1280 to "desktop")) {
            val scene = render(width, 1080) { CalendarScreen(days, emptyList(), false, open = {}, manage = {}) }
            try {
                assertTrue(shows(scene, CALENDAR_EMPTY_WEEK), "$name: the empty week follows copy.md")
                assertTrue(shows(scene, CALENDAR_MANAGE), "$name: the way out is the Manage integrations action")
            } finally { scene.close() }
        }
        // TV always shows one day, so its empty answer is the day's own line.
        val tv = render(1920, 1080, television = true) { CalendarScreen(days, emptyList(), true, open = {}, manage = {}) }
        try {
            assertTrue(shows(tv, CALENDAR_EMPTY_DAY_ALL), "TV: the empty day follows copy.md")
        } finally { tv.close() }
        val noSource = render(1920, 1080, television = true) {
            CalendarScreen(days, emptyList(), true, open = {}, manage = {}, available = false)
        }
        try {
            assertTrue(shows(noSource, CALENDAR_UNAVAILABLE), "TV: no source says what the calendar needs")
            assertTrue(shows(noSource, CALENDAR_MANAGE), "TV: with the action that fixes it")
        } finally { noSource.close() }
    }

    @Test fun aFailedCalendarSaysWhyWithTheDesignSystemsWords() {
        val scene = render(1280, 900) { CalendarScreen(days, emptyList(), false, open = {}, manage = {}, error = "Request timed out.") }
        try {
            assertTrue(shows(scene, CALENDAR_FAILED_TITLE), "the failure title follows copy.md")
            assertTrue(shows(scene, "Request timed out."), "and keeps the message the request read")
        } finally { scene.close() }
    }

    @Test fun theTvCalendarRepeatsThem() {
        val scene = render(1920, 1080, television = true) {
            TvCalendarScreen(days, emptyList(), open = {}, manage = {}, error = "Request timed out.")
        }
        try {
            assertTrue(shows(scene, CALENDAR_FAILED_TITLE))
            assertTrue(shows(scene, "Request timed out."))
        } finally { scene.close() }
    }

    @Test fun theOldMyListLineIsGoneFromEverySource() {
        val root = File("src/commonMain")
        val found = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("Tap + on any movie or series") }.map { it.name }.toList()
        assertEquals(emptyList(), found, "copy.md owns the My List empty line")
    }
}