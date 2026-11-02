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

    private class FakeExchange(
        var observed: String? = null,
        private val respond: (VizioRequest) -> SmartCastHttpResponse,
    ) : SmartCastExchange {
        override val observedFingerprint: String? get() = observed
        override suspend fun execute(request: VizioRequest): SmartCastHttpResponse = respond(request)
    }

    private class FakeTokens : SmartCastCredentialStore {
        val tokens = mutableMapOf<String, String>()
        val pins = mutableMapOf<String, String>()
        override fun loadToken(tvOrigin: String): String? = tokens[tvOrigin]
        override fun loadCertificatePin(tvOrigin: String): String? = pins[tvOrigin]
        override fun saveToken(tvOrigin: String, token: String) { tokens[tvOrigin] = token }
        override fun saveCertificatePin(tvOrigin: String, pin: String) { pins[tvOrigin] = pin }
        override fun clear(tvOrigin: String) { tokens.remove(tvOrigin); pins.remove(tvOrigin) }
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
        val exchange = FakeExchange { response -> assertRequest(response); SmartCastHttpResponse(200, "ok") }
        val client = SmartCastClient(origin, exchange, driver, FakeTokens())
        val result = client.run("pingAuth")
        assertEquals(VizioControllerOutputKind.COMPLETE, result.kind)
        assertEquals(listOf("pingAuth" to "{}"), driver.started)
        assertEquals(listOf(Triple(1u, 200, "ok")), driver.resolved)
        assertEquals(emptyList<UInt>(), driver.rejected)
    }

    @Test fun aTransportFailureTriggersRejectInsteadOfResolve() = runTest {
        val driver = FakeDriver(listOf(emitted(), failed()))
        val exchange = FakeExchange { throw RuntimeException("unreachable") }
        val client = SmartCastClient(origin, exchange, driver, FakeTokens())
        val result = client.run("pingAuth")
        assertEquals(VizioControllerOutputKind.ERROR, result.kind)
        assertEquals(listOf(1u), driver.rejected)
        assertEquals(emptyList<Triple<UInt, Int, String>>(), driver.resolved)
    }

    @Test fun aChangedCredentialAndFingerprintArePersisted() = runTest {
        val driver = FakeDriver(listOf(complete(credentialChanged = true)))
        driver.credential = "new-token"
        val exchange = FakeExchange(observed = "ab12cd34") { SmartCastHttpResponse(200, "ok") }
        val tokens = FakeTokens()
        val client = SmartCastClient(origin, exchange, driver, tokens)
        client.run("finishPair")
        assertEquals("new-token", tokens.tokens[origin])
        assertEquals("ab12cd34", tokens.pins[origin])
    }

    @Test fun forgetClearsTheCredentialPinAndStoredToken() = runTest {
        val driver = FakeDriver(emptyList())
        driver.credential = "old-token"
        val tokens = FakeTokens().apply { tokens[origin] = "stored"; pins[origin] = "pinned" }
        val client = SmartCastClient(origin, FakeExchange { SmartCastHttpResponse(200, "ok") }, driver, tokens)
        client.forget()
        assertNull(driver.credential)
        assertNull(tokens.tokens[origin])
        assertNull(tokens.pins[origin])
    }

    private fun assertRequest(response: VizioRequest) {
        assertEquals(VizioHttpMethod.GET, response.method)
        assertEquals(powerModeUrl, response.url)
    }
}