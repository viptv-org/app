@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, coil3.annotation.DelicateCoilApi::class)

package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.features.details.TvEpisodeShelf
import org.viptv.app.images.ConfigureImageLoading
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

class TvEpisodeShelfTitleTest {
    @get:Rule val uiThread = UiThreadRule()

    private val title = "The Longest Running Television Series That Anyone Has Ever Made Keeps Going And Going Past The Second Line Until It Stops"
    private val overflowTitle = (1..40).joinToString(" ") { "chapter".plus(it) }

    @Test fun aDeadLogoKeepsTheTitleVisibleAndItNeverCutsAGlyph() = runBlocking {
        withDeadLogo { logo ->
            withScene(logo) { scene ->
                settle(scene)
                delay(250)
                assertTrue(logo.requests() > 0, "the logo must actually be requested to prove it is dead")
                val node = titleNode(scene, logo.card())
                val layout = layout(node)
                assertTrue(layout.lineCount in 1..2, "the title stays within two complete lines, got ${layout.lineCount}")
                assertLineFits(layout)
                scene.render(3_000_000_000L).use { shot ->
                    assertTrue(textInk(Bitmap.makeFromImage(shot), node) > 40, "the dead logo never blanks the title text")
                }
            }
        }
    }

    @Test fun aTitleBeyondTwoLinesShowsTwoCompleteLinesThenAnEllipsis() = runBlocking {
        withDeadLogo { logo ->
            logo.title = overflowTitle
            withScene(logo) { scene ->
                settle(scene)
                val layout = layout(titleNode(scene, logo.card()))
                assertEquals(2, layout.lineCount, "an over-long title is capped at two complete lines")
                assertTrue(layout.hasVisualOverflow, "the text really overflows into the ellipsis path")
                assertLineFits(layout)
            }
        }
    }

    @Test fun captureShelfTitleAtTvWidth() = runBlocking {
        for ((name, value) in listOf("tv-724-shelf-title" to title, "tv-724-shelf-title-ellipsis" to overflowTitle)) {
            withDeadLogo { logo ->
                logo.title = value
                withScene(logo) { scene ->
                    settle(scene)
                    System.getenv("VIPTV_SHELF_TITLE_CAPTURE_DIR")?.let { path ->
                        File(path).mkdirs()
                        scene.render(4_000_000_000L).use { image ->
                            image.encodeToData()!!.use { data -> File(path, "$name.png").writeBytes(data.bytes) }
                        }
                    }
                }
            }
        }
    }

    private class DeadLogo {
        var title: String = ""
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val hits = AtomicInteger()

        init {
            server.createContext("/") { exchange ->
                hits.incrementAndGet()
                exchange.sendResponseHeaders(500, -1)
                exchange.close()
            }
            server.start()
        }

        fun url() = "http://127.0.0.1:${server.address.port}/logo.png"
        fun requests() = hits.get()
        fun card() = Card(MediaIdentity("show", "series", emptyList()), provider_id = "library",
            title = title, badges = emptyList(), logo = url())
        fun stop() = server.stop(0)
    }

    private suspend fun withDeadLogo(block: suspend (DeadLogo) -> Unit) {
        val logo = DeadLogo().also { it.title = title }
        try { block(logo) } finally { logo.stop() }
    }

    private suspend fun withScene(logo: DeadLogo, block: suspend (ImageComposeScene) -> Unit) {
        val context = PlatformContext.INSTANCE
        val previous: ImageLoader? = runCatching { SingletonImageLoader.get(context) }.getOrNull()
        val card = logo.card()
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    ConfigureImageLoading()
                    TvEpisodeShelf(card, emptyList(), 1f, {}, {}, true, null, {}, paging = false)
                }
            }
        }
        try {
            block(scene)
        } finally {
            scene.close()
            runCatching { SingletonImageLoader.get(context).shutdown() }
            previous?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    private suspend fun settle(scene: ImageComposeScene) {
        repeat(30) { scene.render(it * 16_666_667L).close(); delay(8) }
    }

    private fun titleNode(scene: ImageComposeScene, card: Card): SemanticsNode = unmerged(scene).single {
        text(it) == card.title && it.config.contains(SemanticsActions.GetTextLayoutResult)
    }

    private fun layout(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
        return results.single()
    }

    private fun assertLineFits(layout: TextLayoutResult) {
        val last = layout.lineCount - 1
        assertTrue(layout.size.width + 0.5f >= layout.getLineRight(last),
            "the last line is truncated mid-word at right=${layout.getLineRight(last)} width=${layout.size.width}")
    }

    private fun textInk(bitmap: Bitmap, node: SemanticsNode): Int {
        val left = node.boundsInRoot.left.toInt().coerceAtLeast(0)
        val top = node.boundsInRoot.top.toInt().coerceAtLeast(0)
        val right = node.boundsInRoot.right.toInt().coerceAtMost(bitmap.width)
        val bottom = node.boundsInRoot.bottom.toInt().coerceAtMost(bitmap.height)
        var ink = 0
        for (y in top until bottom step 2) for (x in left until right step 2) {
            val c = bitmap.getColor(x, y)
            if ((c shr 16 and 0xFF) > 200 && (c shr 8 and 0xFF) > 200 && (c and 0xFF) > 200) ink++
        }
        return ink
    }

    private fun unmerged(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
}