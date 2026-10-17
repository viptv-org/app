package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import kotlin.test.*

class RemoteProfilesTest {
    private class Store : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    @Test fun managementUsesTypedProfilesAndNeverReplacesAccountPinWithProfilePin() = runBlocking {
        val writes = mutableListOf<Pair<String, JsonObject>>()
        val allowed = """{"availability":"Available","message":null}"""
        val unsupported = """{"availability":"Unsupported","message":"Account parent PINs apply"}"""
        val management = """{"profiles":[{"id":"1","name":"Parent","avatar_id":"vynxc","is_primary":true,"can_delete":false,"kids":true,"max_age":10}],"capabilities":{"create":$allowed,"rename":$allowed,"delete":$allowed,"avatar":$allowed,"profile_pin":$unsupported,"parent_unlock":$allowed},"parent":{"pin_configured":true,"unlocked":true,"restricted":false}}"""
        val client = HttpClient(MockEngine { request ->
            if (request.method in listOf(HttpMethod.Patch, HttpMethod.Post))
                writes += request.url.encodedPath to Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond(if (request.method == HttpMethod.Get) management else "{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        val profiles = remote.profileManagement()
        assertTrue(profiles.profiles.single().kids)
        assertEquals(10, profiles.profiles.single().max_age)
        assertTrue(profiles.canManage)
        assertFalse(profiles.cards().single().canDelete)
        assertFalse(profiles.cards().single().locked)
        assertEquals("Unsupported", profiles.capabilities.profile_pin.availability)
        remote.renameProfile("1", "Updated")
        remote.setProfileAvatar("1", "initial-blue")
        remote.unlockParent("1234")
        assertEquals(listOf("/api/profiles/1", "/api/profiles/1", "/api/parent/unlock"), writes.map { it.first })
        assertEquals(setOf("name"), writes[0].second.keys)
        assertEquals(setOf("avatar_id"), writes[1].second.keys)
        assertEquals(setOf("pin"), writes[2].second.keys)
        remote.close()
    }
}
