package org.viptv.app.smartcast

import java.io.IOException
import java.net.URI
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
        timeoutMillis: Long = 2_000,
        maxResponseBytes: Long = 65_536,
    ) = VizioRequest(method, url, emptyMap(), null, timeoutMillis, maxResponseBytes)

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

    @Test fun acceptsASelfSignedTvAndCapturesItsFingerprint() = runBlocking {
        val (key, cert) = load("/smartcast-tv-a.p12")
        FakeVizioTv(key, cert).use { tv ->
            val transport = SmartCastTransport(tv.origin)
            val response = transport.execute(request("${tv.origin}/state/device/deviceinfo"))
            assertEquals(200, response.status)
            assertEquals(tv.fingerprint, transport.observedFingerprint)
        }
    }

    @Test fun aPinnedCertificateIsTrusted() = runBlocking {
        val (key, cert) = load("/smartcast-tv-a.p12")
        FakeVizioTv(key, cert).use { tv ->
            val transport = SmartCastTransport(tv.origin, tv.fingerprint)
            assertEquals(200, transport.execute(request("${tv.origin}/state/device/deviceinfo")).status)
        }
    }

    @Test fun aDifferentCertificateOnTheSameHostIsRejected() = runBlocking {
        val (key, certA) = load("/smartcast-tv-a.p12")
        val (_, certB) = load("/smartcast-tv-b.p12")
        FakeVizioTv(key, certA).use { tv ->
            val transport = SmartCastTransport(tv.origin, certificateFingerprint(certB))
            assertTrue(expectError { transport.execute(request("${tv.origin}/state/device/deviceinfo")) } is IOException)
        }
    }

    @Test fun roundTripsMethodHeadersAndBody() = runBlocking {
        val (key, cert) = load("/smartcast-tv-a.p12")
        FakeVizioTv(key, cert).use { tv ->
            tv.status = 200
            tv.body = """{"ok":true}"""
            val transport = SmartCastTransport(tv.origin, tv.fingerprint)
            val response = transport.execute(VizioRequest(
                VizioHttpMethod.PUT,
                "${tv.origin}/key_command/",
                mapOf("AUTH" to "secret"),
                buildJsonObject { put("KEYLIST", "x") },
                2_000,
                65_536,
            ))
            assertEquals(200, response.status)
            assertEquals("""{"ok":true}""", response.body)
            assertEquals("PUT /key_command/ HTTP/1.1", tv.requestLine)
            assertEquals("secret", tv.requestHeaders["AUTH"])
            assertEquals("""{"KEYLIST":"x"}""", tv.requestBody)
        }
    }

    @Test fun rejectsRedirects() = runBlocking {
        val (key, cert) = load("/smartcast-tv-a.p12")
        FakeVizioTv(key, cert).use { tv ->
            tv.status = 302
            tv.headers = mapOf("Location" to "https://evil.example/")
            val transport = SmartCastTransport(tv.origin, tv.fingerprint)
            assertTrue(expectError { transport.execute(request("${tv.origin}/state/device/deviceinfo")) } is IllegalArgumentException)
        }
    }

    @Test fun enforcesTheResponseSizeLimit() = runBlocking {
        val (key, cert) = load("/smartcast-tv-a.p12")
        FakeVizioTv(key, cert).use { tv ->
            tv.body = "a".repeat(64)
            val transport = SmartCastTransport(tv.origin, tv.fingerprint)
            val error = expectError {
                transport.execute(VizioRequest(VizioHttpMethod.GET, "${tv.origin}/ping", emptyMap(), null, 2_000, 8))
            }
            assertTrue(error is IllegalArgumentException)
        }
    }

    private suspend fun expectError(block: suspend () -> SmartCastHttpResponse): Throwable =
        try {
            block()
            AssertionError("expected the request to fail")
        } catch (error: Throwable) {
            error
        }

    private fun load(resource: String): Pair<PrivateKey, X509Certificate> {
        val keyStore = KeyStore.getInstance("PKCS12")
        SmartCastTransportTest::class.java.getResourceAsStream(resource)!!.use { keyStore.load(it, PASSWORD.toCharArray()) }
        val key = keyStore.getKey("server", PASSWORD.toCharArray()) as PrivateKey
        val cert = keyStore.getCertificate("server") as X509Certificate
        return key to cert
    }

    private class FakeVizioTv(private val key: PrivateKey, private val cert: X509Certificate) : AutoCloseable {
        val fingerprint = certificateFingerprint(cert)
        val origin: String

        @Volatile var requestLine: String? = null
        @Volatile var requestHeaders: Map<String, String> = emptyMap()
        @Volatile var requestBody: String? = null
        @Volatile var status: Int = 200
        @Volatile var body: String = "{}"
        @Volatile var headers: Map<String, String> = emptyMap()

        private val server: SSLServerSocket
        private val thread: Thread

        init {
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry("server", key, PASSWORD.toCharArray(), arrayOf(cert))
            }
            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore, PASSWORD.toCharArray()) }
            val sslContext = SSLContext.getInstance("TLS").apply { init(keyManagerFactory.keyManagers, null, SecureRandom()) }
            server = sslContext.serverSocketFactory.createServerSocket(0) as SSLServerSocket
            origin = "https://localhost:${server.localPort}"
            thread = Thread { serveOnce() }
            thread.start()
        }

        private fun serveOnce() {
            try {
                server.soTimeout = 10_000
                (server.accept() as SSLSocket).use { socket ->
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    val head = StringBuilder()
                    while (!head.endsWith("\r\n\r\n")) {
                        val byte = input.read()
                        if (byte < 0) return
                        head.append(byte.toChar())
                    }
                    val lines = head.toString().split("\r\n")
                    requestLine = lines.firstOrNull()
                    requestHeaders = lines.drop(1).filter { it.contains(':') }.associate { line ->
                        val index = line.indexOf(':')
                        line.substring(0, index).trim() to line.substring(index + 1).trim()
                    }
                    val contentLength = requestHeaders["Content-Length"]?.toIntOrNull() ?: 0
                    val bytes = ByteArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val count = input.read(bytes, read, contentLength - read)
                        if (count < 0) break
                        read += count
                    }
                    requestBody = String(bytes, 0, read, Charsets.UTF_8)
                    val responseHead = buildString {
                        append("HTTP/1.1 $status ")
                        append(if (status == 200) "OK" else "Status")
                        append("\r\n")
                        headers.forEach { (name, value) -> append("$name: $value\r\n") }
                        append("Content-Length: ")
                        append(body.toByteArray().size)
                        append("\r\nConnection: close\r\n\r\n")
                        append(body)
                    }
                    output.write(responseHead.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            } catch (_: Exception) {
                // A client that rejects the certificate aborts the server-side handshake; that is expected.
            }
        }

        override fun close() {
            runCatching { server.close() }
            thread.join(2_000)
        }
    }

    private companion object { const val PASSWORD = "testpass" }
}