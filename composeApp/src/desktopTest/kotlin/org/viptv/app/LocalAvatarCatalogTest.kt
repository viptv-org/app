package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import org.viptv.app.features.entry.AvatarPick
import org.viptv.app.features.entry.avatarPick
import uniffi.viptv_core.SecretStore
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

class LocalAvatarCatalogTest {
    @Test fun localProfilesStoreCatalogPicksBehindTheirPin() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("viptv-local-avatar-")
        val secrets = object : SecretStore {
            val values = ConcurrentHashMap<String, String>()
            override fun get(key: String) = values[key]
            override fun put(key: String, value: String) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
        }
        val local = NativeLocalConnection(directory.resolve("local.sqlite").toString(), secrets,
            HttpClient(MockEngine { error("Profile operations must not request a provider") }) { configureTransport() })
        try {
            local.engine.createProfile("kid", "Kid")
            suspend fun profile(id: String) = local.bootstrap().profiles.single { it.id == id }
            assertEquals("critters", profile("kid").avatarStyle)
            assertNull(profile("kid").avatarChoice)
            assertEquals(AvatarPick("critters", 1), profile("kid").avatarPick())

            local.setProfileAvatar("kid", "princesses", 3, "")
            assertEquals(AvatarPick("princesses", 3), profile("kid").avatarPick())
            assertNull(profile("kid").avatarId)

            assertFailsWith<LocalFailure> { local.setProfileAvatar("kid", "critters", 49, "") }
            assertFailsWith<LocalFailure> { local.setProfileAvatar("kid", "unknown", 1, "") }

            local.engine.setProfilePin("kid", "", "1234")
            assertFailsWith<LocalFailure> { local.setProfileAvatar("kid", "clay", 9, "") }
            local.setProfileAvatar("kid", "clay", 9, "1234")
            assertEquals(AvatarPick("clay", 9), profile("kid").avatarPick())
        } finally {
            local.close()
            directory.toFile().deleteRecursively()
        }
    }
}
