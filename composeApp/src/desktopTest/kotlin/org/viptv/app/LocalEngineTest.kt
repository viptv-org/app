package org.viptv.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import org.viptv.engine.wire.ProfileConfiguration
import uniffi.viptv_core.LocalEngine

class LocalEngineTest {
    @Test
    fun nativeBridgePersistsProfilesAcrossReopen() {
        val directory = Files.createTempDirectory("viptv-engine-test")
        try {
            val path = directory.resolve("local.sqlite").toString()
            LocalEngine(path).use { engine -> engine.createProfile("second", "Second") }
            LocalEngine(path).use { engine ->
                val profiles = Json.decodeFromString<List<ProfileConfiguration>>(engine.profiles())
                assertEquals(setOf("default", "second"), profiles.map { it.scope.profile_id }.toSet())
                assertEquals("Second", profiles.first { it.scope.profile_id == "second" }.name)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
