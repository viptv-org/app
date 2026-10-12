package org.viptv.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import org.viptv.engine.wire.ProfileConfiguration
import uniffi.viptv_core.LocalEngine
import uniffi.viptv_core.SecretStore

class LocalEngineTest {
    private class MemorySecrets : SecretStore {
        private val values = mutableMapOf<String,String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }
    @Test
    fun nativeBridgePersistsProfilesAcrossReopen() {
        val directory = Files.createTempDirectory("viptv-engine-test")
        try {
            val path = directory.resolve("local.sqlite").toString()
            val secrets=MemorySecrets()
            LocalEngine(path, secrets).use { engine -> engine.createProfile("second", "Second") }
            LocalEngine(path, secrets).use { engine ->
                val profiles = Json.decodeFromString<List<ProfileConfiguration>>(engine.profiles())
                assertEquals(setOf("default", "second"), profiles.map { it.scope.profile_id }.toSet())
                assertEquals("Second", profiles.first { it.scope.profile_id == "second" }.name)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun nativeBridgeCallsKotlinSecretStoreAndProjectsCatalog() {
        val directory = Files.createTempDirectory("viptv-addon-test")
        try {
            LocalEngine(directory.resolve("local.sqlite").toString(), MemorySecrets()).use { engine ->
                engine.installAddon("default", "https://example.com/manifest.json", """{"id":"test","name":"Movies","version":"1","resources":["catalog","meta"],"catalogs":[{"id":"top","type":"movie","name":"Popular"}]}""")
                val plan = Json.parseToJsonElement(engine.planHome("default")).jsonObject
                val request = plan.getValue("requests").jsonArray.single().jsonObject
                assertEquals("https://example.com/catalog/movie/top.json", request.getValue("url").jsonPrimitive.content)
                val result = buildJsonArray { add(buildJsonObject { put("id",request.getValue("id").jsonPrimitive.content);put("body","""{"metas":[{"id":"tt123","type":"movie","name":"Film"}]}""") }) }
                val home = Json.parseToJsonElement(engine.projectHome(plan.getValue("id").jsonPrimitive.content,result.toString())).jsonObject
                assertEquals("Film",home.getValue("rows").jsonArray.single().jsonObject.getValue("cards").jsonArray.single().jsonObject.getValue("title").jsonPrimitive.content)
            }
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test
    fun historyAndResumeSurviveNativeBridgeReopen() {
        val directory=Files.createTempDirectory("viptv-history-test")
        val path=directory.resolve("local.sqlite").toString()
        val card="""{"identity":{"id":"fixture-film","kind":"movie","ids":[],"episode":null,"edition":null},"provider_id":"addon:fixture","title":"Film","subtitle":null,"description":null,"poster":null,"landscape":null,"logo":null,"progress":null,"badges":[]}"""
        try {
            LocalEngine(path,MemorySecrets()).use { engine ->
                engine.createProfile("second","Second")
                engine.recordProgress("default",0uL,card,30_000uL,100_000uL)
                val home=Json.parseToJsonElement(engine.home("default")).jsonObject
                assertEquals("Continue Watching",home.getValue("rows").jsonArray.single().jsonObject.getValue("title").jsonPrimitive.content)
                assertEquals("2m left",home.getValue("rows").jsonArray.single().jsonObject.getValue("cards").jsonArray.single().jsonObject.getValue("subtitle").jsonPrimitive.content)
                assertEquals(0,Json.parseToJsonElement(engine.history("second")).jsonArray.size)
            }
            LocalEngine(path,MemorySecrets()).use { engine ->
                assertEquals(1,Json.parseToJsonElement(engine.history("default")).jsonArray.size)
                engine.recordProgress("default",0uL,card,97_000uL,100_000uL)
                assertEquals(0,Json.parseToJsonElement(engine.home("default")).jsonObject.getValue("rows").jsonArray.size)
            }
        } finally { directory.toFile().deleteRecursively() }
    }
}
