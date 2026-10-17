package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import uniffi.viptv_core.SecretStore
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

class LocalConnectionRestoreTest {
    private class Vault : SecretStore {
        val values = ConcurrentHashMap<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }
    private fun device(path: String, vault: Vault) = NativeLocalConnection(path, vault,
        HttpClient(MockEngine { error("Local startup must not perform provider HTTP") }) { configureTransport() })

    @Test fun enteringLocalModeFinishesRememberingBeforePublishingAnInteractiveProfilePicker() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("viptv-local-commit-"); val path = dir.resolve("local.sqlite").toString()
        val vault = Vault(); val nativeStore = NativeRemoteSessionStore { vault }
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val store = object : RemoteSessionStore by nativeStore {
            override suspend fun rememberLocalConnection() {
                entered.complete(Unit); release.await(); nativeStore.rememberLocalConnection()
            }
        }
        val controller = ApplicationController(this, store, localFactory = { device(it, vault) })
        try {
            controller.useLocal(path); val entering = requireNotNull(controller.job)
            withTimeout(2000) { entered.await() }
            assertTrue(controller.state.value.busy, "A fast profile click must not cancel saving the local connection")
            assertEquals(Screen.Connect, controller.state.value.screen)
            controller.selectProfile("default")
            assertSame(entering, controller.job)
            release.complete(Unit); entering.join()
            assertEquals(SavedConnection.Local, nativeStore.savedConnection())
            assertEquals(Screen.Profiles, controller.state.value.screen)
            assertFalse(controller.state.value.busy)
        } finally { release.complete(Unit); controller.job?.cancelAndJoin(); controller.disconnect(); dir.toFile().deleteRecursively() }
    }

    @Test fun oneVaultRecordRetainsLegacyRemoteSessionsAndAtomicallySelectsLocalOrRemote() = runBlocking {
        val vault = Vault()
        vault.values["remote-session-v1"] = """{"origin":"https://fixture.example","refreshToken":"legacy-r1"}"""
        val first = NativeRemoteSessionStore { vault }
        assertEquals("legacy-r1", first.read()?.refreshToken)
        first.rememberLocalConnection()
        val next = NativeRemoteSessionStore { vault }
        assertEquals(SavedConnection.Local, next.savedConnection()); assertNull(next.read())
        assertEquals(1, vault.values.size)
        next.save(SavedRemoteSession("https://next.example", "r2"))
        assertEquals(SavedConnection.Remote(SavedRemoteSession("https://next.example", "r2")), first.savedConnection())
        first.clear(); assertEquals(SavedConnection.None, next.savedConnection())
    }

    @Test fun coldLocalRestoreKeepsDatabaseAndPinGateWithoutRememberingPrivateProfileSelection() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("viptv-local-restore-"); val path = dir.resolve("local.sqlite").toString()
        val vault = Vault(); val store = NativeRemoteSessionStore { vault }
        fun controller() = ApplicationController(this, store, localFactory = { device(it, vault) },
            remoteFactory = { _, _ -> error("Remembered local mode opened a remote connection") })
        val first = controller(); var second: ApplicationController? = null
        try {
            first.useLocal(path); requireNotNull(first.job).join()
            assertEquals(SavedConnection.Local, store.savedConnection())
            val local = requireNotNull(first.local)
            local.setProfilePin("default", "", "1234")
            local.updateProfilePreferences("default", 0, ProfilePreferences(preferred_audio_language = "fr"))
            first.disconnect()
            assertEquals(SavedConnection.Local, store.savedConnection(), "Activity/controller disposal must not forget local mode")
            second = controller().also { it.restore(path); requireNotNull(it.job).join() }
            assertEquals(Screen.Profiles, second.state.value.screen)
            assertTrue(second.state.value.localMode)
            assertTrue(second.state.value.profiles.single().locked)
            assertNull(second.state.value.selectedProfile); assertNull(second.state.value.home)
            assertEquals("fr", requireNotNull(second.local).profilePreferences("default").preferences.preferred_audio_language)
            second.selectProfile("default")
            assertEquals(Screen.ProfilePin, second.state.value.screen)
            assertNull(second.state.value.selectedProfile)
            second.leaveLocal(); requireNotNull(second.job).join()
            assertEquals(Screen.Connect, second.state.value.screen)
            assertEquals(SavedConnection.None, store.savedConnection())
            assertTrue(Files.exists(dir.resolve("local.sqlite")), "Leaving a connection must not delete its data")
        } finally { first.disconnect(); second?.disconnect(); dir.toFile().deleteRecursively() }
    }

    @Test fun canceledLocalBootstrapCannotRevealLateProfiles() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("viptv-local-cancel-"); val path = dir.resolve("local.sqlite").toString()
        val vault = Vault(); val store = NativeRemoteSessionStore { vault }; store.rememberLocalConnection()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val native = device(path, vault)
        val slow = object : LocalConnection by native {
            override suspend fun bootstrap(profile: String?): Bootstrap {
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                return Bootstrap(listOf(Profile("private", "Private", null)), null, null)
            }
        }
        val controller = ApplicationController(this, store, localFactory = { slow })
        try {
            controller.restore(path); val restoring = requireNotNull(controller.job)
            withTimeout(2000) { entered.await() }
            controller.disconnect(); release.complete(Unit); restoring.join()
            assertEquals(Screen.Connect, controller.state.value.screen)
            assertTrue(controller.state.value.profiles.isEmpty()); assertNull(controller.local)
            assertEquals(SavedConnection.Local, store.savedConnection())
        } finally { release.complete(Unit); controller.disconnect(); native.close(); dir.toFile().deleteRecursively() }
    }
}
