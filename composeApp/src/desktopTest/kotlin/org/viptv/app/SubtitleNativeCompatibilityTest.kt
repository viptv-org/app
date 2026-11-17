package org.viptv.app

import kotlinx.serialization.json.*
import org.viptv.app.player.parseSubtitlePcmResult
import org.viptv.app.player.subtitlePcmBackendAvailable
import uniffi.viptv_core.SubtitlePcmSyncBridge
import uniffi.viptv_core.subtitlePcmCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleNativeCompatibilityTest {
    @Test fun nativeBridgeHonorsPatchedCapabilityAndFencesOldEpochs() {
        val capability = subtitlePcmCapabilities()
        val available = Json.parseToJsonElement(capability).jsonObject.getValue("available").jsonPrimitive.boolean
        assertEquals(available, subtitlePcmBackendAvailable(capability))
        val bridge = SubtitlePcmSyncBridge(11uL, "[]")
        try {
            val stale = parseSubtitlePcmResult(bridge.pushPcm16(10uL, 0, 48_000u, 2u, "pcmS16Le", ByteArray(3840)))
            assertEquals("staleEpoch", stale.status)
            assertEquals(0L, stale.processedFrames)
            val current = parseSubtitlePcmResult(bridge.pushPcm16(11uL, 0, 48_000u, 2u, "pcmS16Le", ByteArray(3840)))
            assertEquals(if (available) "ok" else "unavailable", current.status)
            assertEquals(if (available) 1L else 0L, current.processedFrames)
            val unsupported = parseSubtitlePcmResult(bridge.pushPcm16(11uL, 20_000, 48_000u, 2u, "compressed", ByteArray(3840)))
            assertEquals(if (available) "unsupportedFormat" else "unavailable", unsupported.status)
        } finally { bridge.destroy() }
    }
}
