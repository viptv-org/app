package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.engine.wire.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A fake host whose profile "1" has [count] titles in progress, served by `GET /api/profiles/1/continue`
 * with the server's offset/limit paging and next cursor (backend `continuation/page.rs`). Source discovery
 * finds nothing, and Details are not in the fixture, so a resumed card stays on its source selector.
 */
internal class ContinueWatchingHost(@Volatile var count: Int) : AutoCloseable {
    /** Every list read, as `offset+limit`. */
    val reads = CopyOnWriteArrayList<String>()
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private fun MockRequestHandleScope.json(text: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))
    val client = HttpClient(MockEngine { request ->
        when (request.url.encodedPath) {
            "/api/profiles/1/continue" -> {
                val offset = request.url.parameters["offset"]?.toInt() ?: 0
                val limit = request.url.parameters["limit"]?.toInt() ?: 40
                reads += "$offset+$limit"
                val total = count
                val entries = (offset + 1..minOf(total, offset + limit)).map(::continueEntry)
                val next = (offset + limit < total).takeIf { it }?.let { (offset + limit).toString() }
                json(Json.encodeToString(ContinuationPage(Scope("viptv:remote", "account", "1", 1), 1, entries, next, next == null)))
            }
            "/api/streams" -> json("""{"id":"job-1"}""")
            "/api/streams/job-1" -> json("""{"done":true,"events":[]}""")
            else -> if (request.url.encodedPath.endsWith("/source-ranking")) json("""{"streams":[]}""")
                else json("""{"error":"not in this fixture"}""", HttpStatusCode.NotFound)
        }
    }) { configureTransport() }
    val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
    val sessionStore: RemoteSessionStore get() = store
    override fun close() = remote.close()

    companion object {
        private val origin = MetadataOrigin("stremio", "addon:4", "c".repeat(32))
        private fun show(number: Int) = "tt%07d".format(number)
        /** "Show 07" resuming at S1 E2: a scoped episode, so OK goes straight to its sources. */
        fun card(number: Int): Card {
            val series = MediaIdentity(show(number), "series", listOf(ExternalId("imdb", show(number))))
            return Card(MediaIdentity("${show(number)}:1:2", "series", emptyList(), EpisodeCoordinates("aired", 1, 2)),
                provider_id = "addon:4", title = "Show %02d".format(number), subtitle = "S1 E2 · 10m left", progress = 0.4,
                badges = emptyList(), parent_identity = series, metadata_origin = origin)
        }
        fun continueEntry(number: Int) = card(number).let { card ->
            ContinuationEntry(card, PlaybackRecord(identity = card.identity, provider_id = card.provider_id, title = card.title,
                position_millis = 600_000, duration_millis = 1_500_000, updated_at_millis = 1_000_000L - number,
                parent_identity = card.parent_identity, metadata_origin = card.metadata_origin), ContinuationStatus.RESUME)
        }
        /** Home as the server builds it: the row holds the list's first [shown] entries (12 at most) and the `Continue` action. */
        fun home(shown: Int, hero: Card? = null) = HomeSnapshot(Scope("viptv:remote", "account", "1", 1), 1, hero = hero,
            rows = listOf(HomeRow(browse = HomeBrowseAction(HomeBrowseTarget.CONTINUE), id = "continue", title = "Continue Watching",
                provider_id = "library", state = RowState.READY, cards = (1..minOf(shown, 12)).map(::card))),
            complete = true)
    }
}
