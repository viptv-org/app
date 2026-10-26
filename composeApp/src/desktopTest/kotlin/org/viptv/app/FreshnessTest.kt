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

    @Test fun eventChecksCoalesceOnlyWithinTwoSecondsOfTheLastCheck() {
        assertTrue(freshness.checkDue(polling = false))
        freshness.checkFinished()
        assertFalse(freshness.checkDue(polling = false), "The start and resume callbacks of one return coalesce")
        time += 1.seconds
        assertFalse(freshness.checkDue(polling = false))
        time += 1.seconds
        assertTrue(freshness.checkDue(polling = false), "A return two seconds after the last check checks again")
    }

    @Test fun aCheckRequestedBeforeTheAppWentBehindDoesNotCoverTheReturn() {
        freshness.checkRequested()
        assertFalse(freshness.checkDue(polling = false), "A resume right behind a start joins the requested check")
        freshness.foreground(false); freshness.foreground(true)
        assertTrue(freshness.checkDue(polling = false), "That check may have read the tokens before the change")
        freshness.checkFinished()
        assertFalse(freshness.checkDue(polling = false))
    }

    @Test fun pollingChecksEveryFifteenSecondsOnlyWhileForeground() {
        freshness.checkFinished()
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
        assertTrue(freshness.addonsChanged(base + ("addons" to "a2")))
        assertFalse(freshness.addonsChanged(base + ("library" to "5")), "Only the add-on token reloads add-ons")
    }

    @Test fun withoutABaselineEveryObservationIsAChange() {
        assertTrue(freshness.changed(base), "A Home built without tokens cannot be proven current")
        assertTrue(freshness.libraryChanged(base))
        assertTrue(freshness.addonsChanged(base))
        freshness.adopt(base)
        assertFalse(freshness.changed(base))
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
        freshness.homeBuilt(base); freshness.unsupported(); freshness.checkFinished()
        time += 10.minutes
        freshness.reset()
        assertFalse(freshness.homeStale())
        assertTrue(freshness.changed(base), "The old session's baseline is forgotten")
        assertTrue(freshness.checkDue(polling = false))
    }
}
