package org.viptv.app.connection

import kotlinx.serialization.json.*

enum class IptvMode { Xtream, M3u }

/** Private form data; provider URL construction and validation belong to the engine. */
data class IptvCredentials(val mode: IptvMode = IptvMode.Xtream, val address: String = "",
    val username: String = "", val password: String = "") {
    val complete: Boolean get() = address.isNotBlank() &&
        (mode == IptvMode.M3u || username.isNotBlank() && password.isNotEmpty())
    val pluginId: String get() = if (mode == IptvMode.M3u) "m3u" else "xtream"
    internal fun configuration() = buildJsonObject {
        if (mode == IptvMode.M3u) put("playlist_url", address.trim())
        else { put("server_url", address.trim()); put("username", username); put("password", password) }
    }
    override fun toString() = "IptvCredentials(mode=$mode, details=<private>)"
}
