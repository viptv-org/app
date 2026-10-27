package org.viptv.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.viptv.app.updates.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdaterTest {
    private val hour = UPDATE_CHECK_INTERVAL_MILLIS

    // Not backgroundScope: advanceUntilIdle does not run background work once the test body is idle.
    private fun TestScope.updater(platform: FakeUpdatePlatform, origin: String? = "https://viptv.example"): AppUpdater {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        backgroundScope.coroutineContext[Job]!!.invokeOnCompletion { scope.cancel() }
        return AppUpdater(platform, scope) { updateFeedUrl(origin, null) }
    }

    @Test fun launchChecksTheServerDownloadsQuietlyThenOffersTheUpdate() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        val updater = updater(platform)
        updater.foreground(playing = false)
        advanceUntilIdle()
        assertEquals(listOf("https://viptv.example/updates/android/update.json"), platform.fetched)
        val status = assertIs<UpdateStatus.Ready>(updater.state.value.status)
        assertEquals("https://viptv.example/updates/android/viptv-1301.apk", status.feed.url)
        assertEquals(platform.now, platform.lastCheckMillis, "The hourly limit survives restarts")
        assertTrue(showUpdatePrompt(updater.state.value.status, true, updater.state.value.snoozed))
    }

    @Test fun anOlderOrSameBuildIsUpToDateAndStaleDownloadsAreDeleted() = runTest {
        for (code in listOf(1300L, 1299L)) {
            val platform = FakeUpdatePlatform(feedJson(code))
            val updater = updater(platform)
            updater.foreground(playing = false); advanceUntilIdle()
            assertEquals(UpdateStatus.UpToDate, updater.state.value.status)
            assertEquals(0, platform.downloads)
            assertEquals(listOf<UpdateFeed?>(null), platform.kept)
        }
    }

    @Test fun returningWithinTheHourDoesNotCheckAgainButCheckForUpdatesDoes() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1300))
        val updater = updater(platform)
        updater.foreground(false); advanceUntilIdle()
        platform.now += hour - 1
        updater.foreground(false); advanceUntilIdle()
        assertEquals(1, platform.fetched.size)
        updater.checkNow(); advanceUntilIdle()
        assertEquals(2, platform.fetched.size)
        platform.now += hour
        updater.foreground(false); advanceUntilIdle()
        assertEquals(3, platform.fetched.size)
    }

    @Test fun noCheckRunsWhilePlayingAndADueCheckRunsWhenThePlayerCloses() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        val updater = updater(platform)
        updater.foreground(playing = true); advanceUntilIdle()
        assertTrue(platform.fetched.isEmpty())
        updater.playingChanged(false); advanceUntilIdle()
        assertEquals(1, platform.fetched.size)
        assertIs<UpdateStatus.Ready>(updater.state.value.status)
    }

    @Test fun theDownloadPausesWhileThePlayerIsOpenAndResumesAfter() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        platform.downloadGate = CompletableDeferred()
        val updater = updater(platform)
        updater.foreground(false); advanceUntilIdle()
        assertEquals(UpdateStatus.Downloading(parseUpdateFeed(feedJson(1301), "https://viptv.example/updates/android/update.json"), 50),
            updater.state.value.status)
        updater.playingChanged(true); advanceUntilIdle()
        assertTrue(assertIs<UpdateStatus.Downloading>(updater.state.value.status).paused)
        platform.downloadGate = null
        updater.playingChanged(false); advanceUntilIdle()
        assertEquals(2, platform.downloads, "The platform resumes the partial file")
        assertIs<UpdateStatus.Ready>(updater.state.value.status)
    }

    @Test fun aDownloadPausedInThePlayerResumesWhenTheAppReturnsOutsideIt() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        platform.downloadGate = CompletableDeferred()
        val updater = updater(platform)
        updater.foreground(false); advanceUntilIdle()
        updater.playingChanged(true); advanceUntilIdle()
        platform.downloadGate = null
        // The viewer left the app from the player and came back to Home: the return reports it first.
        updater.foreground(playing = false); advanceUntilIdle()
        updater.playingChanged(false); advanceUntilIdle()
        assertIs<UpdateStatus.Ready>(updater.state.value.status)
        assertEquals(2, platform.downloads)
    }

    @Test fun leavingAndroidsConfirmationWithoutAnAnswerLetsInstallRunAgain() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        val updater = updater(platform)
        updater.foreground(false); advanceUntilIdle()
        updater.install(); advanceUntilIdle()
        assertIs<UpdateStatus.Installing>(updater.state.value.status)
        updater.foreground(false); advanceUntilIdle()
        assertIs<UpdateStatus.Ready>(updater.state.value.status)
        assertTrue(showUpdatePrompt(updater.state.value.status, true, updater.state.value.snoozed))
        updater.install(); advanceUntilIdle()
        assertEquals(2, platform.installs.size)
    }

    @Test fun aChecksumMismatchIsNeverOffered() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        platform.downloadOutcomes += UpdateVerificationException("sha256 mismatch")
        val updater = updater(platform)
        updater.foreground(false); advanceUntilIdle()
        assertIs<UpdateStatus.Failed>(updater.state.value.status)
        assertFalse(showUpdatePrompt(updater.state.value.status, true, emptySet()))
        updater.install(); advanceUntilIdle()
        assertTrue(platform.installs.isEmpty())
    }

    @Test fun aFailedDownloadRetriesWithBackoff() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        repeat(2) { platform.downloadOutcomes += IllegalStateException("connection reset") }
        val updater = updater(platform)
        updater.foreground(false)
        advanceTimeBy(1); assertEquals(1, platform.downloads)
        advanceTimeBy(15_000); assertEquals(2, platform.downloads)
        advanceTimeBy(60_000); assertEquals(3, platform.downloads)
        advanceUntilIdle()
        assertIs<UpdateStatus.Ready>(updater.state.value.status)
    }

    @Test fun downloadsGiveUpAfterTheLastRetry() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        repeat(4) { platform.downloadOutcomes += IllegalStateException("offline") }
        val updater = updater(platform)
        updater.foreground(false); advanceUntilIdle()
        assertEquals(4, platform.downloads)
        assertIs<UpdateStatus.Failed>(updater.state.value.status)
    }

    @Test fun laterHidesThePromptForThisVersionOnly() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        val updater = updater(platform)
        updater.foreground(false); advanceUntilIdle()
        updater.later()
        assertFalse(showUpdatePrompt(updater.state.value.status, true, updater.state.value.snoozed))
        platform.feed = feedJson(1302); platform.now += hour
        updater.foreground(false); advanceUntilIdle()
        assertTrue(showUpdatePrompt(updater.state.value.status, true, updater.state.value.snoozed), "A newer build asks again")
    }

    @Test fun aRequiredUpdateAsksAgainOnEveryReturn() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301, minimum = 1301))
        val updater = updater(platform)
        updater.foreground(false); advanceUntilIdle()
        assertTrue(updater.state.value.required)
        updater.later()
        assertFalse(showUpdatePrompt(updater.state.value.status, true, updater.state.value.snoozed))
        updater.foreground(false)
        assertTrue(showUpdatePrompt(updater.state.value.status, true, updater.state.value.snoozed))
    }

    @Test fun installHandsTheVerifiedBuildToAndroid() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        val updater = updater(platform)
        updater.foreground(false); advanceUntilIdle()
        updater.install(); advanceUntilIdle()
        assertEquals(listOf(1301L), platform.installs.map { it.versionCode })
        assertIs<UpdateStatus.Installing>(updater.state.value.status)
        platform.installResult!!(InstallResult.Cancelled)
        assertIs<UpdateStatus.Ready>(updater.state.value.status)
        assertFalse(showUpdatePrompt(updater.state.value.status, true, updater.state.value.snoozed), "Backing out acts as Later")
        updater.install(); advanceUntilIdle(); platform.installResult!!(InstallResult.Failed)
        assertIs<UpdateStatus.Failed>(updater.state.value.status)
    }

    @Test fun withoutTheInstallPermissionTheAppExplainsAndContinuesAfterTheViewerAllowsIt() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        platform.installAllowed = false
        val updater = updater(platform)
        updater.foreground(false); advanceUntilIdle()
        updater.install()
        assertEquals(true, assertIs<UpdateStatus.NeedsPermission>(updater.state.value.status).settingsScreen)
        assertTrue(platform.installs.isEmpty())
        assertTrue(updater.openPermissionSettings())
        updater.foreground(false)
        assertIs<UpdateStatus.NeedsPermission>(updater.state.value.status, "Came back without allowing it")
        platform.installAllowed = true
        updater.foreground(false); advanceUntilIdle()
        assertEquals(1, platform.installs.size, "Came back with it allowed: the install continues")
    }

    @Test fun aFailedQuietCheckKeepsAReadyDownload() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        val updater = updater(platform)
        updater.foreground(false); advanceUntilIdle()
        platform.fetchFails = true
        updater.checkNow(); advanceUntilIdle()
        assertIs<UpdateStatus.Ready>(updater.state.value.status)
    }

    @Test fun aFailedCheckDoesNotStartTheHourlyLimit() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        platform.fetchFails = true
        val updater = updater(platform)
        updater.foreground(false); advanceUntilIdle()
        assertNull(platform.lastCheckMillis)
        platform.fetchFails = false; platform.now += 60_000
        updater.foreground(false); advanceUntilIdle()
        assertEquals(2, platform.fetched.size, "Back online a minute later: checks again")
        assertIs<UpdateStatus.Ready>(updater.state.value.status)
        assertEquals(platform.now, platform.lastCheckMillis)
    }

    @Test fun aFailedCheckIsReported() = runTest {
        val platform = FakeUpdatePlatform("{}")
        val updater = updater(platform)
        updater.checkNow(); advanceUntilIdle()
        assertEquals("The server's update information is invalid.", assertIs<UpdateStatus.Failed>(updater.state.value.status).message)
        platform.fetchFails = true
        updater.checkNow(); advanceUntilIdle()
        assertIs<UpdateStatus.Failed>(updater.state.value.status)
    }

    @Test fun localModeHasNoFeed() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        val updater = updater(platform, origin = null)
        updater.foreground(false); advanceUntilIdle()
        assertEquals(UpdateStatus.NoServer, updater.state.value.status)
        assertTrue(platform.fetched.isEmpty())
    }

    @Test fun anAlreadyVerifiedDownloadIsReused() = runTest {
        val platform = FakeUpdatePlatform(feedJson(1301))
        platform.verified += 1301
        val updater = updater(platform)
        updater.foreground(false); advanceUntilIdle()
        assertIs<UpdateStatus.Ready>(updater.state.value.status)
        assertEquals(0, platform.downloads)
    }
}
