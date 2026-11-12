package org.viptv.app.player

import kotlin.test.*

class SubtitleSyncProtocolTest {
    private fun response(epoch: Long = 4, state: String = "accepted", confidence: String = "uncalibratedHeuristic", status: String = "ok") =
        """{"epoch":$epoch,"status":"$status","processedFrames":3000,"emittedFrames":5,"estimate":{"epoch":4,"algorithmVersion":"activity-offset-affine-v1","confidenceKind":"$confidence","revision":1,"observationRevision":2,"state":"$state","correction":{"anchorUs":28000000,"offsetUs":2000000,"scale":1.0}}}"""

    @Test fun typedNativeEstimateCarriesEpochAndBothRevisions() {
        val value = parseSubtitlePcmResult(response())
        assertEquals(3000L, value.processedFrames)
        val estimate = assertNotNull(value.estimate)
        assertEquals(4L, estimate.epoch)
        assertEquals(1L, estimate.revision)
        assertEquals(2L, estimate.observationRevision)
        assertEquals(2_000_000L, estimate.correction?.offsetUs)
    }

    @Test fun nonOkInputCannotPublishRetainedNativeEstimate() {
        assertNull(parseSubtitlePcmResult(response(status = "needsNewEpoch")).estimate)
        assertNull(parseSubtitlePcmResult(response(status = "busy")).estimate)
    }

    @Test fun malformedOrUnknownResultCannotBecomeCorrection() {
        assertFails { parseSubtitlePcmResult(response(epoch = 5)) }
        assertFails { parseSubtitlePcmResult(response(state = "probable")) }
        assertFails { parseSubtitlePcmResult(response(confidence = "probability")) }
        assertFails { parseSubtitlePcmResult("x".repeat(1024 * 1024 + 1)) }
        assertFails { parseSubtitlePcmResult(response().replace("2000000", "16000000")) }
    }

    @Test fun cueBridgeUsesCanonicalMicrosecondsAndBounds() {
        assertEquals("[{\"startUs\":60000000,\"endUs\":61000000}]", subtitleCueJson(listOf(SubtitleCueInterval(60_000_000, 61_000_000))))
        assertFails { subtitleCueJson(List(16_385) { SubtitleCueInterval(0, 1) }) }
    }
}
