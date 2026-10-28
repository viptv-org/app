package org.viptv.app

import org.viptv.app.updates.*
import kotlin.test.*

class AppUpdatePolicyTest {
    private val feed = UpdateFeed(1301, "0.1.301+abc1234", "https://viptv.example/viptv.apk", "ab".repeat(32), 1000)
    private val hour = UPDATE_CHECK_INTERVAL_MILLIS

    @Test fun onlyAHigherVersionCodeIsAnUpdate() {
        assertEquals(UpdateDecision.Available(feed, required = false), decideUpdate(feed, 1300))
        assertEquals(UpdateDecision.UpToDate, decideUpdate(feed, 1301), "Same version")
        assertEquals(UpdateDecision.UpToDate, decideUpdate(feed, 1400), "Never offers a lower version")
    }

    @Test fun aBuildBelowTheMinimumSupportedVersionMustUpdate() {
        val minimum = feed.copy(minSupportedVersionCode = 1250)
        assertEquals(UpdateDecision.Available(minimum, required = true), decideUpdate(minimum, 1249))
        assertEquals(UpdateDecision.Available(minimum, required = false), decideUpdate(minimum, 1250))
        assertEquals(UpdateDecision.UpToDate, decideUpdate(minimum, 1301))
    }

    @Test fun backgroundChecksRunAtMostOnceAnHour() {
        assertTrue(updateCheckDue(null, 10 * hour, playing = false), "First launch")
        assertFalse(updateCheckDue(10 * hour, 10 * hour + hour - 1, playing = false))
        assertTrue(updateCheckDue(10 * hour, 11 * hour, playing = false))
        assertTrue(updateCheckDue(10 * hour, 9 * hour, playing = false), "A clock that went backwards")
        assertTrue(updateCheckDue(10 * hour, 10 * hour + 1, playing = false, manual = true), "Check for updates")
    }

    @Test fun noCheckRunsWhilePlaying() {
        assertFalse(updateCheckDue(null, 10 * hour, playing = true))
        assertFalse(updateCheckDue(null, 10 * hour, playing = true, manual = true))
    }

    @Test fun aDownloadMustMatchBothSizeAndChecksum() {
        assertTrue(downloadMatches(feed, 1000, "AB".repeat(32)))
        assertFalse(downloadMatches(feed, 1000, "cd".repeat(32)), "Checksum mismatch")
        assertFalse(downloadMatches(feed, 999, feed.sha256), "Size mismatch")
    }

    @Test fun downloadRetriesBackOffThenStop() {
        assertEquals(listOf(15_000L, 60_000L, 300_000L, null), (1..4).map(::downloadRetryDelayMillis))
    }

    @Test fun thePromptShowsAReadyUpdateOutsideThePlayerUntilLater() {
        val ready = UpdateStatus.Ready(feed)
        assertTrue(showUpdatePrompt(ready, allowed = true, snoozedVersionCodes = emptySet()))
        assertFalse(showUpdatePrompt(ready, allowed = false, snoozedVersionCodes = emptySet()), "Never over the player")
        assertFalse(showUpdatePrompt(ready, allowed = true, snoozedVersionCodes = setOf(1301)), "Later")
        assertTrue(showUpdatePrompt(ready, allowed = true, snoozedVersionCodes = setOf(1300)), "A newer build asks again")
        assertFalse(showUpdatePrompt(UpdateStatus.Downloading(feed, 10), allowed = true, snoozedVersionCodes = emptySet()))
    }

    @Test fun theUpdatingScreenTakesOverFromInstallAndBackIsIgnoredOnlyWhileCommitting() {
        assertTrue(showUpdatingScreen(UpdateStatus.Installing(feed)))
        assertTrue(showUpdatingScreen(UpdateStatus.NeedsPermission(feed, true)), "The permission step is part of it")
        assertFalse(showUpdatingScreen(UpdateStatus.Ready(feed)))
        assertFalse(showUpdatePrompt(UpdateStatus.NeedsPermission(feed, true), allowed = true, snoozedVersionCodes = emptySet()),
            "The prompt never sits on top of the Updating screen")
        assertFalse(showUpdatePrompt(UpdateStatus.Installing(feed, confirming = true), allowed = true, snoozedVersionCodes = emptySet()))
        assertTrue(backIgnoredWhileUpdating(UpdateStatus.Installing(feed)))
        assertTrue(backIgnoredWhileUpdating(UpdateStatus.Installing(feed, confirming = true)), "Android is replacing the process")
        assertFalse(backIgnoredWhileUpdating(UpdateStatus.NeedsPermission(feed, true)), "The permission step has nothing to cancel")
        assertFalse(backIgnoredWhileUpdating(UpdateStatus.Ready(feed)))
        assertFalse(backIgnoredWhileUpdating(UpdateStatus.Failed("x", feed, installAttempt = true)))
        assertFalse(backIgnoredWhileUpdating(null))
    }

    /** app#146: the permission step must not let Back reach the page behind it; it leaves as Later. */
    @Test fun backOnThePermissionStepActsAsLater() {
        assertEquals(UpdatingBack.Ignored, updatingBack(UpdateStatus.Installing(feed)))
        assertEquals(UpdatingBack.Ignored, updatingBack(UpdateStatus.Installing(feed, confirming = true)))
        assertEquals(UpdatingBack.Later, updatingBack(UpdateStatus.NeedsPermission(feed, settingsScreen = true)),
            "Before the session starts Back is Later")
        assertEquals(UpdatingBack.Later, updatingBack(UpdateStatus.NeedsPermission(feed, settingsScreen = false)),
            "The Fire TV step is the same step")
        assertEquals(UpdatingBack.Page, updatingBack(UpdateStatus.Ready(feed)))
        assertEquals(UpdatingBack.Page, updatingBack(UpdateStatus.Downloading(feed, 10)))
        assertEquals(UpdatingBack.Page, updatingBack(UpdateStatus.Failed("x", feed, installAttempt = true)))
        assertEquals(UpdatingBack.Page, updatingBack(null))
    }

    @Test fun aFailedInstallComesBackAsTryAgainUntilLater() {
        val failed = UpdateStatus.Failed("Android didn't install the update.", feed, installAttempt = true)
        assertTrue(showUpdatePrompt(failed, allowed = true, snoozedVersionCodes = emptySet()))
        assertFalse(showUpdatePrompt(failed, allowed = true, snoozedVersionCodes = setOf(1301)))
        assertEquals(feed, failed.offeredFeed, "Settings can install again")
        assertTrue(showUpdatePrompt(UpdateStatus.Failed("Couldn't download", feed), allowed = true, snoozedVersionCodes = emptySet()) == false,
            "A download that failed is reported in About only")
    }

    @Test fun lastCheckLabels() {
        assertEquals("Not checked yet", lastCheckLabel(null, 0))
        assertEquals("Checked just now", lastCheckLabel(0, 59_000))
        assertEquals("Checked 5 min ago", lastCheckLabel(0, 5 * 60_000))
        assertEquals("Checked 3 h ago", lastCheckLabel(0, 3 * hour))
        assertEquals("Checked 2 days ago", lastCheckLabel(0, 48 * hour))
    }
}
