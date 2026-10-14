package org.viptv.app.connection

import io.ktor.http.*

internal fun validatedRemoteOrigin(raw: String): String {
    val origin = raw.trim().trimEnd('/')
    val url = try { Url(origin) } catch (_: Exception) { throw IllegalArgumentException("Enter an HTTPS server address.") }
    val host = url.host
    val brackets = '[' in host || ']' in host
    val validBrackets = !brackets || (host.startsWith('[') && host.endsWith(']') && ':' in host &&
        host.count { it == '[' } == 1 && host.count { it == ']' } == 1)
    require(url.protocol == URLProtocol.HTTPS && url.user == null && url.password == null &&
        url.encodedPath in listOf("", "/") && url.parameters.isEmpty() && url.fragment.isEmpty() &&
        '?' !in origin && '#' !in origin && host.isNotBlank() && validBrackets) {
        "Enter an HTTPS server address."
    }
    return origin
}
