package org.viptv.app.connection

import io.ktor.http.encodeURLParameter

internal fun deviceApprovalAppIntent(origin: String, code: String): String? {
    val server = remoteOriginOrNull(origin) ?: return null
    val host = server.removePrefix("https://")
    if (!Regex("[A-Za-z0-9.-]+").matches(host) || !Regex("[0-9]{8}").matches(code)) return null
    val target = "$server/device?code=$code"
    return "intent://$host/device?code=$code#Intent;scheme=https;package=org.viptv.experiment;" +
        "S.browser_fallback_url=${target.encodeURLParameter()};end"
}
