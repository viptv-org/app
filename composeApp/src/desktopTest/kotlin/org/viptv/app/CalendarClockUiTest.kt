package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.viptv.app.connection.CalendarClock
import org.viptv.app.connection.appDateClock
import org.viptv.app.connection.calendarClock
import org.viptv.app.connection.calendarWindow
import org.viptv.app.features.calendar.CalendarDay
import org.viptv.app.features.calendar.CalendarEntry
import org.viptv.app.features.calendar.CalendarScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.MetadataOrigin
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class CalendarClockUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    @get:Rule val clock = MovableClockRule()

    private fun card(title: String) = MergedCalendarWire.seriesCard(title)
        .copy(provider_id = "simkl", metadata_origin = MetadataOrigin("simkl"))

    private fun entry(title: String, day: String, aired: Boolean, airMillis: Long? = null) =
        CalendarEntry(title.lowercase().replace(' ', '-'), day, "8:30 PM", card(title), subtitle = "S2 E4 · Episode",
            aired = aired, season = 2, episode = 4, releaseDate = "2026-10-14T20:30:00Z", airMillis = airMillis)

    private fun today(days: List<CalendarDay> = calendarWindow(0).days) = days.single { it.label == "Today" }

    @Test fun phoneAddsTheActionAndTheStatusTheMomentTheReleaseAirs() = airs(television = false, width = 390, height = 844, name = "phone")
    @Test fun tvAddsTheActionAndTheStatusTheMomentTheReleaseAirs() = airs(television = true, width = 1920, height = 1080, name = "tv")
    @Test fun phoneReloadsTheWeekWhenTheDayRollsOver() = rollsOver(television = false, width = 390, height = 844, name = "phone")
    @Test fun tvReloadsTheWeekWhenTheDayRollsOver() = rollsOver(television = true, width = 1920, height = 1080, name = "tv")

    private fun airs(television: Boolean, width: Int, height: Int, name: String) {
        clock.at = clock.at.withHour(20).withMinute(0).withSecond(0).withNano(0)
        val base = clock.at.toInstant().toEpochMilli()
        val now = clockState()
        val entries = listOf(entry("Night show", today().id, aired = false, airMillis = base + 30_000))
        scene(television, width, height, { entries }, now) { s ->
            s.until("the row") { s.nodes().any { text(it).contains("Night show") } }
            assertFalse(s.nodes().any { text(it) == "Find S2 E4" }, "$name: an upcoming release has no action yet")
            assertTrue(s.nodes().any { text(it).contains("in 1m") }, "$name: an upcoming release counts down")
            s.save("$name-calendar-before-airing")
            clock.at = clock.at.plusMinutes(1)
            now.value = calendarClock()
            s.until("the release's action") { s.nodes().any { text(it) == "Find S2 E4" } }
            assertTrue(s.nodes().any { text(it).contains("Out now") }, "$name: the row turns into Out now at its air time")
            s.save("$name-calendar-after-airing")
        }
    }

    private fun rollsOver(television: Boolean, width: Int, height: Int, name: String) {
        clock.at = clock.at.withHour(23).withMinute(58)
        val before = today()
        val after = calendarWindow(0).days[4]
        val days = mutableStateOf(calendarWindow(0).days)
        val now = clockState()
        var reloads = 0
        val entries = listOf(entry("Night show", before.id, aired = true), entry("Next day show", after.id, aired = true))
        scene(television, width, height, { entries }, now, days = { days.value },
            dayChanged = { reloads++; days.value = calendarWindow(0).days }) { s ->
            assertEquals(before.id, today(days.value).id)
            assertEquals(0, reloads, "$name: a freshly loaded week needs no reload")
            if (television) assertFalse(s.nodes().any { text(it).contains("Next day show") }, "$name: yesterday is the active day")
            clock.at = clock.at.plusMinutes(7)
            now.value = calendarClock()
            s.until("the reload") { reloads == 1 }
            val today = today(days.value)
            assertNotEquals(before.id, today.id, "$name: Today moved to the new day")
            if (television) s.until("the new day's release") { s.nodes().any { text(it).contains("Next day show") } }
            else s.until("the new day's header") { s.nodes().any { text(it) == today.fullDate } }
            s.frames(30)
            assertEquals(1, reloads, "$name: the reload is asked for once")
            s.save("$name-calendar-after-rollover")
        }
    }

    private fun clockState() = mutableStateOf(calendarClock())

    private inner class Scene(val scene: ImageComposeScene, val capture: File?) {
        private var frame = 0L
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) {
                check(System.nanoTime() < deadline) { "timed out waiting for $what; texts=" + nodes().map(::text).filter(String::isNotBlank) }
                frames(2)
            }
        }
        fun save(variant: String) {
            val dir = capture ?: return
            frames(30); dir.mkdirs()
            scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(dir, "$variant.png").writeBytes(it.bytes) } }
        }
    }

    private fun scene(television: Boolean, width: Int, height: Int, entries: () -> List<CalendarEntry>, now: MutableState<CalendarClock>,
        days: () -> List<CalendarDay> = { calendarWindow(0).days }, dayChanged: () -> Unit = {}, body: (Scene) -> Unit) {
        val capture = System.getenv("VIPTV_CALENDAR_CAPTURE_DIR")?.let(::File)
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television) {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    CalendarScreen(days(), entries(), television, {}, {}, controls = true, watch = {},
                        now = now.value, dayChanged = dayChanged)
                }
            }
        }
        try {
            val s = Scene(scene, capture)
            s.frames()
            body(s)
        } finally { scene.close() }
    }

    class MovableClock(var at: ZonedDateTime) : Clock() {
        override fun getZone(): ZoneId = at.zone
        override fun withZone(zone: ZoneId): Clock = MovableClock(at.withZoneSameInstant(zone))
        override fun instant(): Instant = at.toInstant()
    }

    class MovableClockRule : TestRule {
        val clock = MovableClock(ZonedDateTime.now())
        var at: ZonedDateTime
            get() = clock.at
            set(value) { clock.at = value }

        override fun apply(base: Statement, description: Description) = object : Statement() {
            override fun evaluate() {
                val previous = appDateClock
                appDateClock = clock
                try { base.evaluate() } finally { appDateClock = previous }
            }
        }
    }

    private fun text(node: SemanticsNode) =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}