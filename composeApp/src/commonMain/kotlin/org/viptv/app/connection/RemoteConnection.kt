package org.viptv.app.connection

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.CatalogPage
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.Card

data class Profile(val id: String, val name: String, val avatar: String?)
data class Bootstrap(val profiles: List<Profile>, val selected: String?, val home: HomeSnapshot?, val addons: List<org.viptv.engine.wire.IntegrationConnection> = emptyList(), val enabledAddonIds: Set<String> = emptySet())
class RemoteFailure(val status: Int, message: String) : Exception(message)

class RemoteConnection(rawOrigin: String) {
    private val client = platformClient()
    private val codec = Json { ignoreUnknownKeys = true }
    private val origin = Url(rawOrigin).let {
        require(it.protocol == URLProtocol.HTTPS && it.user == null && it.password == null && it.encodedPath in listOf("", "/") && it.parameters.isEmpty() && it.fragment.isEmpty() && it.host.isNotBlank()) { "Enter an HTTPS server address." }
        rawOrigin.trimEnd('/')
    }
    private var csrf: String? = null
    private var accessToken: String? = null

    suspend fun login(username: String, password: String): Bootstrap {
        val native = browserOrigin() == null
        val result = call(if (native) "/api/auth/device/login" else "/api/auth/login", HttpMethod.Post,
            buildJsonObject { put("username", username.trim()); put("password", password); put("device_name", "VIPTV KMP") })
        accessToken = result.jsonObject["access_token"]?.jsonPrimitive?.contentOrNull
        csrf = result.jsonObject["csrf_token"]?.jsonPrimitive?.contentOrNull
        return bootstrap()
    }

    suspend fun bootstrap(): Bootstrap {
        val value = call("/api/v3/bootstrap").jsonObject
        val identity = value.getValue("identity").jsonObject
        csrf = identity["csrf_token"]?.jsonPrimitive?.contentOrNull ?: csrf
        val profiles = identity["profiles"]?.jsonArray.orEmpty().map {
            val p = it.jsonObject
            Profile(p.getValue("id").jsonPrimitive.content, p.getValue("name").jsonPrimitive.content, p["avatar"]?.jsonPrimitive?.contentOrNull)
        }
        return Bootstrap(profiles, identity["profile_id"]?.jsonPrimitive?.contentOrNull,
            value["home"]?.takeUnless { it is JsonNull }?.let { codec.decodeFromJsonElement<HomeSnapshot>(it) })
    }

    suspend fun selectProfile(id: String): Bootstrap {
        call("/api/auth/profile", HttpMethod.Post, buildJsonObject { put("profile_id", id) })
        return bootstrap()
    }

    suspend fun search(term: String): CatalogPage = codec.decodeFromJsonElement(call("/api/v3/discover?type=movie&search=${term.encodeURLParameter()}"))
    suspend fun detail(card: Card): DetailSnapshot = codec.decodeFromJsonElement(call("/api/v3/meta/${card.identity.kind.encodeURLPathPart()}/${card.identity.id.encodeURLPathPart()}"))

    suspend fun call(path: String, method: HttpMethod = HttpMethod.Get, body: JsonElement? = null): JsonElement {
        require(path.startsWith("/api/") && !path.contains('\\'))
        val response = client.request(origin + path) {
            this.method = method
            contentType(ContentType.Application.Json)
            if (browserOrigin() == null) header(HttpHeaders.Origin, origin)
            accessToken?.let { bearerAuth(it) }
            csrf?.let { header("x-csrf-token", it) }
            body?.let { setBody(it.toString()) }
        }
        if (response.status.value !in 200..299) {
            throw RemoteFailure(response.status.value, when (response.status.value) {
                401 -> "Sign in to continue."
                403 -> "This action is not available for this profile."
                429 -> "Too many requests. Please wait and try again."
                else -> "The server could not complete this request."
            })
        }
        return codec.parseToJsonElement(response.bodyAsText())
    }

    fun close() { client.close(); accessToken = null; csrf = null }
}
