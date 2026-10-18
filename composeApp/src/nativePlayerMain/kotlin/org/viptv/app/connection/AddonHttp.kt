package org.viptv.app.connection

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.net.URI

private const val MAX_ADDON_RESPONSE = 2 * 1024 * 1024

private sealed interface AddonReply {
    data class Moved(val location: String) : AddonReply
    data class Content(val body: ByteArray) : AddonReply
}

internal suspend fun addonJson(client: HttpClient, url: String): String = addonBytes(client, url).decodeToString()

/**
 * Add-on URLs legitimately carry configuration in the path (torrentio joins
 * provider and debrid settings with `|`), which RFC 3986 forbids raw and
 * [java.net.URI] rejects with a URISyntaxException. Percent-encode those
 * characters instead of failing the request with an unrelated device error.
 * A `%` that does not start a valid escape is encoded too, so the request
 * target stays a well-formed URI while the engine keeps the raw spelling.
 */
private fun addonUri(url: String): URI {
    val encoded = buildString {
        var index = 0
        while (index < url.length) {
            val ch = url[index]
            when {
                ch == '%' && index + 2 < url.length && url[index + 1].isHex() && url[index + 2].isHex() -> { append(url, index, index + 3); index += 3 }
                ch.isLetterOrDigit() || "-._~!$&'()*+,;=:@/?#".indexOf(ch) >= 0 -> { append(ch); index++ }
                else -> { append(String.format("%%%02X", ch.code.toByte().toInt() and 0xFF)); index++ }
            }
        }
    }
    return try { URI(encoded) } catch (error: java.net.URISyntaxException) { throw LocalFailure("This add-on address is not valid.") }
}

private fun Char.isHex(): Boolean = isDigit() || this in 'a'..'f' || this in 'A'..'F'

internal suspend fun addonBytes(client: HttpClient, url: String): ByteArray {
    var current = addonUri(url)
    val started = System.nanoTime()
    val kind = when { current.path.contains("/catalog/") -> "catalog"; current.path.contains("/meta/") -> "metadata"; current.path.contains("/manifest") -> "manifest"; else -> "addon" }
    repeat(4) { redirects ->
        val reply: AddonReply = client.prepareGet(current.toString()) {
            header(HttpHeaders.UserAgent, "VIPTV-Experiment/0.1")
        }.execute { response ->
            if (response.status.value in 300..399) {
                return@execute AddonReply.Moved(response.headers[HttpHeaders.Location]
                    ?: throw LocalFailure("This add-on sent an invalid redirect."))
            }
            try {
                val body = readContent(response)
                addonFetchFact(kind, response.status.value, started, body.size)
                AddonReply.Content(body)
            } catch (error: Exception) {
                addonFetchFact(kind, response.status.value, started, error = error::class.simpleName)
                throw error
            }
        }
        when (reply) {
            is AddonReply.Content -> return reply.body
            is AddonReply.Moved -> {
                if (redirects == 3) throw LocalFailure("This add-on redirected too many times.")
                val next = try { current.resolve(reply.location) } catch (_: Exception) { throw LocalFailure("This add-on sent an invalid redirect.") }
                if (next.toString().length > 4096 || next.host.isNullOrEmpty() || next.userInfo != null || next.fragment != null || next.scheme !in listOf("http", "https") || (current.scheme == "https" && next.scheme != "https")) {
                    throw LocalFailure("This add-on sent an invalid redirect.")
                }
                current = next
            }
        }
    }
    throw LocalFailure("This add-on redirected too many times.")
}

private suspend fun readContent(response: HttpResponse): ByteArray {
    if (response.status != HttpStatusCode.OK || response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { it > MAX_ADDON_RESPONSE } == true) {
        throw LocalFailure("This add-on could not be loaded. Check its address and try again.")
    }
    val output = ByteArrayOutputStream()
    val channel: ByteReadChannel = response.body()
    val buffer = ByteArray(16_384)
    while (true) {
        val size = channel.readAvailable(buffer)
        if (size < 0) break
        if (size == 0) continue
        if (output.size() + size > MAX_ADDON_RESPONSE) throw LocalFailure("This add-on returned too much data.")
        output.write(buffer, 0, size)
    }
    return output.toByteArray()
}

