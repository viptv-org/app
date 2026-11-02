package org.viptv.app.smartcast

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.viptv.engine.wire.VizioHttpMethod
import org.viptv.engine.wire.VizioRequest

class SmartCastTransportTest {
    private val origin = "https://192.0.2.10:7345"

    private fun request(
        url: String,
        method: VizioHttpMethod = VizioHttpMethod.GET,
        timeoutMillis: Long = 1_000,
        maxResponseBytes: Long = 65_536,
    ) = VizioRequest(method, url, emptyMap(), null, timeoutMillis, maxResponseBytes)

    private class Seen(var method: String? = null, var path: String? = null, var auth: String? = null, var body: String? = null)

    @Test fun rejectsARequestOutsideTheSelectedOriginBeforeNetworking() = runBlocking {
        val transport = SmartCastTransport(origin)
        assertTrue(expectError { transport.execute(request("https://192.0.2.11:7345/state/device/deviceinfo")) } is IllegalArgumentException)
    }

    @Test fun rejectsCleartextAndHostileUrls() = runBlocking {
        val transport = SmartCastTransport(origin)
        assertTrue(expectError { transport.execute(request("http://192.0.2.10:7345/ping")) } is IllegalArgumentException)
        assertTrue(expectError { transport.execute(request("https://192.0.2.10:7345/ping?x=1")) } is IllegalArgumentException)
        assertTrue(expectError { transport.execute(request("https://user@192.0.2.10:7345/ping")) } is IllegalArgumentException)
    }

    @Test fun roundTripsMethodHeadersAndBody() = runBlocking {
        val seen = Seen()
        val transport = SmartCastTransport(origin, client(status = 200, body = """{"ok":true}""", seen = seen))
        val response = transport.execute(VizioRequest(
            VizioHttpMethod.PUT,
            "$origin/key_command/",
            mapOf("AUTH" to "secret"),
            buildJsonObject { put("KEYLIST", "x") },
            2_000,
            65_536,
        ))
        assertEquals(200, response.status)
        assertEquals("""{"ok":true}""", response.body)
        assertEquals("PUT", seen.method)
        assertEquals("/key_command/", seen.path)
        assertEquals("secret", seen.auth)
        assertEquals("""{"KEYLIST":"x"}""", seen.body)
    }

    @Test fun rejectsRedirects() = runBlocking {
        val transport = SmartCastTransport(origin, client(status = 302, body = "", headers = mapOf("Location" to "https://evil.example/")))
        assertTrue(expectError { transport.execute(request("$origin/state/device/deviceinfo")) } is IllegalArgumentException)
    }

    @Test fun enforcesTheResponseSizeLimit() = runBlocking {
        val transport = SmartCastTransport(origin, client(status = 200, body = "a".repeat(64)))
        val error = expectError {
            transport.execute(VizioRequest(VizioHttpMethod.GET, "$origin/ping", emptyMap(), null, 2_000, 8))
        }
        assertTrue(error is IllegalArgumentException)
    }

    private fun client(status: Int, body: String, headers: Map<String, String> = emptyMap(), seen: Seen? = null): OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor { chain ->
                val request = chain.request()
                seen?.let {
                    it.method = request.method
                    it.path = request.url.encodedPath
                    it.auth = request.header("AUTH")
                    val buffer = Buffer()
                    request.body?.writeTo(buffer)
                    it.body = buffer.readUtf8()
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message("")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .apply { headers.forEach { (name, value) -> header(name, value) } }
                    .build()
            }
            .build()

    private suspend fun expectError(block: suspend () -> SmartCastHttpResponse): Throwable =
        try {
            block()
            AssertionError("expected the request to fail")
        } catch (error: Throwable) {
            error
        }
}