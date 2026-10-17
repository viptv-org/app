package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import uniffi.viptv_core.SecretStore
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

class LocalProfileDeletionTest {
    @Test fun deletingSelectedLocalProfileClearsItsUiDataAndKeepsOtherProfilePreferences() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("viptv-profile-delete-controller-")
        val secrets = object : SecretStore {
            val values = ConcurrentHashMap<String, String>()
            override fun get(key: String) = values[key]
            override fun put(key: String, value: String) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
        }
        val local = NativeLocalConnection(directory.resolve("local.sqlite").toString(), secrets,
            HttpClient(MockEngine { error("Profile operations must not request a provider") }) { configureTransport() })
        val controller = ApplicationController(this)
        controller.local = local
        try {
            local.engine.createProfile("other", "Other")
            assertTrue(local.liveFavorites("default").isEmpty())
            assertTrue(local.liveFavorites("other").isEmpty())
            assertFails { local.setLiveFavorite("default", "foreign-channel", true) }
            assertFails { local.setLiveFavorite("default", "foreign-channel", false) }

            local.engine.setProfilePin("default", "", "1234")
            local.updateProfilePreferences("default", 0, ProfilePreferences(preferred_audio_language = "fr"))
            local.updateProfilePreferences("other", 0, ProfilePreferences(preferred_audio_language = "ja"))
            val card = Card(MediaIdentity("private-title", "movie", emptyList()), provider_id = "metadata", title = "Private title", badges = emptyList())
            controller.mutable.value = ApplicationState(screen = Screen.ProfileEdit, localMode = true, selectedProfile = "default", profileToEdit = "default",
                profiles = listOf(Profile("default", "Default", null, true), Profile("other", "Other", null, false)),
                results = listOf(card), library = listOf(card), detail = DetailSnapshot(card, episodes = emptyList()), sourceTitle = card,
                liveFavorites = setOf("private-channel"), preferences = PreferencesSnapshot(preferences = ProfilePreferences(preferred_audio_language = "fr")))
            controller.deleteProfile("9999"); controller.job!!.join()
            assertEquals("default", controller.state.value.selectedProfile); assertEquals(listOf(card), controller.state.value.results)
            assertNotNull(controller.state.value.error)
            controller.deleteProfile("1234"); controller.job!!.join()
            val state = controller.state.value
            assertNull(state.selectedProfile); assertEquals(Screen.Profiles, state.screen)
            assertTrue(state.results.isEmpty()); assertTrue(state.library.isEmpty()); assertTrue(state.liveFavorites.isEmpty())
            assertNull(state.preferences); assertNull(state.detail); assertNull(state.sourceTitle)
            assertEquals(listOf("other"), state.profiles.map { it.id })
            assertFails { local.liveFavorites("default") }
            assertEquals("ja", local.profilePreferences("other").preferences.preferred_audio_language)
        } finally { controller.job?.cancelAndJoin(); controller.libraryHydration?.cancelAndJoin(); local.close(); directory.toFile().deleteRecursively() }
    }
}
