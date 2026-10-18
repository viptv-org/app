package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

internal const val stremioHandle = "synthetic_preview_123456789"
internal const val stremioAddonId = "synthetic_addon_123456789"
internal const val stremioSecondAddonId = "another_addon_1234567890"
internal const val stremioMovieId = "synthetic_movie_123456789"
internal const val stremioEpisodeId = "synthetic_episode_123456789"
private val rowCounts = mapOf("favorites_to_add" to 1, "progress_to_add" to 0, "progress_to_update" to 0, "existing_preserved" to 0, "already_imported" to 0)
private val summary = mapOf("source_items" to 2, "favorites_to_add" to 2, "progress_to_add" to 0, "progress_to_update" to 0,
    "existing_preserved" to 0, "already_imported" to 0, "needs_review" to 0, "skipped_items" to 0)
internal fun stremioExpiry(seconds: Long = 600) = System.currentTimeMillis() / 1000 + seconds
internal fun stremioAddons(profile: String = "2", expires: Long = stremioExpiry(), addons: List<StremioAddon>? = null) = StremioAddonPreview(stremioHandle, profile, expires,
    addons ?: listOf(StremioAddon(stremioSecondAddonId, "Second metadata", listOf("meta"), "add"),
        StremioAddon(stremioAddonId, "Synthetic metadata", listOf("meta", "stream"), "add"),
        StremioAddon("broken_addon_12345678901", "Broken", listOf("stream"), "unavailable", "unsupported_transport")))
internal val stremioMovie = StremioReviewItem(stremioMovieId, "Saved movie", "movie", "add", "none", "ready", rowCounts, true)
internal val stremioEpisode = stremioMovie.copy(id = stremioEpisodeId, name = "Episode resume", type = "series", season = 1, episode = 2)
internal fun stremioReview(revision: Long = 1, profile: String = "2", expires: Long = stremioExpiry(), items: List<StremioReviewItem> = listOf(stremioMovie, stremioEpisode),
    counts: Map<String, Int> = summary, addonsToAdd: Int = 1) = StremioReview(stremioHandle, profile, expires, counts, items, addonsToAdd, revision)
internal val stremioCompletion = StremioCompletion("2", mapOf("favorites_added" to 2, "progress_added" to 0, "progress_updated" to 0,
    "existing_preserved" to 0, "already_imported" to 0, "needs_review" to 0, "skipped_items" to 0))

/** Records every typed call; each result may suspend so tests control response timing. */
internal class FakeStremioTransport : StremioImportTransport {
    val calls = mutableListOf<String>()
    val passwords = mutableListOf<String>()
    var preview: suspend () -> StremioPreview = { stremioAddons() }
    var review: suspend (List<String>, Long) -> StremioReview = { _, _ -> stremioReview(reviews.toLong()) }
    var apply: suspend () -> StremioCompletion = { stremioCompletion }
    val reviews get() = calls.count { it.startsWith("review") }
    override suspend fun preview(profile: String, email: String, password: String, library: Boolean, progress: Boolean): StremioPreview {
        calls += "preview:$profile:$email:$library:$progress"; passwords += password; return preview()
    }
    override suspend fun review(profile: String, preview: String, addons: List<String>, expectedRevision: Long): StremioReview {
        calls += "review:$preview:$addons:$expectedRevision"; return review(addons, expectedRevision)
    }
    override suspend fun apply(profile: String, preview: String, excluded: List<String>, revision: Long): StremioCompletion {
        calls += "apply:$preview:$excluded:$revision"; return apply()
    }
    override suspend fun discard(profile: String, preview: String) { calls += "discard:$profile:$preview" }
}

class StremioImportTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    @AfterTest fun close() = scope.cancel()
    private fun session(transport: StremioImportTransport, editable: Boolean = true, restricted: Boolean = false, owned: () -> Boolean = { true }) =
        StremioImportSession(scope, transport, "2", "Synthetic profile", restricted, editable, owned = owned)
    private fun StremioImportSession.toItems() { connect(" synthetic@example.invalid ", "synthetic-password"); reviewItems() }

    @Test fun guidedFlowUsesSortedSelectionsRevisionAndAppliesOnlyAfterConfirmation() {
        val transport = FakeStremioTransport()
        val session = session(transport)
        session.connect(" synthetic@example.invalid ", "synthetic-password")
        val addons = session.state.value
        assertEquals(StremioImportStage.Addons, addons.stage)
        assertEquals(setOf(stremioAddonId, stremioSecondAddonId), addons.selectedAddons, "Compatible add-ons start selected; unavailable ones do not")
        assertEquals("synthetic@example.invalid", addons.email)
        assertFalse(addons.toString().contains("synthetic-password"), "The password never enters import state")
        assertEquals(listOf("synthetic-password"), transport.passwords)
        session.reviewItems()
        assertEquals("review:$stremioHandle:${listOf(stremioAddonId, stremioSecondAddonId).sorted()}:0", transport.calls[1])
        assertEquals(StremioImportStage.Items, session.state.value.stage)
        session.setItemsIncluded(listOf(stremioMovieId), false)
        assertEquals(1, session.state.value.chosenCounts?.get("favorites_to_add"))
        session.toConfirm()
        assertEquals(StremioImportStage.Confirm, session.state.value.stage)
        assertFalse(session.state.value.confirmed, "Destination confirmation starts unchecked")
        session.apply()
        assertTrue(transport.calls.none { it.startsWith("apply") }, "No apply before explicit confirmation")
        session.setConfirmed(true); session.apply()
        assertEquals("apply:$stremioHandle:[$stremioMovieId]:1", transport.calls.last())
        assertEquals(StremioImportStage.Complete, session.state.value.stage)
        assertEquals(2, session.state.value.completion?.summary?.get("favorites_added"))
        session.startAnother()
        assertEquals(StremioImportStage.Connect, session.state.value.stage)
        assertFalse(session.state.value.connected)
        assertEquals(1, session.state.value.resets)
        assertEquals("discard:2:$stremioHandle", transport.calls.last())
    }

    @Test fun backRestoresExactChoicesAndReusesAnUnchangedReview() {
        val transport = FakeStremioTransport()
        val session = session(transport)
        session.toItems()
        session.setItemsIncluded(listOf(stremioMovieId), false)
        session.toConfirm(); session.back()
        assertEquals(StremioImportStage.Items, session.state.value.stage)
        assertEquals(setOf(stremioMovieId), session.state.value.excluded)
        session.back()
        assertEquals(StremioImportStage.Addons, session.state.value.stage)
        assertEquals(setOf(stremioAddonId, stremioSecondAddonId), session.state.value.selectedAddons)
        session.reviewItems()
        assertEquals(1, transport.reviews, "Unchanged add-on choices reuse the current review")
        assertEquals(setOf(stremioMovieId), session.state.value.excluded)
    }

    @Test fun changedAddonsReReviewWithExpectedRevisionAndKeepStableExclusions() {
        val transport = FakeStremioTransport()
        val session = session(transport)
        session.toItems()
        session.setItemsIncluded(listOf(stremioMovieId), false)
        session.back(); session.toggleAddon(stremioSecondAddonId, false); session.reviewItems()
        assertEquals("review:$stremioHandle:[$stremioAddonId]:1", transport.calls.last())
        assertEquals(2L, session.state.value.review?.revision)
        assertEquals(setOf(stremioMovieId), session.state.value.excluded)
        session.back(); session.continueWithoutAddons()
        assertEquals("review:$stremioHandle:[]:2", transport.calls.last())
        assertTrue(session.state.value.selectedAddons.isEmpty())
    }

    @Test fun backToConnectKeepsTheDraftWithoutThePasswordAndChangeConnectionDiscardsIt() {
        val transport = FakeStremioTransport()
        val session = session(transport)
        session.setProgress(false)
        session.connect("synthetic@example.invalid", "synthetic-password")
        session.toggleAddon(stremioAddonId, false)
        session.back()
        val connect = session.state.value
        assertEquals(StremioImportStage.Connect, connect.stage)
        assertTrue(connect.connected)
        assertEquals("synthetic@example.invalid", connect.email)
        assertTrue(transport.calls.none { it.startsWith("discard") })
        session.connect("other@example.invalid", "again")
        assertEquals(1, transport.calls.count { it.startsWith("preview") }, "A connected draft is resumed, not re-previewed")
        session.resume()
        assertEquals(StremioImportStage.Addons, session.state.value.stage)
        assertEquals(setOf(stremioSecondAddonId), session.state.value.selectedAddons)
        session.back(); session.changeConnection()
        assertFalse(session.state.value.connected)
        assertEquals("", session.state.value.email)
        assertFalse(session.state.value.progress, "Source choices survive a new connection, as on the web dashboard")
        assertEquals("discard:2:$stremioHandle", transport.calls.last())
    }

    @Test fun uncertainApplyLocksTheSelectionAndRetriesTheIdenticalRequest() {
        val transport = FakeStremioTransport()
        var fail = true
        transport.apply = { if (fail) { fail = false; throw IllegalStateException("private upstream") }; stremioCompletion }
        val session = session(transport)
        session.toItems(); session.setItemsIncluded(listOf(stremioEpisodeId), false); session.toConfirm()
        session.setConfirmed(true); session.apply()
        val failed = session.state.value
        assertEquals(stremioUncertainApplyCopy, failed.error)
        assertTrue(failed.selectionLocked)
        session.back(); session.cancel(); session.setItemsIncluded(listOf(stremioEpisodeId), true)
        assertEquals(StremioImportStage.Confirm, session.state.value.stage)
        assertEquals(setOf(stremioEpisodeId), session.state.value.excluded)
        session.apply()
        val applies = transport.calls.filter { it.startsWith("apply") }
        assertEquals(2, applies.size); assertEquals(applies[0], applies[1])
        assertEquals(StremioImportStage.Complete, session.state.value.stage)
    }

    @Test fun applyCannotBeCancelledOrRepeatedWhileRunning(): Unit = runBlocking {
        val transport = FakeStremioTransport()
        val gate = CompletableDeferred<StremioCompletion>()
        transport.apply = { gate.await() }
        val session = session(transport)
        session.toItems(); session.toConfirm(); session.setConfirmed(true); session.apply()
        assertEquals(StremioImportBusy.Apply, session.state.value.busy)
        session.apply(); session.back(); session.cancel(); session.setConfirmed(false); session.changeConnection()
        assertEquals(1, transport.calls.count { it.startsWith("apply") })
        assertEquals(StremioImportStage.Confirm, session.state.value.stage)
        assertTrue(session.state.value.confirmed)
        gate.complete(stremioCompletion)
        assertEquals(StremioImportStage.Complete, session.state.value.stage)
        assertNull(session.state.value.busy)
    }

    @Test fun emptyScopesCannotBeConfirmedAndAddonOnlyScopesCan() {
        val transport = FakeStremioTransport()
        val session = session(transport)
        session.toItems()
        session.setItemsIncluded(listOf(stremioMovieId, stremioEpisodeId), false)
        assertTrue(session.state.value.hasScope, "New account add-ons remain a valid scope")
        transport.review = { _, revision -> stremioReview(revision + 1, addonsToAdd = 0) }
        session.back(); session.continueWithoutAddons()
        session.setItemsIncluded(listOf(stremioMovieId, stremioEpisodeId), false)
        assertFalse(session.state.value.hasScope)
        session.toConfirm()
        assertEquals(StremioImportStage.Items, session.state.value.stage, "An empty scope cannot reach confirmation")
    }

    @Test fun anyScopeChangeOrBackClearsTheDestinationConfirmation() {
        val transport = FakeStremioTransport()
        val session = session(transport)
        session.toItems(); session.toConfirm(); session.setConfirmed(true)
        session.back()
        assertFalse(session.state.value.confirmed)
        session.toConfirm(); assertFalse(session.state.value.confirmed)
        session.setConfirmed(true); session.back(); session.setItemsIncluded(listOf(stremioMovieId), false)
        session.toConfirm(); assertFalse(session.state.value.confirmed)
    }

    @Test fun nativeDeviceSessionsSeeAddonsReadOnlyAndReviewWithNone() {
        val transport = FakeStremioTransport()
        transport.review = { addons, _ -> assertTrue(addons.isEmpty()); stremioReview(addonsToAdd = 0) }
        val session = session(transport, editable = false)
        session.connect("synthetic@example.invalid", "synthetic-password")
        assertEquals(StremioImportStage.Addons, session.state.value.stage)
        assertTrue(session.state.value.selectedAddons.isEmpty())
        session.selectAllAddons(); session.toggleAddon(stremioAddonId, true); session.continueWithoutAddons()
        assertTrue(session.state.value.selectedAddons.isEmpty())
        assertEquals(0, transport.reviews)
        session.reviewItems()
        assertEquals("review:$stremioHandle:[]:0", transport.calls.last())
        assertEquals(StremioImportStage.Items, session.state.value.stage)
        session.back(); session.reviewItems()
        assertEquals(1, transport.reviews)
    }

    @Test fun cancelledAndReplacedRequestsCannotPublishLateResponses(): Unit = runBlocking {
        val transport = FakeStremioTransport()
        val late = CompletableDeferred<StremioPreview>()
        transport.preview = { withContext(NonCancellable) { late.await() } }
        val session = session(transport)
        session.connect("synthetic@example.invalid", "synthetic-password")
        assertEquals(StremioImportBusy.Preview, session.state.value.busy)
        session.cancel()
        late.complete(stremioAddons())
        yield()
        assertEquals(StremioImportStage.Connect, session.state.value.stage)
        assertFalse(session.state.value.connected)
        assertNull(session.state.value.busy)

        var owner = true
        val review = CompletableDeferred<StremioReview>()
        val replaced = FakeStremioTransport().apply { this.review = { _, _ -> withContext(NonCancellable) { review.await() } } }
        val other = session(replaced, owned = { owner })
        other.connect("synthetic@example.invalid", "synthetic-password"); other.reviewItems()
        owner = false
        review.complete(stremioReview())
        yield()
        assertEquals(StremioImportStage.Addons, other.state.value.stage, "A response for a replaced profile is ignored")
    }

    @Test fun stalePreviewCodesDiscardTheDraftWithFixedCopy() {
        val transport = FakeStremioTransport()
        transport.review = { _, _ -> throw StremioImportFailure(409, "stremio_preview_stale") }
        val session = session(transport)
        session.toItems()
        val state = session.state.value
        assertEquals(StremioImportStage.Connect, state.stage)
        assertTrue(state.expired); assertFalse(state.connected)
        assertEquals(stremioStaleCopy, state.error)
        assertEquals("discard:2:$stremioHandle", transport.calls.last())

        val applying = FakeStremioTransport().apply { apply = { throw StremioImportFailure(410, "stremio_preview_expired") } }
        val second = session(applying)
        second.toItems(); second.toConfirm(); second.setConfirmed(true); second.apply()
        assertEquals(stremioStaleCopy, second.state.value.error)
        assertFalse(second.state.value.selectionLocked)
        assertEquals(StremioImportStage.Connect, second.state.value.stage)
    }

    @Test fun previewExpiryDiscardsTheDraftAndAnUncertainApplyWarnsAboutSavedChanges() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val local = CoroutineScope(SupervisorJob() + dispatcher)
        val base = 1_800_000_000_000L
        val now = { base + testScheduler.currentTime }
        val transport = FakeStremioTransport()
        transport.preview = { stremioAddons(expires = base / 1000 + 60) }
        transport.review = { _, _ -> stremioReview(expires = base / 1000 + 60) }
        val session = StremioImportSession(local, transport, "2", "Synthetic profile", false, true, now = now)
        session.connect("synthetic@example.invalid", "synthetic-password"); runCurrent()
        session.reviewItems(); runCurrent()
        assertEquals(StremioImportStage.Items, session.state.value.stage)
        advanceTimeBy(61_000); runCurrent()
        assertEquals(StremioImportStage.Connect, session.state.value.stage)
        assertEquals(stremioStaleCopy, session.state.value.error)
        assertTrue(transport.calls.last().startsWith("discard"))

        transport.apply = { throw IllegalStateException("unknown outcome") }
        val started = testScheduler.currentTime
        transport.preview = { stremioAddons(expires = (base + started) / 1000 + 30) }
        transport.review = { _, _ -> stremioReview(expires = (base + started) / 1000 + 30) }
        session.connect("synthetic@example.invalid", "synthetic-password"); runCurrent()
        session.reviewItems(); runCurrent(); session.toConfirm(); session.setConfirmed(true); session.apply(); runCurrent()
        assertEquals(stremioUncertainApplyCopy, session.state.value.error)
        advanceTimeBy(31_000); runCurrent()
        assertTrue(session.state.value.error!!.contains("Some changes may already have been saved"))
        assertFalse(session.state.value.connected)
        local.cancel()
    }

    @Test fun restrictedProfilesNeverStartAPreview() {
        val transport = FakeStremioTransport()
        val session = session(transport, restricted = true)
        session.connect("synthetic@example.invalid", "synthetic-password")
        assertTrue(transport.calls.isEmpty())
        assertEquals(StremioImportStage.Connect, session.state.value.stage)
    }

    @Test fun failedAddonVerificationNamesTheAddonAndRequiresAnotherReview() {
        val transport = FakeStremioTransport()
        transport.review = { addons, _ ->
            if (stremioSecondAddonId in addons) throw StremioImportFailure(502, "stremio_addon_unavailable", listOf(stremioSecondAddonId, "not_in_this_preview_1234"))
            stremioReview(transport.reviews.toLong())
        }
        val session = session(transport)
        session.toItems()
        assertEquals(StremioImportStage.Addons, session.state.value.stage)
        assertEquals(setOf(stremioSecondAddonId), session.state.value.failedAddons)
        assertEquals("An add-on could not be verified. Change your add-on choices and try again.", session.state.value.error)
        session.toggleAddon(stremioSecondAddonId, false); session.reviewItems()
        assertEquals(StremioImportStage.Items, session.state.value.stage)
        assertTrue(session.state.value.failedAddons.isEmpty())
    }

    @Test fun replacedReviewHandlesAndNonIncreasingRevisionsAreRejected() {
        val transport = FakeStremioTransport()
        transport.review = { _, _ -> stremioReview().copy(previewId = "replacement_preview_123456789") }
        val session = session(transport)
        session.toItems()
        assertEquals("The server returned an unexpected import review. Start a new preview or update VIPTV.", session.state.value.error)
        assertEquals(StremioImportStage.Addons, session.state.value.stage)
        val repeated = FakeStremioTransport().apply { review = { _, _ -> stremioReview(1) } }
        val second = session(repeated)
        second.toItems(); second.back(); second.toggleAddon(stremioAddonId, false); second.reviewItems()
        assertEquals("The server returned an unexpected import review. Start a new preview or update VIPTV.", second.state.value.error)
    }

    @Test fun everyServerCodeMapsToFixedCopy() {
        val expected = mapOf(
            "stremio_credentials_invalid" to "Stremio sign-in failed. Check your details.",
            "stremio_preview_stale" to stremioStaleCopy, "stremio_preview_expired" to stremioStaleCopy,
            "stremio_preview_not_found" to stremioStaleCopy, "stremio_selection_mismatch" to stremioStaleCopy,
            "stremio_restricted_profile" to "Switch to an unrestricted profile to import.",
            "stremio_source_unavailable" to "Could not read Stremio. Try again.",
            "stremio_addon_unavailable" to "An add-on could not be verified. Change your add-on choices and try again.",
            "stremio_import_busy" to "Too many imports are pending. Cancel a preview or try again shortly.",
            "stremio_storage_unavailable" to "Import storage is temporarily unavailable. Try again.",
            "stremio_invalid_request" to "This import request could not be accepted. Start a new preview and try again.",
            "secret_store_not_configured" to "The server needs encrypted add-on storage before importing configuration.",
            "account_session_required" to "Add-ons can only be added from VIPTV on the web.")
        expected.forEach { (code, copy) -> assertEquals(copy, stremioImportError(StremioImportFailure(400, code), false), code) }
        listOf("Invalid review", "Invalid add-ons", "Replaced review").forEach {
            assertEquals("The server returned an unexpected import review. Start a new preview or update VIPTV.", stremioImportError(StremioInvalidResponse(it), false))
        }
        assertEquals(stremioUncertainApplyCopy, stremioImportError(StremioInvalidResponse("Invalid completion"), true))
        assertEquals("Could not prepare the import. Check your details or add-on choices and try again.", stremioImportError(StremioImportFailure(500, "private https://upstream"), false))
        assertEquals(stremioUncertainApplyCopy, stremioImportError(IllegalStateException("private"), true))
    }

    // Wire contract: request bodies, device sessions and response validation.
    private class Store : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject
    private fun addonsJson(profile: String = "2", expires: Long = stremioExpiry(), addons: String = """[{"item_id":"$stremioAddonId","name":"Synthetic metadata","resources":["meta"],"status":"add"},{"item_id":"$stremioSecondAddonId","name":"Second","resources":["stream"],"status":"existing"}]""") =
        """{"stage":"addons","preview_id":"$stremioHandle","profile_id":$profile,"expires_at":$expires,"addons":$addons}"""
    private fun reviewJson(revision: String = "1", items: String = """[{"item_id":"$stremioMovieId","name":"Saved movie","type":"movie","favorite_action":"add","progress_action":"none","status":"ready","counts":{"favorites_to_add":1,"progress_to_add":0,"progress_to_update":0,"existing_preserved":0,"already_imported":0},"selectable":true,"season":null,"position":12.5,"duration":3600,"watched":false}]""",
        favorites: Int = 1) =
        """{"stage":"review","preview_id":"$stremioHandle","profile_id":"2","expires_at":${stremioExpiry()},"addons_to_add":1,"review_revision":$revision,"summary":{"source_items":1,"favorites_to_add":$favorites,"progress_to_add":0,"progress_to_update":0,"existing_preserved":0,"already_imported":0,"needs_review":0,"skipped_items":0},"review_items":$items}"""
    private val completionJson = """{"profile_id":2,"summary":{"favorites_added":1,"progress_added":0,"progress_updated":0,"existing_preserved":0,"already_imported":0,"needs_review":0,"skipped_items":0},"addons_added":1,"already_completed":false}"""

    @Test fun deviceSessionsSendTheDocumentedBodiesAndSortedHandles(): Unit = runBlocking {
        val bodies = mutableListOf<Triple<HttpMethod, String, JsonObject?>>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("https://watch.example", request.headers[HttpHeaders.Origin], "Native device requests carry the device origin")
            bodies += Triple(request.method, request.url.encodedPath, (request.body as? TextContent)?.text?.takeIf { it.isNotBlank() }?.let(::json))
            val path = request.url.encodedPath
            respond(when {
                path.endsWith("/preview") -> addonsJson()
                path.endsWith("/review") -> reviewJson()
                path.endsWith("/apply") -> completionJson
                else -> ""
            }, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", Store(), client, browserMode = false)
        try {
            assertFalse(remote.accountManagementEnabled)
            val api = RemoteStremioImport(remote)
            assertIs<StremioAddonPreview>(api.preview("2", "synthetic@example.invalid", "synthetic-password", true, false))
            api.review("2", stremioHandle, listOf(stremioSecondAddonId, stremioAddonId), 0)
            val done = api.apply("2", stremioHandle, listOf(stremioMovieId, stremioEpisodeId), 1)
            assertEquals(1, done.addonsAdded)
            api.discard("2", stremioHandle)
            assertEquals(json("""{"email":"synthetic@example.invalid","password":"synthetic-password","import_library":true,"import_progress":false,"inspect_addons":true}"""), bodies[0].third)
            assertEquals("/api/profiles/2/imports/stremio/preview", bodies[0].second)
            assertEquals(buildJsonObject { put("selected_addons", JsonArray(listOf(stremioSecondAddonId, stremioAddonId).sorted().map(::JsonPrimitive)))
                put("expected_review_revision", 0) }, bodies[1].third)
            assertEquals(buildJsonObject { put("confirm", true); put("excluded_items", JsonArray(listOf(stremioMovieId, stremioEpisodeId).sorted().map(::JsonPrimitive))); put("review_revision", 1) }, bodies[2].third)
            assertEquals(HttpMethod.Delete to "/api/profiles/2/imports/stremio/$stremioHandle", bodies[3].first to bodies[3].second)
        } finally { remote.close() }
    }

    @Test fun errorsKeepOnlyTheCodeAndSafeAddonHandles(): Unit = runBlocking {
        val client = HttpClient(MockEngine { request ->
            val body = if (request.url.encodedPath.endsWith("/review"))
                """{"error_code":"stremio_addon_unavailable","error":"private https://source.invalid/token=secret","failed_addon_items":["$stremioAddonId","$stremioAddonId","https://private.invalid",42,"${"x".repeat(129)}"]}"""
            else """{"error_code":"account_session_required","error":"private"}"""
            respond(body, HttpStatusCode.BadGateway, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        try {
            val api = RemoteStremioImport(remote)
            val review = assertFailsWith<StremioImportFailure> { api.review("2", stremioHandle, listOf(stremioAddonId), 0) }
            assertEquals("stremio_addon_unavailable", review.code); assertEquals(listOf(stremioAddonId), review.failedAddons)
            assertFalse(review.message.orEmpty().contains("private"))
            val preview = assertFailsWith<StremioImportFailure> { api.preview("2", "a@example.invalid", "p", true, true) }
            assertEquals("account_session_required", preview.code); assertTrue(preview.failedAddons.isEmpty())
        } finally { remote.close() }
    }

    @Test fun responsesAreValidatedBeforeTheyCanBeShown() {
        val now = System.currentTimeMillis()
        assertNotNull(parseAddons(json(addonsJson()), "2", now))
        assertNull(parseAddons(json(addonsJson(profile = "3")), "2", now), "Another profile's preview")
        assertNull(parseAddons(json(addonsJson(expires = now / 1000 - 1)), "2", now), "Already expired")
        assertNull(parseAddons(json(addonsJson(addons = """[{"item_id":"$stremioAddonId","name":"A","resources":["meta"],"status":"add"},{"item_id":"$stremioAddonId","name":"B","resources":["meta"],"status":"add"}]""")), "2", now), "Duplicate handles")
        assertNull(parseAddons(json(addonsJson(addons = """[{"item_id":"$stremioAddonId","name":"https://private.invalid","resources":["meta"],"status":"add"}]""")), "2", now), "URL-shaped names")
        assertNull(parseAddons(json(addonsJson(addons = """[{"item_id":"short","name":"A","resources":["meta"],"status":"add"}]""")), "2", now), "Untoken-shaped handles")
        assertNull(parseAddons(json(addonsJson(addons = """[{"item_id":"$stremioAddonId","name":"A","resources":["configure"],"status":"add"}]""")), "2", now))
        val review = assertNotNull(parseReview(json(reviewJson()), "2", now))
        assertEquals(12.5, review.items.single().position); assertNull(review.items.single().season)
        assertNull(parseReview(json(reviewJson(revision = "0")), "2", now), "Revision must be positive")
        assertNull(parseReview(json(reviewJson(revision = "\"1\"")), "2", now), "Revision must be a number")
        assertNull(parseReview(json(reviewJson(favorites = 2)), "2", now), "Row counts must sum to the summary")
        assertNull(parseReview(json(reviewJson(items = reviewJson().let { json(it)["review_items"].toString() }.replace("\"position\":12.5", "\"position\":-1"))), "2", now))
        assertNull(parseReview(json(reviewJson(items = reviewJson().let { json(it)["review_items"].toString() }.replace("\"watched\":false", "\"watched\":\"no\""))), "2", now))
        assertNull(parseReview(json(reviewJson().replace("\"addons_to_add\":1", "\"addons_to_add\":100001")), "2", now), "Bounded counts")
        assertNotNull(parseCompletion(json(completionJson), "2"))
        assertNull(parseCompletion(json(completionJson), "3"))
        assertNull(parseCompletion(json(completionJson.replace("\"addons_added\":1", "\"addons_added\":-1")), "2"))
    }

    @Test fun profileChangeAndSignOutDiscardTheDraftAndDeleteTheServerPreview(): Unit = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = HttpClient(MockEngine { request ->
            requests += request.method to request.url.encodedPath
            val path = request.url.encodedPath
            if (path.endsWith("/preview")) respond(addonsJson(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            else respond("{}", if (request.method == HttpMethod.Delete) HttpStatusCode.OK else HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        try {
            val controller = ApplicationController(scope, Store())
            controller.connection = remote
            val profiles = listOf(Profile("2", "Synthetic profile", null), Profile("3", "Replacement", null))
            controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "2", profiles = profiles)
            val session = assertNotNull(controller.openStremioImport(false))
            assertTrue(session.state.value.addonsEditable, "Browser account sessions choose add-ons")
            session.connect("synthetic@example.invalid", "synthetic-password")
            withTimeout(5_000) { while (session.state.value.stage != StremioImportStage.Addons) delay(5) }
            controller.accept(Bootstrap(profiles, "2", null))
            assertTrue(session.state.value.connected, "A refreshed bootstrap for the same profile keeps the draft")
            controller.accept(Bootstrap(profiles, "3", null))
            assertFalse(session.state.value.connected)
            withTimeout(5_000) { while (requests.none { it.first == HttpMethod.Delete }) delay(5) }
            assertEquals(HttpMethod.Delete to "/api/profiles/2/imports/stremio/$stremioHandle", requests.last { it.first == HttpMethod.Delete })
            assertNull(controller.stremioImport)
            session.connect("synthetic@example.invalid", "synthetic-password")
            assertEquals(1, requests.count { it.second.endsWith("/preview") }, "A discarded draft cannot start new requests")

            val next = assertNotNull(controller.openStremioImport(false))
            controller.disconnect()
            assertNull(controller.stremioImport)
            next.connect("synthetic@example.invalid", "synthetic-password")
            assertEquals(1, requests.count { it.second.endsWith("/preview") })
        } finally { remote.close() }
    }
}
