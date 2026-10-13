package org.viptv.app.connection

import io.ktor.client.request.*
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.CatalogPage
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.Card

data class Profile(val id: String, val name: String, val avatar: String?, val locked: Boolean = false, val avatarId: String? = null, val canDelete: Boolean = true, val lockKind: String? = null)
data class Bootstrap(val profiles: List<Profile>, val selected: String?, val home: HomeSnapshot?, val addons: List<org.viptv.engine.wire.IntegrationConnection> = emptyList(), val enabledAddonIds: Set<String> = emptySet(), val profileManagement: ProfileManagement? = null, val plugins: List<org.viptv.engine.wire.PluginDescriptor> = emptyList(), val library: List<Card>? = null, val preferences: PreferencesSnapshot? = null)
class RemoteFailure(val status: Int, message: String) : Exception(message)

class RemoteConnection(
    rawOrigin: String,
    private val sessionStore: RemoteSessionStore = remoteSessionStore(),
    private val client: HttpClient = platformClient(),
    browserMode: Boolean = browserOrigin() != null,
) {
    private val codec = Json { ignoreUnknownKeys = true }
    private val browser = browserMode
    private val origin = Url(rawOrigin).let {
        require(it.protocol == URLProtocol.HTTPS && it.user == null && it.password == null && it.encodedPath in listOf("", "/") && it.parameters.isEmpty() && it.fragment.isEmpty() && it.host.isNotBlank()) { "Enter an HTTPS server address." }
        rawOrigin.trimEnd('/')
    }
    private var csrf: String? = null
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private val refreshGate = Mutex()
    private var refreshVersion = 0L
    var persistenceWarning: String? = null
        private set

    suspend fun login(username: String, password: String): Bootstrap {
        val result = requestJson(if (browser) "/api/auth/login" else "/api/auth/device/login", HttpMethod.Post,
            buildJsonObject { put("username", username.trim()); put("password", password); put("device_name", "VIPTV KMP") })
        if (!browser) installDeviceTokens(result.jsonObject)
        csrf = result.jsonObject["csrf_token"]?.jsonPrimitive?.contentOrNull
        return bootstrap()
    }

    suspend fun restore(saved: SavedRemoteSession): Bootstrap {
        require(!browser && saved.origin == origin)
        refreshToken = saved.refreshToken
        refreshDevice()
        return bootstrap()
    }

    suspend fun restoreBrowser(): Bootstrap? {
        if (!browser) return null
        val status = requestJson("/api/auth/status").jsonObject
        if (status["authenticated"]?.jsonPrimitive?.booleanOrNull != true) return null
        csrf = status["csrf_token"]?.jsonPrimitive?.contentOrNull
        return bootstrap()
    }

    suspend fun bootstrap(): Bootstrap {
        val value = call("/api/v3/bootstrap").jsonObject
        val identity = value.getValue("identity").jsonObject
        csrf = identity["csrf_token"]?.jsonPrimitive?.contentOrNull ?: csrf
        val profiles = identity["profiles"]?.jsonArray.orEmpty().map {
            val p = it.jsonObject
            Profile(p.getValue("id").jsonPrimitive.content, p.getValue("name").jsonPrimitive.content, p["avatar"]?.jsonPrimitive?.contentOrNull,
                p["locked"]?.jsonPrimitive?.booleanOrNull ?: false, p["avatar_id"]?.jsonPrimitive?.contentOrNull)
        }
        val management = value["profile_management"]?.takeUnless { it is JsonNull }?.let { codec.decodeFromJsonElement<ProfileManagement>(it) }
        return Bootstrap(management?.cards() ?: profiles, identity["profile_id"]?.jsonPrimitive?.contentOrNull,
            value["home"]?.takeUnless { it is JsonNull }?.let { codec.decodeFromJsonElement<HomeSnapshot>(it) }, profileManagement = management, plugins = value["integrations"]?.let { codec.decodeFromJsonElement(it) } ?: emptyList(),
            library = value["library"]?.takeUnless { it is JsonNull }?.let { codec.decodeFromJsonElement<CatalogPage>(it).takeIf { page -> page.complete }?.cards },
            preferences = value["preferences"]?.takeUnless { it is JsonNull }?.let { codec.decodeFromJsonElement(it) })
    }

    suspend fun selectProfile(id: String): Bootstrap {
        call("/api/auth/profile", HttpMethod.Post, buildJsonObject { put("profile_id", id) })
        return bootstrap()
    }

    suspend fun search(term: String): CatalogPage = codec.decodeFromJsonElement(call("/api/v3/discover?type=all&search=${term.encodeURLParameter()}"))
    suspend fun detail(card: Card, offset: Int = 0): DetailSnapshot = (card.parent_identity ?: card.identity).let { detail(it.kind, it.id, offset) }
    suspend fun detail(kind: String, id: String, offset: Int = 0): DetailSnapshot {
        require(offset in 0..10_000)
        val page = if (offset == 0) "" else "?offset=$offset"
        return codec.decodeFromJsonElement(call("/api/v3/meta/${kind.encodeURLPathPart()}/${id.encodeURLPathPart()}$page"))
    }

    suspend fun subtitles(card: Card): org.viptv.engine.wire.SubtitleSnapshot = codec.decodeFromJsonElement(
        call("/api/v3/subtitles/${card.identity.kind.encodeURLPathPart()}/${card.identity.id.encodeURLPathPart()}"))

    suspend fun subtitleContent(id: String): String = authenticatedResponse(
        "/api/v3/subtitle-content/${id.encodeURLPathPart()}").let { response ->
        checkStatus(response)
        response.bodyAsText()
    }

    suspend fun call(path: String, method: HttpMethod = HttpMethod.Get, body: JsonElement? = null): JsonElement {
        return decode(authenticatedResponse(path, method, body))
    }

    private suspend fun authenticatedResponse(path: String, method: HttpMethod = HttpMethod.Get, body: JsonElement? = null): HttpResponse {
        require(path.startsWith("/api/") && !path.contains('\\'))
        val version = refreshVersion
        var response = send(path, method, body, accessToken, csrf)
        if (response.status == HttpStatusCode.Unauthorized && path !in setOf("/api/auth/login", "/api/auth/device/login", "/api/auth/logout")) {
            response.bodyAsText()
            refreshGate.withLock {
                if (refreshVersion == version) {
                    if (browser) refreshBrowser() else refreshDevice()
                    refreshVersion++
                }
            }
            response = send(path, method, body, accessToken, csrf)
        }
        return response
    }

    private suspend fun send(path: String, method: HttpMethod, body: JsonElement?, access: String?, csrfValue: String?): HttpResponse = client.request(origin + path) {
            this.method = method
            contentType(ContentType.Application.Json)
            header("x-viptv-image-delivery", if (browser) "proxy" else "direct")
            if (!browser) header(HttpHeaders.Origin, origin)
            access?.let { bearerAuth(it) }
            csrfValue?.let { header("x-csrf-token", it) }
            body?.let { setBody(it.toString()) }
        }

    private suspend fun requestJson(path: String, method: HttpMethod = HttpMethod.Get, body: JsonElement? = null): JsonElement =
        decode(send(path, method, body, accessToken, csrf))

    private suspend fun decode(response: HttpResponse): JsonElement {
        checkStatus(response)
        return codec.parseToJsonElement(response.bodyAsText()).resolveRemoteImages(origin)
    }

    private fun checkStatus(response: HttpResponse) {
        if (response.status.value !in 200..299) {
            throw RemoteFailure(response.status.value, when (response.status.value) {
                401 -> "Sign in to continue."
                403 -> "This action is not available for this profile."
                429 -> "Too many requests. Please wait and try again."
                else -> "The server could not complete this request."
            })
        }
    }

    private suspend fun installDeviceTokens(value: JsonObject) {
        val nextAccess = value["access_token"]?.jsonPrimitive?.contentOrNull ?: throw RemoteFailure(502, "The server did not provide a device session.")
        val nextRefresh = value["refresh_token"]?.jsonPrimitive?.contentOrNull ?: throw RemoteFailure(502, "The server did not provide a refresh token.")
        try {
            sessionStore.save(SavedRemoteSession(origin, nextRefresh))
            persistenceWarning = null
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            persistenceWarning = "Secure storage is unavailable. This device will need to sign in again after the app closes."
        }
        accessToken = nextAccess
        refreshToken = nextRefresh
    }

    private suspend fun refreshDevice() {
        val token = refreshToken ?: throw RemoteFailure(401, "Sign in to continue.")
        val response = send("/api/auth/device/refresh", HttpMethod.Post,
            buildJsonObject { put("refresh_token", token) }, null, null)
        if (response.status == HttpStatusCode.Unauthorized) {
            accessToken = null; refreshToken = null
            try { sessionStore.clear() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* The expired token remains unusable on the server. */ }
        }
        installDeviceTokens(decode(response).jsonObject)
    }

    private suspend fun refreshBrowser() {
        val status = requestJson("/api/auth/status").jsonObject
        if (status["authenticated"]?.jsonPrimitive?.booleanOrNull != true) throw RemoteFailure(401, "Sign in to continue.")
        csrf = status["csrf_token"]?.jsonPrimitive?.contentOrNull
        val result = requestJson("/api/auth/refresh", HttpMethod.Post, buildJsonObject {})
        csrf = result.jsonObject["csrf_token"]?.jsonPrimitive?.contentOrNull ?: csrf
    }

    suspend fun signOut() {
        try {
            refreshGate.withLock {
                if (browser) refreshBrowser() else if (refreshToken != null) refreshDevice()
                refreshVersion++
            }
            call("/api/auth/logout", HttpMethod.Post, buildJsonObject {})
        } catch (error: RemoteFailure) {
            if (error.status != 401) throw error
        }
        try { sessionStore.clear() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* Server revocation has already completed. */ }
        accessToken = null; refreshToken = null; csrf = null
    }

    fun close() { client.close(); accessToken = null; refreshToken = null; csrf = null }
}
