package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class SettingsSaveKeepsShellTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private val identity = AccountIdentity("account-1", "owner", "Owner", "owner")
    private val devices = AccountDevices(identity, listOf(AccountDevice("device-1", "Pixel", "phone", 1L, true, true)), can_sign_out_all = true)
    private val addon = IntegrationConnection("connection-1", "simkl", "account-1", "2", "Simkl", true, 3, emptyList())
    private val preferences = PreferencesSnapshot(4, ProfilePreferences(source_selection = "remember"))

    private fun client(handle: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(handle)) { configureTransport() }

    private fun bootstrapJson() = buildJsonObject {
        put("identity", buildJsonObject { put("profiles", JsonArray(emptyList())); put("profile_id", "2") })
        put("profile_management", Json.encodeToJsonElement(parentFixture(kids = true)))
        put("library", Json.encodeToJsonElement(CatalogPage(emptyList(), null, true)))
    }.toString()

    private fun remote(handle: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?)? = null) =
        RemoteConnection("https://watch.example", store, client { request ->
            val override = handle?.invoke(this, request)
            if (override != null) override else {
                val body = when {
                    request.method == HttpMethod.Put -> Json.encodeToString(parentKids(true, 10))
                    request.url.encodedPath == "/api/bootstrap" -> bootstrapJson()
                    request.url.encodedPath == "/api/profiles" -> Json.encodeToString(parentFixture(kids = true))
                    request.url.encodedPath.endsWith("/approvals") -> "{\"items\":[]}"
                    request.url.encodedPath.endsWith("/history") -> "[]"
                    request.url.encodedPath.endsWith("/pin") -> "{\"availability\":\"Unsupported\",\"configured\":false}"
                    else -> Json.encodeToString(parentKids(true, 10))
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }, browserMode = true)

    private fun shell(page: SettingsPage) = ApplicationState(
        screen = Screen.Settings,
        navBack = listOf(BackEntry(Screen.Home), BackEntry(Screen.History)),
        settingsPage = page,
        selectedProfile = "2",
        accountIdentity = identity,
        accountDevices = devices,
        addons = listOf(addon),
        preferences = preferences,
        profileManagement = parentFixture(kids = true),
        parentSettings = parentFixture(kids = true),
        parentControls = mapOf("2" to ParentProfileControls(parentKids(true, 10, "PG-13", "TV-14", false))))

    private fun assertShellKept(state: ApplicationState, page: SettingsPage? = state.settingsPage) {
        assertEquals(Screen.Settings, state.screen)
        assertEquals(page, state.settingsPage)
        assertEquals(listOf(Screen.Home, Screen.History), state.navBack.map { it.screen })
        assertEquals(identity, state.accountIdentity)
        assertEquals(devices, state.accountDevices)
        assertEquals(listOf(addon), state.addons)
        assertEquals(preferences, state.preferences)
        assertFalse(state.busy)
        assertFalse(state.mutations.running(MutationKind.Preference), "still pending: ${state.mutations.entries}")
    }

    @Test fun aProfilePageSaveKeepsBackToTheScreenThatOpenedSettings() = runBlocking {
        val remote = remote()
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = shell(SettingsPage("Profile", subpage = true))
        try {
            controller.saveProfileKids(parentKids(true), ""); controller.job!!.join()
            assertShellKept(controller.state.value, SettingsPage("Profile", subpage = true))
            controller.navigateBack()
            assertEquals(SettingsPage(), controller.state.value.settingsPage)
            controller.navigateBack(); controller.job!!.join()
            assertEquals(Screen.History, controller.state.value.screen, "Back left Settings for Home instead of the history it came from")
            assertEquals(listOf(Screen.Home), controller.state.value.navBack.map { it.screen })
        } finally { remote.close() }
    }

    @Test fun aParentalControlsSaveKeepsBackToTheScreenThatOpenedSettings() = runBlocking {
        val remote = remote()
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = shell(SettingsPage("Parental controls", subpage = true))
        try {
            controller.changeKidsPolicy("2", parentKids(true)); controller.job!!.join()
            assertShellKept(controller.state.value, SettingsPage("Parental controls", subpage = true))
            controller.navigateBack()
            controller.navigateBack(); controller.job!!.join()
            assertEquals(Screen.History, controller.state.value.screen)
        } finally { remote.close() }
    }

    @Test fun aSaveInFlightKeepsItsPendingControlAndTheAccountReads() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val remote = remote { request ->
            if (request.method == HttpMethod.Put) {
                started.complete(Unit); release.await()
                respond(Json.encodeToString(parentKids(true, 10)), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else null
        }
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = shell(SettingsPage("Profile", subpage = true))
        try {
            controller.changeKidsPolicy("2", parentKids(true)); started.await()
            val pending = controller.state.value
            assertTrue(pending.mutations.isPending(preferenceTarget(kidsPolicyTarget("2"))), "the control that is saving lost its pending state")
            assertEquals(identity, pending.accountIdentity)
            assertEquals(listOf(Screen.Home, Screen.History), pending.navBack.map { it.screen })
            release.complete(Unit); controller.job!!.join()
            assertShellKept(controller.state.value, SettingsPage("Profile", subpage = true))
        } finally { release.complete(Unit); remote.close() }
    }

    @Test fun everySettingsSnapshotApplicationKeepsTheShell() = runBlocking {
        val remotes = mutableListOf<RemoteConnection>()
        fun controller(page: SettingsPage): ApplicationController {
            val remote = remote().also(remotes::add)
            return ApplicationController(this, store).also {
                it.connection = remote
                it.mutable.value = shell(page)
            }
        }
        try {
            val refreshed = controller(SettingsPage("Parental controls", subpage = true))
            refreshed.mutable.value = refreshed.state.value.copy(policyRefreshNeeded = true)
            refreshed.refreshParentControls(); refreshed.job!!.join()
            assertShellKept(refreshed.state.value, SettingsPage("Parental controls", subpage = true))

            val restriction = controller(SettingsPage("Parental controls", subpage = true))
            restriction.changeKidsPolicy("2", parentKids(true)); restriction.job!!.join()
            assertShellKept(restriction.state.value, SettingsPage("Parental controls", subpage = true))

            val profile = controller(SettingsPage("Profile", subpage = true))
            profile.saveProfileKids(parentKids(true), ""); profile.job!!.join()
            assertShellKept(profile.state.value, SettingsPage("Profile", subpage = true))
        } finally { remotes.forEach { it.close() } }
    }

    @Test fun backAfterASaveReadsTheScreenItReturnsToAgain() = runBlocking {
        var historyReads = 0
        val remote = remote { request ->
            if (request.url.encodedPath.endsWith("/history")) {
                historyReads++
                respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else null
        }
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = shell(SettingsPage("Profile", subpage = true))
        try {
            controller.changeKidsPolicy("2", parentKids(true)); controller.job!!.join()
            controller.navigateBack()
            controller.navigateBack(); controller.job!!.join()
            assertEquals(Screen.History, controller.state.value.screen)
            assertEquals(1, historyReads, "Back drew the history the restriction change had dropped")
        } finally { remote.close() }
    }

    @Test fun theHomeRefreshAfterARestrictionChangeKeepsTheBackStackAndTheAccountReads() = runBlocking {
        val remote = remote()
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = shell(SettingsPage()).copy(screen = Screen.Home,
            navBack = listOf(BackEntry(Screen.Settings)), policyRefreshNeeded = true)
        try {
            controller.refresh(); controller.job!!.join()
            assertEquals(Screen.Home, controller.state.value.screen)
            assertEquals(listOf(Screen.Settings), controller.state.value.navBack.map { it.screen })
            assertEquals(identity, controller.state.value.accountIdentity)
            assertEquals(listOf(addon), controller.state.value.addons)
            assertFalse(controller.state.value.policyRefreshNeeded)
        } finally { remote.close() }
    }
}