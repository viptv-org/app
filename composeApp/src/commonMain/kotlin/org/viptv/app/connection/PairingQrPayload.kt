package org.viptv.app.connection

import io.ktor.http.URLProtocol
import io.ktor.http.Url

internal object PairingQrPayload {
    // A TV paints verification_uri_complete; older builds paint the code image URL.
    private val pairingPaths = setOf("/device", "/api/auth/device/qr")

    fun code(payload: String, origin: String): String? {
        val scanned = runCatching { Url(payload.trim()) }.getOrNull() ?: return null
        val server = runCatching { Url(origin) }.getOrNull() ?: return null
        if (scanned.protocol != URLProtocol.HTTPS || server.protocol != URLProtocol.HTTPS) return null
        if (scanned.user != null || scanned.password != null) return null
        if (!scanned.host.equals(server.host, ignoreCase = true) || scanned.port != server.port) return null
        if (scanned.encodedPath !in pairingPaths) return null
        val code = scanned.parameters["code"]?.trim() ?: return null
        return code.takeIf { it.length == 8 && it.all(Char::isDigit) }
    }
}
