package org.viptv.app.smartcast

import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.viptv.engine.wire.VizioRequest

data class SmartCastHttpResponse(val status: Int, val body: String)

interface SmartCastExchange {
    val observedFingerprint: String?
    suspend fun execute(request: VizioRequest): SmartCastHttpResponse
}

class SmartCastTransport(
    private val tvOrigin: String,
    certificatePin: String? = null,
) : SmartCastExchange {
    private val origin = canonicalOrigin(tvOrigin)
    private val trustManager = SmartCastTrustManager(certificatePin?.lowercase())
    private val client = pinnedClient(origin, trustManager)

    override val observedFingerprint: String?
        get() = trustManager.observedFingerprint()

    override suspend fun execute(request: VizioRequest): SmartCastHttpResponse = withContext(Dispatchers.IO) {
        val uri = URI(request.url)
        require(runCatching { exactOrigin(uri) }.getOrNull() == origin) {
            "SmartCast request is outside the selected TV origin"
        }
        require(request.timeoutMillis in 250..60_000) { "SmartCast request timeout is out of range" }
        require(request.maxResponseBytes in 1..(8L * 1024 * 1024)) { "SmartCast response limit is out of range" }

        val builder = Request.Builder().url(uri.toString())
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        val body = request.body?.let { JsonObject(it).toString() }
        builder.method(request.method.name, body?.toRequestBody(JSON_MEDIA_TYPE))

        val requestClient = client.newBuilder()
            .callTimeout(request.timeoutMillis, TimeUnit.MILLISECONDS)
            .connectTimeout(request.timeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(request.timeoutMillis, TimeUnit.MILLISECONDS)
            .build()

        requestClient.newCall(builder.build()).execute().use { response ->
            require(response.code !in 300..399) { "SmartCast redirects are not permitted" }
            val output = ByteArrayOutputStream()
            response.body.byteStream().use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size().toLong() + count <= request.maxResponseBytes) {
                        "SmartCast response exceeds its size limit"
                    }
                    output.write(buffer, 0, count)
                }
            }
            SmartCastHttpResponse(response.code, output.toString(Charsets.UTF_8.name()))
        }
    }

    private fun exactOrigin(uri: URI): String {
        require(uri.scheme.equals("https", ignoreCase = true))
        require(uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        val port = if (uri.port == -1) 443 else uri.port
        return "https://${uri.host.lowercase()}:$port"
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private fun pinnedClient(origin: String, trustManager: SmartCastTrustManager): OkHttpClient {
            val host = URI(origin).host
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustManager), SecureRandom())
            }
            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                // The pin binds the cert to this exact host, so the host check is scoped, never skipped.
                .hostnameVerifier { presented, _ -> presented.equals(host, ignoreCase = true) }
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build()
        }

        fun canonicalOrigin(input: String): String {
            val uri = URI(if (input.contains("://")) input else "https://$input")
            require(uri.scheme.equals("https", ignoreCase = true) && uri.host != null)
            require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
            require(uri.rawQuery == null && uri.rawFragment == null && uri.rawUserInfo == null)
            val authority = input.substringAfter("://", input).substringBefore('/')
            val explicitPort = if (authority.startsWith("[")) {
                authority.indexOf(']').let { it >= 0 && authority.getOrNull(it + 1) == ':' }
            } else {
                authority.substringAfterLast(':', "").all(Char::isDigit) && authority.contains(':')
            }
            val port = if (explicitPort) uri.port else 7345
            require(port in 1..65535)
            return "https://${uri.host.lowercase()}:$port"
        }
    }
}