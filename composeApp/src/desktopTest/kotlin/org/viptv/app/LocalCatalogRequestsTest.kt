package org.viptv.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.fetchLocalCatalogRows
import kotlin.test.*

class LocalCatalogRequestsTest {
    @Test fun queuedRequestsShareOneDeadlineInsteadOfSerialTimeoutWaves() = runBlocking {
        val requests = JsonArray((0 until 12).map { buildJsonObject {
            put("id", "row-$it"); put("url", "https://example.invalid/$it")
        } })
        val results = kotlinx.coroutines.withTimeout(1000) {
            fetchLocalCatalogRows(requests, timeoutMillis = 150) { kotlinx.coroutines.awaitCancellation() }
        }
        assertEquals(12, results.size)
        assertTrue(results.all { "body" !in it.jsonObject })
    }

    @Test
    fun failedSharedRequestKeepsEveryFailedRow() = runBlocking {
        val requests = Json.parseToJsonElement("""[{"id":"one","url":"https://example.invalid/fail"},{"id":"two","url":"https://example.invalid/fail"}]""").jsonArray
        var calls = 0
        val results = fetchLocalCatalogRows(requests) { calls++; throw IllegalStateException("fixture failure") }
        assertEquals(1, calls)
        assertEquals(2, results.size)
        assertTrue(results.all { !it.jsonObject.containsKey("body") })
    }

    @Test
    fun cancellationIsNotConvertedToFailedRows() {
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            runBlocking {
                val requests = Json.parseToJsonElement("""[{"id":"one","url":"https://example.invalid/cancel"}]""").jsonArray
                fetchLocalCatalogRows(requests) { throw kotlinx.coroutines.CancellationException("fixture cancellation") }
            }
        }
    }

    @Test
    fun identicalUrlsFetchOnceAndKeepIndependentRowIds() = runBlocking {
        val requests = Json.parseToJsonElement("""[
            {"id":"first","url":"https://example.invalid/catalog"},
            {"id":"second","url":"https://example.invalid/catalog"},
            {"id":"other","url":"https://example.invalid/other"},
            {"id":"unavailable","url":null}
        ]""").jsonArray
        val calls = mutableListOf<String>()
        val results = fetchLocalCatalogRows(requests) { url -> calls += url; "body:$url" }
        assertEquals(2, calls.size)
        assertEquals(1, calls.count { it.endsWith("/catalog") })
        assertEquals(listOf("first", "second", "other", "unavailable"), results.map { it.jsonObject.getValue("id").jsonPrimitive.content })
        assertEquals(results[0].jsonObject["body"], results[1].jsonObject["body"])
        assertFalse(results[3].jsonObject.containsKey("body"))
    }
}
