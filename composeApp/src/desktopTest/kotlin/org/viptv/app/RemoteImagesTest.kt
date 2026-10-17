package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import kotlin.test.*

class RemoteImagesTest {
    @Test fun negotiatedImagesUseTheConnectedOriginWithoutChangingExternalAssets() = runBlocking {
        for (browser in listOf(false, true)) {
            val client = HttpClient(MockEngine { request ->
                assertEquals(if (browser) "proxy" else "direct", request.headers["x-viptv-image-delivery"])
                respond("""{"cards":[{"logo":"/api/images/simkl/posters/hash_m.webp","poster":"https://cdn.example/poster.webp"}],"subtitle":"/api/subtitle-content/id"}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureTransport() }
            val store = object : RemoteSessionStore {
                override suspend fun read(): SavedRemoteSession? = null
                override suspend fun save(session: SavedRemoteSession) = Unit
                override suspend fun clear() = Unit
            }
            val connection = RemoteConnection("https://192.168.88.14:18451", store, client, browserMode = browser)
            val result = connection.call("/api/example").jsonObject
            val card = result.getValue("cards").jsonArray.single().jsonObject
            assertEquals("https://192.168.88.14:18451/api/images/simkl/posters/hash_m.webp", card.getValue("logo").jsonPrimitive.content)
            assertEquals("https://cdn.example/poster.webp", card.getValue("poster").jsonPrimitive.content)
            assertEquals("/api/subtitle-content/id", result.getValue("subtitle").jsonPrimitive.content)
            connection.close()
        }
    }
}
