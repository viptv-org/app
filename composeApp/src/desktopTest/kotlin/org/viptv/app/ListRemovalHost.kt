package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.*

internal class ListRemovalHost : AutoCloseable {
    val deletes = CopyOnWriteArrayList<String>()
    val visibility = CopyOnWriteArrayList<JsonObject>()
    var libraryCards = (1..3).map(::libraryCard)
    var continueCards = (1..4).map(::continueCard)
    var refuseVisibility = false
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private fun MockRequestHandleScope.json(text: String) =
        respond(text, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    private fun MockRequestHandleScope.json(element: JsonElement) = json(Json.encodeToString(element))
    private val snapshot: LibrarySnapshot
        get() = LibrarySnapshot(scope, 1, defaultLibraryQuery(), libraryCards, complete = true,
            availability = Availability.AVAILABLE)
    private val membership: LibraryMembershipSnapshot
        get() = LibraryMembershipSnapshot(libraryCards.map { it.identity }, 1, complete = true)
    val client = HttpClient(MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path == "/api/profiles/1/library/membership" -> json(Json.encodeToJsonElement(membership))
            path.startsWith("/api/profiles/1/library/") && request.method == HttpMethod.Delete -> {
                val id = path.removePrefix("/api/profiles/1/library/")
                deletes += id
                libraryCards = libraryCards.filterNot { "${it.identity.kind}/${it.identity.id}" == id }
                json("""{"ok":true}""")
            }
            path == "/api/profiles/1/library" -> json(Json.encodeToJsonElement(snapshot))
            path == "/api/profiles/1/continue" -> json(Json.encodeToJsonElement(ContinuationPage(scope, 1,
                continueCards.map { ContinuationEntry(it, record(it), ContinuationStatus.RESUME) }, null, true)))
            path == "/api/profiles/1/continue/visibility" -> {
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                if (refuseVisibility) respond("""{"error":"visibility unavailable"}""", HttpStatusCode.InternalServerError,
                    headersOf(HttpHeaders.ContentType, "application/json")) else {
                    visibility += body
                    // The host resolves an episode to the title it hides, which Undo then shows again.
                    val hidden = body["hidden"]!!.jsonPrimitive.boolean
                    val resolved = Json.decodeFromJsonElement<MediaIdentity>(
                        body[if (hidden) "parent_identity" else "identity"]!!)
                    json(Json.encodeToJsonElement(QueueVisibilityReceipt(scope, 2, resolved, hidden)))
                }
            }
            else -> respond("""{"error":"not in this fixture"}""", HttpStatusCode.NotFound)
        }
    }) { configureTransport() }
    val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
    val sessionStore: RemoteSessionStore get() = store
    override fun close() = remote.close()

    companion object {
        val scope = Scope("viptv:remote", "account", "1", 1)
        private val origin = MetadataOrigin("stremio", "addon:4", "c".repeat(32))
        fun series(number: Int) = MediaIdentity("tt%07d".format(number), "series",
            listOf(ExternalId("imdb", "tt%07d".format(number))))
        fun libraryCard(number: Int) = Card(series(number), provider_id = "addon:4",
            title = "Show %02d".format(number), badges = emptyList(), metadata_origin = origin)
        fun continueCard(number: Int) = Card(MediaIdentity("${series(number).id}:1:2", "series", emptyList(),
            EpisodeCoordinates("aired", 1, 2)), provider_id = "addon:4", title = "Show %02d".format(number),
            subtitle = "S1 E2 · 10m left", progress = 0.4, badges = emptyList(), parent_identity = series(number),
            metadata_origin = origin)
        fun record(card: Card) = PlaybackRecord(identity = card.identity, provider_id = card.provider_id,
            title = card.title, position_millis = 600_000, duration_millis = 1_500_000,
            updated_at_millis = 1_000_000L, parent_identity = card.parent_identity, metadata_origin = card.metadata_origin)
    }
}
