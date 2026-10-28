package org.viptv.app.connection

import io.ktor.client.request.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.CatalogPage
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.Card

/**
 * A profile card. [avatarStyle] + 1-based [avatarChoice] are the design catalog pick
 * (`avatarChoice` is null until one is made); [avatarUrl] is the server's image for
 * the profile (a random seed when nothing was picked); [avatarId] is a legacy
 * bundled avatar, sent only while there is no catalog pick.
 */
data class Profile(val id: String, val name: String, val avatar: String?, val locked: Boolean = false, val avatarId: String? = null, val canDelete: Boolean = true, val lockKind: String? = null,
    val avatarStyle: String? = null, val avatarChoice: Int? = null, val avatarUrl: String? = null)
data class Bootstrap(val profiles: List<Profile>, val selected: String?, val home: HomeSnapshot?, val addons: List<org.viptv.engine.wire.IntegrationConnection> = emptyList(), val enabledAddonIds: Set<String> = emptySet(), val profileManagement: ProfileManagement? = null, val plugins: List<org.viptv.engine.wire.PluginDescriptor> = emptyList(), val library: List<Card>? = null, val preferences: PreferencesSnapshot? = null, val libraryMembership: org.viptv.engine.wire.LibraryMembershipSnapshot? = null, val libraryImport: org.viptv.engine.wire.LibraryImportStatus? = null,
    /** Change tokens read before [home] was built; null for local and older servers. */
    val revisions: org.viptv.app.state.Revisions? = null)
class RemoteFailure(val status: Int, message: String) : Exception(message)

private val nativeSessionGate = Mutex()
internal suspend fun <T> withNativeSessionStoreLock(block: suspend () -> T): T = nativeSessionGate.withLock { block() }

class RemoteConnection(
    rawOrigin: String,
    private val sessionStore: RemoteSessionStore = remoteSessionStore(),
    client: HttpClient? = null,
    browserMode: Boolean = browserOrigin() != null,
) {
    private val codec = Json { ignoreUnknownKeys = true }
    private val browser = browserMode
    internal val accountManagementEnabled: Boolean get() = browser
    internal val accountDashboardUrl: String get() = origin + "/"
    internal val origin = validatedRemoteOrigin(rawOrigin)
    private val client = client ?: platformClient()
    private var csrf: String? = null
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private val refreshGate = Mutex()
    private var retired = false
    private var refreshVersion = 0L
    var persistenceWarning: String? = null
        private set
    internal var requiredAudioIndexSupported = false
        private set

    internal fun devicePairing(active: () -> Boolean = { true }) = RemoteDevicePairing(
        send = { path, body -> send(path, HttpMethod.Post, body, null, null) },
        adopt = { value ->
            if (!active()) throw CancellationException("Pairing cancelled")
            authSession { installDeviceTokens(value, persist = false) }
            val result = bootstrap()
            if (!active()) throw CancellationException("Pairing cancelled")
            authSession { persistDeviceToken(requireNotNull(refreshToken)) }
            result
        },
    )

    suspend fun login(username: String, password: String): Bootstrap {
        ensureOpen()
        val result = requestJson(if (browser) "/api/auth/login" else "/api/auth/device/login", HttpMethod.Post,
            buildJsonObject { put("username", username.trim()); put("password", password); put("device_name", deviceDisplayName()) })
        if (!browser) authSession { installDeviceTokens(result.jsonObject) }
        csrf = result.jsonObject["csrf_token"]?.jsonPrimitive?.contentOrNull
        return bootstrap()
    }

    internal suspend fun registerAccount(username: String, name: String, password: String): RecoveryKit {
        check(browser) { "Create accounts in a browser" }
        ensureOpen()
        val response = requestJson("/api/auth/register", HttpMethod.Post, buildJsonObject {
            put("username", username.trim()); put("name", name.trim()); put("password", password)
        }).jsonObject
        csrf = response["csrf_token"]?.jsonPrimitive?.contentOrNull ?: csrf
        return recoveryKit(response, recovered = false)
    }

    internal suspend fun recoverAccount(username: String, recoveryCode: String, password: String): RecoveryKit {
        check(browser) { "Recover accounts in a browser" }
        ensureOpen()
        val response = requestJson("/api/auth/recover", HttpMethod.Post, buildJsonObject {
            put("username", username.trim()); put("recovery_code", recoveryCode.trim()); put("password", password)
        }).jsonObject
        csrf = response["csrf_token"]?.jsonPrimitive?.contentOrNull ?: csrf
        return recoveryKit(response, recovered = true)
    }

    suspend fun restore(saved: SavedRemoteSession): Bootstrap {
        require(!browser && validatedRemoteOrigin(saved.origin) == origin)
        return restoreSession { saved }
    }

    /** A startup snapshot may predate a retiring controller's one-use rotation. */
    internal suspend fun restoreFromStore(): Bootstrap = restoreSession {
        val saved = try { (sessionStore.savedConnection() as? SavedConnection.Remote)?.session }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { throw LocalFailure("Secure storage is unavailable. Try again.") }
        if (saved == null || validatedRemoteOrigin(saved.origin) != origin)
            throw LocalFailure("The saved connection changed. Choose your connection again.")
        saved
    }

    private suspend fun restoreSession(currentSession: suspend () -> SavedRemoteSession): Bootstrap {
        check(!browser)
        authSession {
            val saved = currentSession()
            currentCoroutineContext().ensureActive()
            ensureOpen()
            if (refreshToken != null && refreshToken != saved.refreshToken) return@authSession
            refreshToken = saved.refreshToken
            refreshDevice()
            refreshVersion++
        }
        currentCoroutineContext().ensureActive()
        return bootstrap()
    }

    suspend fun restoreBrowser(): Bootstrap? {
        if (!browser) return null
        val status = requestJson("/api/auth/status").jsonObject
        if (status["authenticated"]?.jsonPrimitive?.booleanOrNull != true) return null
        csrf = status["csrf_token"]?.jsonPrimitive?.contentOrNull
        // Proactively rotate the access token so a restored session never
        // starts with already-expired credentials (random mid-use logouts).
        try { refreshBrowser() } catch (error: CancellationException) { throw error } catch (_: Exception) { /* status says authenticated; keep going on current cookies */ }
        return bootstrap()
    }

    suspend fun bootstrap(): Bootstrap {
        val value = call("/api/bootstrap").jsonObject
        requiredAudioIndexSupported = value["playback_capabilities"]?.jsonObject
            ?.get("required_audio_index")?.jsonPrimitive?.booleanOrNull == true
        val identity = value.getValue("identity").jsonObject
        csrf = identity["csrf_token"]?.jsonPrimitive?.contentOrNull ?: csrf
        val profiles = identity["profiles"]?.jsonArray.orEmpty().map {
            val p = it.jsonObject
            Profile(p.getValue("id").jsonPrimitive.content, p.getValue("name").jsonPrimitive.content, p["avatar"]?.jsonPrimitive?.contentOrNull,
                p["locked"]?.jsonPrimitive?.booleanOrNull ?: false, p["avatar_id"]?.jsonPrimitive?.contentOrNull,
                avatarStyle = p["avatar_style"]?.jsonPrimitive?.contentOrNull, avatarChoice = p["avatar_choice"]?.jsonPrimitive?.intOrNull,
                avatarUrl = p["avatar_url"]?.jsonPrimitive?.contentOrNull)
        }
        val management = value["profile_management"]?.takeUnless { it is JsonNull }?.let { codec.decodeFromJsonElement<ProfileManagement>(it) }
        return Bootstrap(management?.cards() ?: profiles, identity["profile_id"]?.jsonPrimitive?.contentOrNull,
            value["home"]?.takeUnless { it is JsonNull }?.let { codec.decodeFromJsonElement<HomeSnapshot>(it) }, profileManagement = management, plugins = value["integrations"]?.let { codec.decodeFromJsonElement(it) } ?: emptyList(),
            library = value["library"]?.takeUnless { it is JsonNull }?.let { codec.decodeFromJsonElement<CatalogPage>(it).cards },
            preferences = value["preferences"]?.takeUnless { it is JsonNull }?.let { codec.decodeFromJsonElement(it) },
            libraryMembership = value["library_membership"]?.takeUnless { it is JsonNull }?.let { codec.decodeFromJsonElement(it) },
            libraryImport = value["library_import"]?.takeUnless { it is JsonNull }?.let { codec.decodeFromJsonElement(it) },
            revisions = parseRevisions(value["revisions"]))
    }

    suspend fun selectProfile(id: String): Bootstrap {
        call("/api/auth/profile", HttpMethod.Post, buildJsonObject { put("profile_id", id) })
        return bootstrap()
    }

    suspend fun search(term: String): CatalogPage = codec.decodeFromJsonElement(call("/api/discover?type=all&search=${term.encodeURLParameter()}"))
    /**
     * Details of [card]'s title. An episode card (Continue Watching, the player)
     * looks up its parent, so the metadata expectation must describe that parent:
     * the host rejects an episode expectation for a title lookup with 400
     * "Metadata expectation does not match lookup".
     */
    suspend fun detail(card: Card, offset: Int = 0, limit: Int = 24): DetailSnapshot = (card.parent_identity ?: card.identity).let {
        detail(it.kind, it.id, offset, limit, card.provider_id, if (card.parent_identity == null) card else card.copy(identity = it, parent_identity = null))
    }
    suspend fun detail(kind: String, id: String, offset: Int = 0, limit: Int = 24, provider: String? = null, expected: Card? = null): DetailSnapshot {
        require(offset in 0..10_000)
        require(limit in 1..128)
        val parameters = mutableListOf<String>()
        if (offset != 0 || limit != 24) parameters += "offset=$offset&limit=$limit"
        if (provider == "simkl" || provider == "stremio") parameters += "provider_id=$provider"
        else provider?.removePrefix("addon:")?.takeIf { provider.startsWith("addon:") }?.toLongOrNull()?.takeIf { it > 0 }?.let {
            parameters += "provider_id=stremio&addon_id=$it"
        }
        expected?.metadata_origin?.let { origin ->
            parameters += "metadata_origin=${Json.encodeToString(origin).encodeURLParameter()}"
            parameters += "card=${Json.encodeToString(expected.lookupSelector()).encodeURLParameter()}"
        }
        val page = parameters.takeIf { it.isNotEmpty() }?.joinToString("&", prefix = "?") ?: ""
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            codec.decodeFromJsonElement(call("/api/meta/${kind.encodeURLPathPart()}/${id.encodeURLPathPart()}$page"))
        }
    }

    suspend fun subtitles(card: Card): org.viptv.engine.wire.SubtitleSnapshot = subtitlesFor(card)

    suspend fun subtitleContent(id: String): String = authenticatedResponse(
        "/api/subtitle-content/${id.encodeURLPathPart()}").let { response ->
        checkStatus(response)
        response.bodyAsText()
    }

    /** Raw bytes of an authenticated GET, such as an add-on logo. */
    internal suspend fun bytes(path: String): ByteArray = authenticatedResponse(path).let { response ->
        checkStatus(response)
        response.readRawBytes()
    }

    suspend fun call(path: String, method: HttpMethod = HttpMethod.Get, body: JsonElement? = null): JsonElement {
        return decode(authenticatedResponse(path, method, body))
    }

    /** Account-only browser operations share refresh/retirement without a media bootstrap. */
    internal suspend fun accountCall(path: String, method: HttpMethod = HttpMethod.Get, body: JsonElement? = null): JsonObject {
        check(browser && path.startsWith("/api/auth/"))
        val value = call(path, method, body).jsonObject
        if (path in setOf("/api/auth/status", "/api/auth/me", "/api/auth/login", "/api/auth/refresh"))
            csrf = value["csrf_token"]?.jsonPrimitive?.contentOrNull ?: csrf
        return value
    }

    /**
     * Status and body without generic failure copy, for endpoints whose callers map fixed error codes.
     * [timeoutMillis] replaces the shared request and socket timeouts for this one slow request only.
     */
    internal suspend fun exchange(path: String, method: HttpMethod, body: JsonElement? = null, timeoutMillis: Long? = null): Pair<Int, String> =
        authenticatedResponse(path, method, body, timeoutMillis).let { it.status.value to it.bodyAsText() }

    private suspend fun authenticatedResponse(path: String, method: HttpMethod = HttpMethod.Get, body: JsonElement? = null,
        timeoutMillis: Long? = null): HttpResponse {
        require(path.startsWith("/api/") && !path.contains('\\'))
        ensureOpen()
        val version = refreshVersion
        var response = send(path, method, body, accessToken, csrf, timeoutMillis)
        if (response.status == HttpStatusCode.Unauthorized && path !in setOf("/api/auth/login", "/api/auth/device/login", "/api/auth/logout")) {
            response.bodyAsText()
            authSession {
                if (refreshVersion == version) {
                    if (browser) refreshBrowser() else refreshDevice()
                    refreshVersion++
                }
            }
            currentCoroutineContext().ensureActive()
            ensureOpen()
            response = send(path, method, body, accessToken, csrf, timeoutMillis)
        }
        return response
    }

    private suspend fun send(path: String, method: HttpMethod, body: JsonElement?, access: String?, csrfValue: String?,
        timeoutMillis: Long? = null): HttpResponse {
        // A retirement can happen while a rotation is finishing. That rotation
        // already owns the gate; its request must complete, while ordinary new
        // calls are rejected by their entry points.
        return client.request(origin + path) {
            this.method = method
            contentType(ContentType.Application.Json)
            header("x-viptv-image-delivery", if (browser) "proxy" else "direct")
            if (!browser) header(HttpHeaders.Origin, origin)
            access?.let { bearerAuth(it) }
            csrfValue?.let { header("x-csrf-token", it) }
            body?.let { setBody(it.toString()) }
            // Overrides the HttpTimeout plugin defaults per request on OkHttp, Js and Darwin; connect stays shared.
            timeoutMillis?.let { limit -> timeout { requestTimeoutMillis = limit; socketTimeoutMillis = limit } }
        }
    }

    private suspend fun requestJson(path: String, method: HttpMethod = HttpMethod.Get, body: JsonElement? = null): JsonElement =
        decode(send(path, method, body, accessToken, csrf))

    private suspend fun decode(response: HttpResponse): JsonElement {
        checkStatus(response)
        return codec.parseToJsonElement(response.bodyAsText()).resolveRemoteImages(origin)
    }

    private suspend fun checkStatus(response: HttpResponse) {
        val status = response.status.value
        if (status in 200..299) return
        throw RemoteFailure(status, when (status) {
            401 -> "Sign in to continue."
            403 -> "This action is not available for this profile."
            429 -> tooManyRequestsMessage(response)
            else -> "The server could not complete this request."
        })
    }

    /**
     * The auth limiter answers `error_code: "rate_limited"` with the seconds it keeps refusing
     * in `Retry-After` (backend#35, backend#59), so sign-in can show that window instead of a
     * generic wait. A 429 from another limiter, or one without a positive hint, keeps the copy.
     */
    private suspend fun tooManyRequestsMessage(response: HttpResponse): String {
        val generic = "Too many requests. Please wait and try again."
        val seconds = response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()?.takeIf { it > 0 }
            ?: return generic
        val code = try {
            codec.parseToJsonElement(response.bodyAsText()).jsonObject["error_code"]?.jsonPrimitive?.contentOrNull
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
        return if (code == "rate_limited") "Too many attempts. Try again in $seconds s." else generic
    }

    private suspend fun installDeviceTokens(value: JsonObject, persist: Boolean = true) {
        val nextAccess = value["access_token"]?.jsonPrimitive?.contentOrNull ?: throw RemoteFailure(502, "The server did not provide a device session.")
        val nextRefresh = value["refresh_token"]?.jsonPrimitive?.contentOrNull ?: throw RemoteFailure(502, "The server did not provide a refresh token.")
        if (persist) persistDeviceToken(nextRefresh)
        accessToken = nextAccess
        refreshToken = nextRefresh
    }

    private suspend fun persistDeviceToken(token: String) {
        try {
            sessionStore.save(SavedRemoteSession(origin, token))
            persistenceWarning = null
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            persistenceWarning = "Secure storage is unavailable. This device will need to sign in again after the app closes."
        }
    }

    // Once sent, a rotation may already have consumed its one-use token. Navigation
    // cancellation must wait until its replacement is installed and persisted.
    private suspend fun refreshDevice() = withContext(NonCancellable) {
        try {
            withTimeout(20_000) {
                val token = refreshToken ?: throw RemoteFailure(401, "Sign in to continue.")
                val response = send("/api/auth/device/refresh", HttpMethod.Post,
                    buildJsonObject { put("refresh_token", token) }, null, null)
                if (response.status == HttpStatusCode.Unauthorized) {
                    accessToken = null; refreshToken = null
                    try { sessionStore.clear() }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { /* The server has rejected this session. */ }
                    throw RemoteFailure(401, "Sign in to continue.")
                }
                installDeviceTokens(decode(response).jsonObject)
            }
        } catch (error: TimeoutCancellationException) {
            // A timeout is not navigation cancellation. Report it: the one-use token may
            // already be consumed server-side, so the caller must not drop this silently.
            throw RemoteFailure(504, "Your server took too long to refresh this session. Try again.")
        }
    }

    private suspend fun refreshBrowser() = withContext(NonCancellable) {
        try {
            withTimeout(35_000) {
                val status = requestJson("/api/auth/status").jsonObject
                if (status["authenticated"]?.jsonPrimitive?.booleanOrNull != true) throw RemoteFailure(401, "Sign in to continue.")
                csrf = status["csrf_token"]?.jsonPrimitive?.contentOrNull
                val result = requestJson("/api/auth/refresh", HttpMethod.Post, buildJsonObject {})
                csrf = result.jsonObject["csrf_token"]?.jsonPrimitive?.contentOrNull ?: csrf
            }
        } catch (error: TimeoutCancellationException) {
            throw RemoteFailure(504, "Your server took too long to refresh this session. Try again.")
        }
    }

    suspend fun signOut() {
        authSession {
            withContext(NonCancellable) {
                try {
                    withTimeout(50_000) {
                        try {
                            if (browser) refreshBrowser() else if (refreshToken != null) refreshDevice()
                            refreshVersion++
                            requestJson("/api/auth/logout", HttpMethod.Post, buildJsonObject {})
                        } catch (error: RemoteFailure) {
                            if (error.status != 401) throw error
                        }
                        try { sessionStore.clear() }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { /* Server revocation has already completed. */ }
                        accessToken = null; refreshToken = null; csrf = null
                    }
                } catch (error: TimeoutCancellationException) {
                    // Never let a sign-out timeout masquerade as navigation cancellation:
                    // the leave dialog would close with a live session and no message.
                    throw RemoteFailure(504, "Sign out did not finish. Check the connection and try again.")
                }
            }
        }
    }

    private suspend fun <T> authSession(block: suspend () -> T): T = refreshGate.withLock {
        ensureOpen()
        if (browser) block() else nativeSessionGate.withLock { ensureOpen(); block() }
    }

    private fun ensureOpen() {
        if (retired) throw CancellationException("Connection retired")
    }

    // Mark retirement immediately, then let an already-started one-use rotation
    // finish before closing its client. New connections serialize credential
    // installation with that rotation, so the replacement account always wins.
    fun close() {
        if (retired) return
        retired = true
        if (refreshGate.tryLock()) {
            try { finishClose() } finally { refreshGate.unlock() }
        } else CoroutineScope(Dispatchers.Default).launch {
            refreshGate.withLock { finishClose() }
        }
    }

    suspend fun retire() {
        close()
        refreshGate.withLock { finishClose() }
    }

    private fun finishClose() { client.close(); accessToken = null; refreshToken = null; csrf = null }
}
