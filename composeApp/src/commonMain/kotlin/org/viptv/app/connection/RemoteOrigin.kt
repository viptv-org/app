package org.viptv.app.connection

import io.ktor.http.*

internal const val SERVER_ADDRESS_ERROR = "Enter an HTTPS server address, without a page path."

internal fun validatedRemoteOrigin(raw: String): String {
    val origin = raw.trim().trimEnd('/')
    val url = try { Url(origin) } catch (_: Exception) { throw IllegalArgumentException(SERVER_ADDRESS_ERROR) }
    val host = url.host
    val brackets = '[' in host || ']' in host
    val validBrackets = !brackets || (host.startsWith('[') && host.endsWith(']') && ':' in host &&
        host.count { it == '[' } == 1 && host.count { it == ']' } == 1)
    require(url.protocol == URLProtocol.HTTPS && url.user == null && url.password == null &&
        url.encodedPath in listOf("", "/") && url.parameters.isEmpty() && url.fragment.isEmpty() &&
        '?' !in origin && '#' !in origin && host.isNotBlank() && validBrackets) {
        SERVER_ADDRESS_ERROR
    }
    return origin
}
