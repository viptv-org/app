package org.viptv.app.smartcast

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.viptv.engine.wire.VizioDiscoveryCandidate
import org.viptv.engine.wire.VizioFailure
import org.viptv.engine.wire.VizioFailureKind
import org.viptv.engine.wire.VizioPlatformSupport
import org.viptv.engine.wire.VizioTransportSupport

class SmartCastDiscoveryTest {
    private class FakeNative(candidates: List<VizioDiscoveryCandidate>) : SmartCastNative {
        private val results = candidates
        override fun discoveryCandidates(subnet: String) = VizioCandidatesResult.Ok(results)
        override fun deviceinfoName(body: String) = "Vizio TV"
        override fun platformSupport(platform: String) = VizioPlatformSupport(true, VizioTransportSupport.NATIVE, "ok")
        override fun planRequest(operation: String, input: String) =
            VizioRequestResult.Err(VizioFailure(VizioFailureKind.INVALIDINPUT, "no", false, null))
        override fun parseResponse(status: Int, body: String, allowStatusless: Boolean) =
            VizioResponseResult.Err(VizioFailure(VizioFailureKind.INVALIDINPUT, "no", false, null))
    }

    private fun candidate(host: String, port: Int) = VizioDiscoveryCandidate("$host:$port", port)

    @Test fun deduplicatesAHostAcrossItsTwoPorts() = runTest {
        val probed = mutableListOf<String>()
        val probe: suspend (String) -> VizioTv? = { origin -> probed += origin; VizioTv("TV", origin) }
        val discovery = SmartCastDiscovery(
            FakeNative(listOf(candidate("200.0.0.1", 7345), candidate("200.0.0.1", 9000), candidate("200.0.0.2", 7345))),
            probe,
        )
        val results = discovery.search("200.0.0")
        assertEquals(setOf("https://200.0.0.1:7345", "https://200.0.0.2:7345"), results.map { it.origin }.toSet())
        assertEquals(3, probed.size)
    }

    @Test fun anUnreachableHostNeverAbortsTheScan() = runTest {
        val probe: suspend (String) -> VizioTv? = { origin -> if (origin.contains("200.0.0.1")) null else VizioTv("TV", origin) }
        val discovery = SmartCastDiscovery(
            FakeNative(listOf(candidate("200.0.0.1", 7345), candidate("200.0.0.2", 7345))),
            probe,
        )
        val results = discovery.search("200.0.0")
        assertEquals(listOf("https://200.0.0.2:7345"), results.map { it.origin })
    }

    @Test fun publishesProgressivelyAndRespectsTheResultLimit() = runTest {
        val collected = mutableListOf<String>()
        val candidates = (1..5).map { candidate("200.0.0.$it", 7345) }
        val discovery = SmartCastDiscovery(
            FakeNative(candidates),
            { origin -> VizioTv("TV", origin) },
        )
        val results = discovery.search("200.0.0", resultLimit = 2) { collected += it.origin }
        assertEquals(2, results.size)
        assertEquals(results.map { it.origin }, collected)
    }

    @Test fun probeAcceptsBareAddressesAndCanonicalizesTheOrigin() = runTest {
        val probed = mutableListOf<String>()
        val discovery = SmartCastDiscovery(FakeNative(emptyList()), { origin -> probed += origin; null })
        discovery.probe("200.0.0.9")
        assertEquals(listOf("https://200.0.0.9:7345"), probed)
    }
}