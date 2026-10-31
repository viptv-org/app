package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

internal fun parentFixture(kids: Boolean = false, unlocked: Boolean = true, configured: Boolean = true,
    leavePin: Boolean = false): ProfileManagement {
    val yes = FeatureAvailability("Available")
    return ProfileManagement(listOf(ServerProfile("1", "Parent"), ServerProfile("2", "Child", kids = kids, max_age = 10)),
        ProfileCapabilities(yes, yes, yes, yes,
            FeatureAvailability(if (leavePin) "Available" else "Unsupported"), yes), ParentAccess(configured, unlocked, false))
}

internal val parentRatings = KidsRatings(
    movie = listOf(RatingLabel("G", 0), RatingLabel("PG", 10), RatingLabel("PG-13", 13), RatingLabel("R", 17), RatingLabel("NC-17", 18)),
    series = listOf(RatingLabel("TV-Y", 0), RatingLabel("TV-Y7", 7), RatingLabel("TV-G", 0), RatingLabel("TV-PG", 10),
        RatingLabel("TV-14", 14), RatingLabel("TV-MA", 18)))

internal fun parentKids(enabled: Boolean = true, maxAge: Int = 10, movie: String? = null, series: String? = null,
    unrated: Boolean = false) = KidsPolicy(enabled, maxAge, movie, series, unrated, parentRatings)

class ParentControlsTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private fun client(handle: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> HttpResponseData) = HttpClient(MockEngine(handle)) { configureTransport() }

    @Test fun unlockAndPinChangesUseHouseholdEndpointsWithoutSwitchingProfiles() = runBlocking {
        val writes = mutableListOf<Pair<String, JsonObject>>()
        val remote = RemoteConnection("https://watch.example", store, client { request ->
            if (request.method != HttpMethod.Get) writes += request.url.encodedPath to Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond(if (request.method == HttpMethod.Get) Json.encodeToString(parentFixture()) else "{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, browserMode = true)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1")
        try {
            controller.refreshParentControls(); controller.job!!.join()
            controller.unlockParentControls("0123"); controller.job!!.join()
            controller.changeParentPin("0123", "456789"); controller.job!!.join()
            assertEquals(listOf("/api/parent/unlock", "/api/parent/pin"), writes.map { it.first })
            assertEquals("0123", writes[0].second["pin"]?.jsonPrimitive?.content)
            assertEquals("0123", writes[1].second["current_pin"]?.jsonPrimitive?.content)
            assertEquals("456789", writes[1].second["pin"]?.jsonPrimitive?.content)
            assertEquals("1", controller.state.value.selectedProfile)
            assertEquals(2, controller.state.value.parentSettingsSaved)
        } finally { remote.close() }
    }

    @Test fun invalidPinRemovalAndForeignProfileMakeNoMutationRequests() = runBlocking {
        var requests = 0
        val remote = RemoteConnection("https://watch.example", store, client { requests++; error("No request expected") }, browserMode = true)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1", parentSettings = parentFixture(kids = true))
        try {
            controller.changeParentPin("1234", ""); controller.job!!.join()
            assertContains(controller.state.value.error.orEmpty(), "every profile")
            controller.changeParentPin("1234", "12"); controller.job!!.join()
            controller.changeKidsPolicy("foreign", KidsPolicy(true, 6)); controller.job!!.join()
            controller.unlockParentControls("१२३४"); controller.job!!.join()
            assertEquals(0, requests)
        } finally { remote.close() }
    }

    @Test fun editingCurrentProfileReplacesCachedAdultContentWithAuthoritativeBootstrap() = runBlocking {
        val requests = mutableListOf<String>()
        val remote = RemoteConnection("https://watch.example", store, client { request ->
            requests += request.url.encodedPath
            val response = if (request.method == HttpMethod.Put) {
                val value = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals(true, value["enabled"]?.jsonPrimitive?.boolean)
                assertEquals(10, value["max_age"]?.jsonPrimitive?.int)
                "{}"
            } else buildJsonObject {
                put("identity", buildJsonObject { put("profiles", JsonArray(emptyList())); put("profile_id", "2") })
                put("profile_management", Json.encodeToJsonElement(parentFixture(kids = true, unlocked = false)))
                put("library", Json.encodeToJsonElement(CatalogPage(emptyList(), null, true)))
            }.toString()
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, browserMode = true)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        val adult = Card(MediaIdentity("adult", "movie", emptyList()), provider_id = "simkl", title = "Adult title", badges = emptyList())
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "2", parentSettings = parentFixture(), results = listOf(adult), library = listOf(adult))
        try {
            controller.changeKidsPolicy("2", KidsPolicy(true, 10)); controller.job!!.join()
            assertEquals(listOf("/api/profiles/2/kids", "/api/bootstrap", "/api/profiles/1/kids", "/api/profiles/2/kids"), requests)
            assertTrue(controller.state.value.results.isEmpty()); assertTrue(controller.state.value.library.isEmpty())
            assertTrue(controller.state.value.parentSettings!!.profiles.last().kids)
            assertEquals(Screen.Settings, controller.state.value.screen)
        } finally { remote.close() }
    }

    @Test fun nativeSessionsSetChangeRemovePinAndKidsPolicyThroughFakeHost() = runBlocking {
        val writes = mutableListOf<Pair<String, JsonObject>>()
        val remote = RemoteConnection("https://watch.example", store, client { request ->
            if (request.method != HttpMethod.Get)
                writes += request.url.encodedPath to Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond(if (request.method == HttpMethod.Get) Json.encodeToString(parentFixture()) else "{}",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1",
            parentSettings = parentFixture(configured = false))
        try {
            controller.changeParentPin("", "4321"); controller.job!!.join()
            assertEquals("/api/parent/pin" to buildJsonObject { put("pin", "4321") }, writes.last())
            controller.mutable.value = controller.state.value.copy(parentSettings = parentFixture(configured = true))
            controller.changeParentPin("4321", ""); controller.job!!.join()
            assertEquals("/api/parent/pin" to buildJsonObject { put("pin", ""); put("current_pin", "4321") }, writes.last())
            controller.changeKidsPolicy("2", KidsPolicy(true, 12)); controller.job!!.join()
            assertEquals("/api/profiles/2/kids" to kidsBody(true, 12, "", "", false), writes.last())
            controller.changeKidsPolicy("2", KidsPolicy(false, 12)); controller.job!!.join()
            assertEquals("/api/profiles/2/kids" to kidsBody(false, 12, "", "", false), writes.last())
            assertNull(controller.state.value.error)
        } finally { remote.close() }
    }

    @Test fun ratingLimitsAndTheUnratedToggleSaveThroughTheKidsRoute() = runBlocking<Unit> {
        val writes = mutableListOf<Pair<String, JsonObject>>()
        val remote = RemoteConnection("https://watch.example", store, client { request ->
            when {
                request.method == HttpMethod.Put -> {
                    writes += request.url.encodedPath to Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    respond(Json.encodeToString(parentKids(true, 10, "PG-13", "TV-14", true)),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                request.url.encodedPath == "/api/profiles" ->
                    respond(Json.encodeToString(parentFixture(kids = true)), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond(Json.encodeToString(parentKids(true, 10)), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1")
        try {
            controller.refreshParentControls(); controller.job!!.join()
            assertEquals(parentRatings, controller.state.value.parentControls.getValue("2").policy.ratings)
            controller.changeKidsPolicy("2", KidsPolicy(true, 10, "PG-14")); controller.job!!.join()
            assertContains(controller.state.value.error.orEmpty(), "movie rating limits")
            assertEquals(0, writes.size)
            controller.changeKidsPolicy("2", KidsPolicy(true, 10, "PG-13", "TV-14", true)); controller.job!!.join()
            assertEquals("/api/profiles/2/kids" to kidsBody(true, 10, "PG-13", "TV-14", true), writes.single())
            val saved = controller.state.value.parentControls.getValue("2").policy
            assertEquals("PG-13", saved.movie_rating); assertEquals("TV-14", saved.series_rating)
            assertTrue(saved.allow_unrated)
            assertNull(controller.state.value.error)
        } finally { remote.close() }
    }

    @Test fun leavePinSetChangeAndRemoveSaveThroughTheProfilePinRoute() = runBlocking<Unit> {
        val writes = mutableListOf<Pair<String, JsonObject>>()
        var configured = false
        val remote = RemoteConnection("https://watch.example", store, client { request ->
            when {
                request.url.encodedPath == "/api/profiles" ->
                    respond(Json.encodeToString(parentFixture(leavePin = true)), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("/pin") && request.method == HttpMethod.Put -> {
                    val value = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    writes += request.url.encodedPath to value
                    configured = value["pin"]?.jsonPrimitive?.content?.isNotEmpty() == true
                    respond("{\"configured\":$configured}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                request.url.encodedPath.endsWith("/pin") ->
                    respond("{\"availability\":\"Available\",\"message\":null,\"configured\":$configured}",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond(Json.encodeToString(parentKids(true, 10)), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1")
        try {
            controller.refreshParentControls(); controller.job!!.join()
            assertEquals(false, controller.state.value.parentControls.getValue("2").leavePin)
            controller.changeProfileLeavePin("2", "", "1234"); controller.job!!.join()
            assertEquals("/api/profiles/2/pin" to buildJsonObject { put("pin", "1234") }, writes.last())
            assertEquals(true, controller.state.value.parentControls.getValue("2").leavePin)
            controller.changeProfileLeavePin("2", "", "5678"); controller.job!!.join()
            assertContains(controller.state.value.error.orEmpty(), "current PIN")
            assertEquals(1, writes.size)
            controller.changeProfileLeavePin("2", "1234", "5678"); controller.job!!.join()
            assertEquals("/api/profiles/2/pin" to buildJsonObject { put("pin", "5678"); put("current_pin", "1234") }, writes.last())
            controller.changeProfileLeavePin("2", "5678", ""); controller.job!!.join()
            assertEquals("/api/profiles/2/pin" to buildJsonObject { put("pin", ""); put("current_pin", "5678") }, writes.last())
            assertEquals(false, controller.state.value.parentControls.getValue("2").leavePin)
            assertNull(controller.state.value.error)
        } finally { remote.close() }
    }

    @Test fun leavePinSaveNeedsAServerThatOffersIt() = runBlocking<Unit> {
        var calls = 0
        val remote = RemoteConnection("https://watch.example", store, client { calls++; error("No request expected") }, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1", parentSettings = parentFixture())
        try {
            controller.changeProfileLeavePin("2", "", "1234"); controller.job!!.join()
            assertContains(controller.state.value.error.orEmpty(), "does not support")
            assertEquals(0, calls)
        } finally { remote.close() }
    }

    @Test fun parentSnapshotCannotCrossAnAccountChange(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val remote = RemoteConnection("https://watch.example", store, client {
            started.complete(Unit); release.await()
            respond(Json.encodeToString(parentFixture()), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, browserMode = true)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        try {
            controller.refreshParentControls(); started.await()
            controller.connection = null
            controller.mutable.value = ApplicationState()
            release.complete(Unit); controller.job!!.join()
            assertNull(controller.state.value.parentSettings)
            assertTrue(controller.state.value.profiles.isEmpty())
        } finally { release.complete(Unit); remote.close() }
    }
    @Test fun failedBootstrapAfterPolicyMutationCannotLeaveOldAdultContentVisible() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val remote = RemoteConnection("https://watch.example", store, client { request ->
            if (request.method == HttpMethod.Put) {
                started.complete(Unit); release.await()
                respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else respond("{}", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType, "application/json"))
        }, browserMode = true)
        val controller = ApplicationController(this, store); controller.connection = remote
        val adult = Card(MediaIdentity("adult", "movie", emptyList()), provider_id = "simkl", title = "Adult title", badges = emptyList())
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "2", parentSettings = parentFixture(),
            results = listOf(adult), library = listOf(adult), browseCards = listOf(adult))
        try {
            controller.changeKidsPolicy("2", KidsPolicy(true, 10)); started.await()
            assertTrue(controller.state.value.results.isEmpty())
            assertTrue(controller.state.value.library.isEmpty())
            assertTrue(controller.state.value.browseCards.isEmpty())
            release.complete(Unit); controller.job!!.join()
            assertTrue(controller.state.value.policyRefreshNeeded)
            assertTrue(controller.state.value.results.isEmpty())
            assertNotNull(controller.state.value.error)
        } finally { release.complete(Unit); remote.close() }
    }

    private fun kidsBody(enabled: Boolean, age: Int, movie: String, series: String, unrated: Boolean) = buildJsonObject {
        put("enabled", enabled); put("max_age", age); put("movie_rating", movie)
        put("series_rating", series); put("allow_unrated", unrated)
    }
}
