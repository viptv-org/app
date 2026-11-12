package org.viptv.app.player

import kotlin.test.*

class SubtitleSyncProtocolTest {
    private fun response(epoch: Long = 4, state: String = "accepted", confidence: String = "uncalibratedHeuristic", status: String = "ok") =
        """{"epoch":$epoch,"status":"$status","processedFrames":3000,"emittedFrames":5,"estimate":{"epoch":4,"algorithmVersion":"activity-offset-affine-v1","confidenceKind":"$confidence","revision":1,"observationRevision":2,"state":"$state","correction":{"anchorUs":28000000,"offsetUs":2000000,"scale":1.0}}}"""

    @Test fun abiCompatibleUnpatched48kRuntimeCannotEnableAnalysis() {
        val capabilities = """{"available":true,"workerOnly":true,"encoding":"pcmS16Le","frameMs":20,"maxInputBytes":32768,"frontendVersion":"pcm16-webrtc20-v2","vadPatch":"48k-input-offset-v1","vadRevision":"e283ca41df3a84b0e87fb1f5cb9b21580a286b09","sampleRates":[8000,16000,32000,48000],"channels":[1,2]}"""
        assertTrue(subtitlePcmBackendAvailable(capabilities))
        assertFalse(subtitlePcmBackendAvailable(capabilities.replace("pcm16-webrtc20-v2", "pcm16-webrtc20-v1")))
        assertFalse(subtitlePcmBackendAvailable(capabilities.replace("48k-input-offset-v1", "unpatched")))
        assertFalse(subtitlePcmBackendAvailable(capabilities.replace("48000", "44100")))
        assertFalse(subtitlePcmBackendAvailable(capabilities.replace("\"available\":true", "\"available\":false")))
        assertFalse(subtitlePcmBackendAvailable("not JSON"))
        assertFalse(subtitlePcmBackendAvailable("x".repeat(4097)))
    }

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
