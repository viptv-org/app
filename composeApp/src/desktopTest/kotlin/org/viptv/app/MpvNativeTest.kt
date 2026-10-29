package org.viptv.app

import com.sun.jna.Memory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.mpv.*
import kotlin.test.*

class MpvNativeTest {
    @Test fun createsAClientEvenWhenTheProcessNumericLocaleIsNotC() {
        runBlocking {
            // The JVM starts with setlocale(LC_ALL, ""), where libmpv refuses to create a client.
            val original = MpvLocale.numericLocale()
            val pathological = listOf("C.utf8", "C.UTF-8", "en_US.utf8", "en_US.UTF-8")
                .firstOrNull { MpvLocale.setNumericLocale(it) }
            try {
                pathological?.let { assertEquals(it, MpvLocale.numericLocale()) }
                val session = MpvSession(PlaybackInput("owned", "about:blank"))
                try {
                    assertEquals("C", MpvLocale.numericLocale())
                    assertNotNull(session.handle)
                } finally {
                    session.closeAfterRenderer()
                    withTimeout(5_000) { session.closed.await() }
                }
            } finally {
                original?.let { MpvLocale.setNumericLocale(it) }
            }
        }
    }

    @Test fun nativeNodeHeadersPreserveCommasQuotesAndUtf8WithoutOptionStringSplitting() = runBlocking {
        val headers = mapOf("Authorization" to "Bearer test,with,commas", "X-Fixture" to "quoted \"résumé\"")
        val session = MpvSession(PlaybackInput("owned", "about:blank", headers))
        try {
            Memory(16).use { node ->
                node.clear()
                mpvCheck(session.api.mpv_get_property(session.handle, "http-header-fields", 6, node))
                try {
                    assertEquals(headers.map { (k, v) -> "$k: $v" }, mpvNode(node).jsonArray.map { it.jsonPrimitive.content })
                } finally { session.api.mpv_free_node_contents(node) }
            }
        } finally { session.closeAfterRenderer(); withTimeout(5_000) { session.closed.await() } }
    }

    @Test fun rejectHeaderFramingAndNativeStringTruncationBeforeOpeningMedia() {
        for (headers in listOf(mapOf("X-Invalid\u0000" to "x"), mapOf("X:Invalid" to "x"),
            mapOf("X-Test" to "value\r\nInjected: true"), mapOf("X-Test" to "value\u0000suffix"))) {
            assertFailsWith<IllegalArgumentException> { MpvSession(PlaybackInput("owned", "about:blank", headers)) }
        }
    }
}
