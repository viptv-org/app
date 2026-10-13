package org.viptv.app

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*
import uniffi.viptv_core.LocalEngine
import uniffi.viptv_core.SecretStore

/** Explicit packaging diagnostic; uses its own temporary database and never opens a user vault. */
internal fun verifyPackagedLocalEngine() {
    val directory = Files.createTempDirectory("viptv-package-smoke-")
    try {
        val secrets = object : SecretStore {
            private val entries = mutableMapOf<String, String>()
            override fun get(key: String) = entries[key]
            override fun put(key: String, value: String) { entries[key] = value }
            override fun remove(key: String) { entries.remove(key) }
        }
        val database = directory.resolve("local.sqlite").toString()
        LocalEngine(database, secrets).use { engine ->
            engine.createProfile("smoke", "Packaging check")
            engine.updateProfilePreferences("smoke", 0uL, """{"preferred_audio_language":"fr","preferred_subtitle_language":null,"subtitles_enabled":true,"source_selection":"ask","max_quality_height":null}""")
        }
        LocalEngine(database, secrets).use { engine ->
            check(Json.parseToJsonElement(engine.profileCards()).jsonArray.size == 2)
            val schema = Json.parseToJsonElement(engine.sourceIntegrationSchemas("smoke")).jsonObject
            check(schema.getValue("schemas").jsonArray.isNotEmpty())
            check(Json.parseToJsonElement(engine.profilePreferences("smoke")).jsonObject
                .getValue("preferences").jsonObject.getValue("preferred_audio_language").jsonPrimitive.content == "fr")
            check(Json.parseToJsonElement(engine.rankSources("smoke", "[]")).jsonArray.isEmpty())
            val live = Json.parseToJsonElement(engine.liveChannels("smoke", "{}")).jsonObject
            check(live.getValue("channels").jsonArray.isEmpty())
            check(live.getValue("availability").jsonPrimitive.content == "NeedsConfiguration")
        }
        System.getenv("VIPTV_ENGINE_SMOKE_RESULT")?.let { Files.writeString(Path.of(it), "verified-local-engine\n") }
    } finally { directory.toFile().deleteRecursively() }
}
