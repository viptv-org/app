package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.json.*

data class SimklLinkStatus(val configured: Boolean, val linked: Boolean)
internal suspend fun RemoteConnection.simklStatus(profile: String): SimklLinkStatus {
    val value = call("/api/profiles/${profile.encodeURLPathPart()}/simkl").jsonObject
    return SimklLinkStatus(value.getValue("configured").jsonPrimitive.boolean, value.getValue("linked").jsonPrimitive.boolean)
}
internal suspend fun RemoteConnection.authorizeSimkl(profile: String): String {
    val value = call("/api/profiles/${profile.encodeURLPathPart()}/simkl/authorize", HttpMethod.Post, buildJsonObject {}).jsonObject
    val url = value.getValue("authorization_url").jsonPrimitive.content
    val parsed = Url(url)
    require(parsed.protocol == URLProtocol.HTTPS && parsed.host == "simkl.com" && parsed.encodedPath == "/oauth2/authorize")
    return url
}
internal suspend fun RemoteConnection.unlinkSimkl(profile: String) {
    call("/api/profiles/${profile.encodeURLPathPart()}/simkl", HttpMethod.Delete)
}
internal expect fun openAccountAuthorization(url: String)
