package org.viptv.app.connection

import io.ktor.http.encodeURLParameter

internal fun deviceApprovalAppIntent(origin: String, code: String): String? {
    val server = deviceApprovalOrigin(origin) ?: return null
    val authority = server.removePrefix("https://")
    if (!Regex("[0-9]{8}").matches(code)) return null
    val target = "$server/device?code=$code"
    return "intent://$authority/device?code=$code#Intent;scheme=https;package=org.viptv.experiment;" +
        "S.browser_fallback_url=${target.encodeURLParameter()};end"
}

internal fun deviceApprovalOrigin(origin: String): String? {
    val server = remoteOriginOrNull(origin) ?: return null
    val authority = Regex("https://([A-Za-z0-9.-]+)(?::([0-9]{1,5}))?").matchEntire(server) ?: return null
    val port = authority.groupValues[2]
    if (port.isNotEmpty() && port.toInt() !in 1..65535) return null
    return server
}
