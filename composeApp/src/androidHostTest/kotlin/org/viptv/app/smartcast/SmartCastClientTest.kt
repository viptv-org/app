package org.viptv.app.smartcast

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.viptv.engine.wire.VizioControllerOutput
import org.viptv.engine.wire.VizioControllerOutputKind
import org.viptv.engine.wire.VizioFailure
import org.viptv.engine.wire.VizioFailureKind
import org.viptv.engine.wire.VizioHttpMethod
import org.viptv.engine.wire.VizioRequest

class SmartCastClientTest {
    private val origin = "https://192.0.2.10:7345"
    private val powerModeUrl = "$origin/state/device/power_mode"

    private class FakeDriver(responses: List<VizioControllerOutput>) : SmartCastDriver {
        private val queue = ArrayDeque(responses)
        val started = mutableListOf<Pair<String, String>>()
        val resolved = mutableListOf<Triple<UInt, Int, String>>()
        val rejected = mutableListOf<UInt>()
        var credential: String? = null

        override fun start(operation: String, input: String): VizioControllerOutput {
            started += operation to input
            return queue.removeFirst()
        }

        override fun resolve(requestId: UInt, status: Int, body: String): VizioControllerOutput {
            resolved += Triple(requestId, status, body)
            return queue.removeFirst()
        }

        override fun reject(requestId: UInt): VizioControllerOutput {
            rejected += requestId
            return queue.removeFirst()
        }

        override fun cancel() = Unit
        override fun credential(): String? = credential
        override fun clearCredential() { credential = null }
        override fun close() = Unit
    }

    private class FakeTokens : SmartCastCredentialStore {
        val saved = mutableMapOf<String, String>()
        override fun load(tvOrigin: String): String? = saved[tvOrigin]
        override fun save(tvOrigin: String, token: String) { saved[tvOrigin] = token }
        override fun clear(tvOrigin: String) { saved.remove(tvOrigin) }
    }

    private fun request(
        url: String = powerModeUrl,
        method: VizioHttpMethod = VizioHttpMethod.GET,
    ) = VizioRequest(method, url, emptyMap(), null, 1_000, 65_536)

    private fun complete(result: Map<String, kotlinx.serialization.json.JsonElement>? = null, credentialChanged: Boolean = false) =
        VizioControllerOutput(VizioControllerOutputKind.COMPLETE, null, null, result, null, credentialChanged)

    private fun emitted(id: Long = 1) =
        VizioControllerOutput(VizioControllerOutputKind.REQUEST, id, request(), null, null, false)

    private fun failed() = VizioControllerOutput(
        VizioControllerOutputKind.ERROR, null, null, null,
        VizioFailure(VizioFailureKind.INVALIDINPUT, "failed", false, null), false,
    )

    @Test fun resolvesEachEmittedRequestBeforeCompleting() = runTest {
        val driver = FakeDriver(listOf(emitted(), complete()))
        val client = SmartCastClient(origin, { response -> assertRequest(response); SmartCastHttpResponse(200, "ok") }, driver, FakeTokens())
        val result = client.run("pingAuth")
        assertEquals(VizioControllerOutputKind.COMPLETE, result.kind)
        assertEquals(listOf("pingAuth" to "{}"), driver.started)
        assertEquals(listOf(Triple(1u, 200, "ok")), driver.resolved)
        assertEquals(emptyList<UInt>(), driver.rejected)
    }

    @Test fun aTransportFailureTriggersRejectInsteadOfResolve() = runTest {
        val driver = FakeDriver(listOf(emitted(), failed()))
        val client = SmartCastClient(origin, { throw RuntimeException("unreachable") }, driver, FakeTokens())
        val result = client.run("pingAuth")
        assertEquals(VizioControllerOutputKind.ERROR, result.kind)
        assertEquals(listOf(1u), driver.rejected)
        assertEquals(emptyList<Triple<UInt, Int, String>>(), driver.resolved)
    }

    @Test fun aChangedCredentialIsPersistedFromTheBridge() = runTest {
        val driver = FakeDriver(listOf(complete(credentialChanged = true)))
        driver.credential = "new-token"
        val tokens = FakeTokens()
        val client = SmartCastClient(origin, { SmartCastHttpResponse(200, "ok") }, driver, tokens)
        client.run("finishPair")
        assertEquals("new-token", tokens.saved[origin])
    }

    @Test fun forgetClearsTheCredentialAndStoredToken() = runTest {
        val driver = FakeDriver(emptyList())
        driver.credential = "old-token"
        val tokens = FakeTokens().apply { saved[origin] = "stored" }
        val client = SmartCastClient(origin, { SmartCastHttpResponse(200, "ok") }, driver, tokens)
        client.forget()
        assertNull(driver.credential)
        assertNull(tokens.saved[origin])
    }

    private fun assertRequest(response: VizioRequest) {
        assertEquals(VizioHttpMethod.GET, response.method)
        assertEquals(powerModeUrl, response.url)
    }
}