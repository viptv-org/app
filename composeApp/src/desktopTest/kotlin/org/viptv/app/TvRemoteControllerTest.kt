package org.viptv.app

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.viptv.app.features.remote.TvRemoteController
import org.viptv.app.features.remote.VizioRemoteEngine
import org.viptv.app.features.remote.remoteSwipeKey
import org.viptv.engine.wire.VizioControllerOutput
import org.viptv.engine.wire.VizioControllerOutputKind
import org.viptv.engine.wire.VizioFailure
import org.viptv.engine.wire.VizioFailureKind
import org.viptv.engine.wire.VizioRemoteKey
import kotlin.test.*

class TvRemoteControllerTest {

    private class FakeEngine : VizioRemoteEngine {
        val operations = mutableListOf<Pair<String, JsonObject>>()
        val outputs = ArrayDeque<VizioControllerOutput>()
        var closedBy = 0

        override suspend fun run(operation: String, input: JsonObject): VizioControllerOutput {
            operations += operation to input
            return if (outputs.isEmpty()) complete() else outputs.removeFirst()
        }

        override fun close() { closedBy++ }
    }

    private val receiverUrl = "https://example.invalid/tv/?platform=vizio"
    private val ping = "pingAuth" to JsonObject(emptyMap())
    private val up = "key" to buildJsonObject { put("key", "UP") }
    private val launchInput = buildJsonObject { put("url", receiverUrl) }

    @Test fun openingPingsAndComingOnlineEnablesKeys() = runTest {
        val engine = FakeEngine()
        val controller = TvRemoteController({ engine }, this, receiverUrl)
        assertFalse(controller.online)
        controller.open(); advanceUntilIdle()
        assertTrue(controller.online)
        assertFalse(controller.checking)
        assertEquals(listOf(ping), engine.operations)
        assertTrue(controller.key(VizioRemoteKey.UP))
        advanceUntilIdle()
        assertEquals(listOf(ping, up), engine.operations)
        assertEquals(0, controller.pendingKeys)
    }

    @Test fun theEighthQueuedKeyIsAcceptedAndTheNinthIsDropped() = runTest {
        val engine = FakeEngine()
        val controller = TvRemoteController({ engine }, this, receiverUrl)
        controller.open(); advanceUntilIdle()
        repeat(8) { assertTrue(controller.key(VizioRemoteKey.UP), "key $it accepted") }
        assertFalse(controller.key(VizioRemoteKey.DOWN), "the ninth tap must be dropped")
        assertEquals(8, controller.pendingKeys)
        advanceUntilIdle()
        assertEquals(0, controller.pendingKeys)
        assertEquals(8, engine.operations.count { it.first == "key" })
    }

    @Test fun closeDiscardsQueuedKeysAndReopeningNeverReplaysThem() = runTest {
        val engine = FakeEngine()
        val controller = TvRemoteController({ engine }, this, receiverUrl)
        controller.open(); advanceUntilIdle()
        assertTrue(controller.key(VizioRemoteKey.UP))
        assertEquals(1, controller.pendingKeys)
        controller.close()
        assertEquals(0, controller.pendingKeys)
        assertFalse(controller.online)
        advanceUntilIdle()
        assertTrue(engine.operations.none { it.first == "key" }, "queued key must be discarded: ${engine.operations}")
        assertEquals(1, engine.closedBy)
        controller.open(); advanceUntilIdle()
        assertTrue(controller.online)
        assertEquals(listOf(ping, ping), engine.operations)
    }

    @Test fun aTransportFailureGoesOfflineAndaSkippedThirdOfTheBoundedQueueIsDropped() = runTest {
        val engine = FakeEngine().apply { outputs += error(VizioFailureKind.TRANSPORT) }
        val controller = TvRemoteController({ engine }, this, receiverUrl)
        controller.open(); advanceUntilIdle()
        assertFalse(controller.online)
        assertFalse(controller.checking)
        assertFalse(controller.key(VizioRemoteKey.UP), "keys must be disabled while offline")
    }

    @Test fun aRejectedCommandIsNotAnOfflineTv() = runTest {
        val engine = FakeEngine()
        val controller = TvRemoteController({ engine }, this, receiverUrl)
        controller.open(); advanceUntilIdle()
        assertTrue(controller.online)
        engine.outputs += error(VizioFailureKind.INVALIDPARAMETER)
        assertTrue(controller.key(VizioRemoteKey.UP))
        advanceUntilIdle()
        assertTrue(controller.online, "a rejected command alone is not an offline TV")
    }

    @Test fun launchOnTvSendsTheReceiverConjure() = runTest {
        val engine = FakeEngine()
        val controller = TvRemoteController({ engine }, this, receiverUrl)
        controller.open(); advanceUntilIdle()
        controller.launchOnTv(); advanceUntilIdle()
        assertTrue(engine.operations.any { it.first == "launchConjure" && it.second == launchInput }, engine.operations.toString())
        assertFalse(controller.launching)
    }

    @Test fun retryRechecksReachabilityAfterAFailure() = runTest {
        val engine = FakeEngine().apply { outputs += error(VizioFailureKind.TRANSPORT); outputs += complete() }
        val controller = TvRemoteController({ engine }, this, receiverUrl)
        controller.open(); advanceUntilIdle()
        assertFalse(controller.online)
        controller.retry(); advanceUntilIdle()
        assertTrue(controller.online)
        assertEquals(listOf(ping, ping), engine.operations)
    }

    @Test fun swipeMapsDistantGesturesToDirectionsAndIgnoresShortOnes() {
        assertEquals(VizioRemoteKey.UP, remoteSwipeKey(0f, -50f, 32f))
        assertEquals(VizioRemoteKey.DOWN, remoteSwipeKey(0f, 50f, 32f))
        assertEquals(VizioRemoteKey.LEFT, remoteSwipeKey(-50f, 0f, 32f))
        assertEquals(VizioRemoteKey.RIGHT, remoteSwipeKey(50f, 0f, 32f))
        assertEquals(VizioRemoteKey.RIGHT, remoteSwipeKey(40f, 30f, 32f))
        assertNull(remoteSwipeKey(10f, 10f, 32f))
    }
}

private fun complete() = VizioControllerOutput(VizioControllerOutputKind.COMPLETE, null, null, null, null, false)

private fun error(kind: VizioFailureKind) = VizioControllerOutput(
    VizioControllerOutputKind.ERROR, null, null, null,
    VizioFailure(kind, "failed", false, null), false,
)