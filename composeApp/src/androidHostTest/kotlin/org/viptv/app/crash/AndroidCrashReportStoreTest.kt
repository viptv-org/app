package org.viptv.app.crash

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidCrashReportStoreTest {
    @Before fun attach() {
        AndroidCrashReports.attach(RuntimeEnvironment.getApplication())
        CrashReportStore.deletePending()
    }
    @After fun clean() { CrashReportStore.deletePending() }

    @Test fun reportRoundTripsThroughAppPrivateStorage() {
        val report = CrashReport(appVersion = "0.1.9+abc", buildCommit = "abc", deviceModel = "Pixel", androidVersion = "14", thread = "main", stack = "boom")
        CrashReportStore.storeReport(report)
        val pending = CrashReportStore.pendingReport()!!
        assertEquals("abc", pending.buildCommit)
        assertEquals("main", pending.thread)
        assertFalse(pending.nativeDuringPlaybackStart)
    }

    @Test fun playbackMarkerWithoutCleanStopBecomesNativeCrashReport() {
        val attempt = PlaybackAttempt(sourceId = "s1", providerLabel = "Torrentio", delivery = "gateway")
        CrashReportStore.writePlaybackMarker(attempt)
        val pending = CrashReportStore.pendingReport()!!
        assertTrue(pending.nativeDuringPlaybackStart)
        assertEquals("s1", pending.playback?.sourceId)
        assertEquals("gateway", pending.playback?.delivery)
    }

    @Test fun cleanStopClearsMarker() {
        CrashReportStore.writePlaybackMarker(PlaybackAttempt(sourceId = "s1"))
        CrashReportStore.clearPlaybackMarker()
        assertNull(CrashReportStore.pendingReport())
    }

    @Test fun deletePendingRemovesReportAndMarker() {
        CrashReportStore.storeReport(CrashReport(appVersion = "v"))
        CrashReportStore.writePlaybackMarker(PlaybackAttempt(sourceId = "s1"))
        CrashReportStore.deletePending()
        assertNull(CrashReportStore.pendingReport())
    }
}