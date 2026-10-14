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

internal suspend fun addonBytes(client: HttpClient, url: String): ByteArray {
    var current = URI(url)
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

