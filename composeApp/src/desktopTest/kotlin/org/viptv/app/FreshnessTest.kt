package org.viptv.app

import org.viptv.app.state.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class FreshnessTest {
    private val time = TestTimeSource()
    private val freshness = Freshness(time)
    private val base = mapOf("library" to "4", "configuration" to "2", "addons" to "a1", "policy" to "0", "preferences" to "1", "profiles" to "7")

    @Test fun eventChecksRunAtMostEveryTenSecondsAndCoalesce() {
        assertTrue(freshness.checkDue(polling = false))
        freshness.checked()
        assertFalse(freshness.checkDue(polling = false), "Repeated foreground callbacks coalesce")
        time += 9.seconds
        assertFalse(freshness.checkDue(polling = false))
        time += 1.seconds
        assertTrue(freshness.checkDue(polling = false))
    }

    @Test fun pollingChecksEveryFifteenSecondsOnlyWhileForeground() {
        freshness.checked()
        time += 14.seconds
        assertFalse(freshness.checkDue(polling = true))
        time += 1.seconds
        assertTrue(freshness.checkDue(polling = true))
        freshness.foreground(false)
        assertFalse(freshness.checkDue(polling = true), "A hidden or background client never checks")
        assertFalse(freshness.checkDue(polling = false))
        freshness.foreground(true)
        assertTrue(freshness.checkDue(polling = true))
    }

    @Test fun unsupportedServersAreNeverChecked() {
        freshness.unsupported()
        assertFalse(freshness.checkDue(polling = false))
        assertFalse(freshness.checkDue(polling = true))
    }

    @Test fun unchangedRevisionsNeedNothingAndAnyChangeNeedsAReload() {
        freshness.homeBuilt(base)
        assertFalse(freshness.changed(base))
        assertFalse(freshness.changed(base.toMap()))
        assertTrue(freshness.changed(base + ("library" to "5")))
        assertTrue(freshness.changed(base + ("profiles" to "8")))
        assertTrue(freshness.changed(base - "addons"), "A token that disappears is a change")
        assertTrue(freshness.libraryChanged(base + ("library" to "5")))
        assertFalse(freshness.libraryChanged(base + ("addons" to "a2")))
    }

    @Test fun observationsWithoutABaselineAreAdoptedNotTreatedAsChanges() {
        assertFalse(freshness.changed(base))
        freshness.adopt(base)
        assertTrue(freshness.changed(base + ("configuration" to "3")))
    }

    @Test fun homeIsStaleAfterFiveMinutesAndRefreshesOnlyWhenTheViewerIsIdle() {
        freshness.homeBuilt(base)
        time += 4.minutes + 59.seconds
        assertFalse(freshness.homeStale())
        time += 1.seconds
        assertTrue(freshness.homeStale())
        freshness.input()
        time += 19.seconds
        assertFalse(freshness.idle(), "Input in the last 20 seconds means the viewer is active")
        time += 1.seconds
        assertTrue(freshness.idle())
    }

    @Test fun anUnbuiltHomeIsNotStaleAndResetForgetsTheSession() {
        assertFalse(freshness.homeStale())
        freshness.homeBuilt(base); freshness.unsupported(); freshness.checked()
        time += 10.minutes
        freshness.reset()
        assertFalse(freshness.homeStale())
        assertFalse(freshness.changed(base + ("library" to "9")))
        assertTrue(freshness.checkDue(polling = false))
    }
}
