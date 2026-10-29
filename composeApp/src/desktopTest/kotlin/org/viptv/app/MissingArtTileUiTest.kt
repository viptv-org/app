package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.features.home.MISSING_ART_TAG
import org.viptv.app.features.home.MediaCard
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class MissingArtTileUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private val posterUrl = "https://art.example/poster/tt0903747.jpg"
    private val stillUrl = "https://art.example/background/tt0903747.jpg"
    private val artless = Card(MediaIdentity("tt0903747", "series", emptyList()), provider_id = "library",
        title = "Imported Show", badges = emptyList(), poster = null, landscape = null)

    @Test fun aCardWithNoArtFillsItsFrameWithTheFilmIconAndTheTitle() = onEdt {
        for (portrait in listOf(false, true)) {
            val frame = if (portrait) "poster" else "landscape"
            withFakeArtwork { requested ->
                val shown = render(artless, portrait)
                assertTrue(requested.isEmpty(), "the $frame card has nothing to load: $requested")
                val tile = shown.nodes.single { it.tag == MISSING_ART_TAG }
                val bounds = tile.bounds
                assertEquals(shown.art.width, bounds.width, 1.5f, "the tile fills the $frame art's width")
                assertEquals(shown.art.height, bounds.height, 1.5f, "the tile fills the $frame art's height")
                assertEquals(shown.art.center.x, bounds.center.x, 1.5f, "the tile is centred on the $frame art")
                assertEquals(shown.art.center.y, bounds.center.y, 1.5f, "the tile is centred on the $frame art")
                assertEquals(artless.title, tile.description, "the art announces the title it now carries")
                val fill = Palette.raised.toArgb()
                assertEquals(fill, shown.pixel(shown.art.center.x.toInt(), shown.art.bottom.toInt() - 2), "the $frame tile is a surface-2 block")
                val bands = shown.inkBands(shown.art.deflate(12f), fill)
                assertTrue(bands.size >= 2, "the $frame tile draws a film icon and, under it, the title: $bands")
                assertTrue(bands.first().width < 40, "the top of the $frame tile is the film icon: $bands")
                assertTrue(bands.drop(1).any { it.width > bands.first().width * 2 }, "the title is wider than the $frame icon: $bands")
            }
        }
    }

    @Test fun aBlankArtUrlIsNoArt() = onEdt {
        withFakeArtwork { requested ->
            val shown = render(artless.copy(poster = "", landscape = "  "), portrait = false)
            assertTrue(requested.isEmpty(), "a blank URL is never loaded: $requested")
            assertEquals(1, shown.nodes.count { it.tag == MISSING_ART_TAG }, "a blank URL draws the tile")
        }
    }

    @Test fun aCardWithArtKeepsItsArtAndDropsTheTile() = onEdt {
        for ((kind, card) in listOf("poster" to artless.copy(poster = posterUrl),
            "still over its poster" to artless.copy(poster = posterUrl, landscape = stillUrl),
            "still" to artless.copy(landscape = stillUrl))) {
            withFakeArtwork { requested ->
                val shown = render(card, portrait = false)
                assertTrue(requested.isNotEmpty(), "the $kind is loaded: $requested")
                assertTrue(shown.nodes.none { it.tag == MISSING_ART_TAG }, "the $kind replaces the tile")
            }
        }
    }

    @Test fun aCardWhoseArtFailsToLoadFallsBackToTheTile() = onEdt {
        withFakeArtwork(fail = true) { requested ->
            for (card in listOf(artless.copy(poster = posterUrl, landscape = stillUrl),
                artless.copy(poster = posterUrl))) {
                val shown = render(card, portrait = false)
                assertTrue(requested.isNotEmpty(), "Coil tried the art: $requested")
                assertEquals(1, shown.nodes.count { it.tag == MISSING_ART_TAG },
                    "a failed image leaves the tile, not an empty block")
            }
        }
    }

    private class Shown(val art: Rect, val nodes: List<Node>, private val pixels: Bitmap) {
        fun pixel(x: Int, y: Int) = pixels.getColor(x, y)

        fun inkBands(rect: Rect, fill: Int): List<Band> {
            val ink = (rect.top.roundToInt() until rect.bottom.roundToInt()).map { y ->
                (rect.left.roundToInt() until rect.right.roundToInt()).filter { x -> pixels.getColor(x, y) != fill }
            }
            val bands = mutableListOf<MutableList<Int>>()
            ink.indices.filter { ink[it].isNotEmpty() }.forEach { y ->
                if (bands.isNotEmpty() && bands.last().last() == y - 1) bands.last() += y else bands += mutableListOf(y)
            }
            return bands.map { rows ->
                val columns = rows.flatMap { ink[it] }
                Band(columns.maxOrNull()!! - columns.minOrNull()!! + 1)
            }
        }
    }

    private class Node(val bounds: Rect, val tag: String?, val description: String)

    private class Band(val width: Int) {
        override fun toString() = "$width px wide"
    }

    private fun SemanticsNode.tag() = config.getOrNull(SemanticsProperties.TestTag)
    private fun SemanticsNode.description() = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()

    private suspend fun render(card: Card, portrait: Boolean): Shown {
        val height = if (portrait) ART * 3 / 2 else ART * 9 / 16
        val scene = ImageComposeScene(ART + 80, height + 80, Density(1f)) { ViptvTheme {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                MediaCard(card, ART, portrait = portrait, showLabels = false, open = {})
            }
        } }
        try {
            repeat(20) { scene.render(it * 16_666_667L).close(); delay(10) }
            scene.render(1_000_000_000L).use { image ->
                val pixels = Bitmap.makeFromImage(image)
                // Read the bounds while the scene is open: closing it detaches the tree, and a detached node reports Rect.Zero.
                val nodes = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
                    .map { Node(it.boundsInRoot, it.tag(), it.description()) }
                return Shown(Rect(0f, 0f, ART.toFloat(), height.toFloat()), nodes, pixels)
            }
        } finally { scene.close() }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)

    private suspend fun withFakeArtwork(fail: Boolean = false, block: suspend (requested: Set<String>) -> Unit) {
        val requested = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val context = PlatformContext.INSTANCE
        val previous = SingletonImageLoader.get(context)
        val loader = ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                val url = chain.request.data.toString(); requested += url
                if (fail) throw IOException("unreachable art")
                SuccessResult(Bitmap().apply { allocN32Pixels(160, 240); erase(artColor(url)) }.asImage(),
                    chain.request, DataSource.MEMORY) as ImageResult
            })
        }.build()
        SingletonImageLoader.setUnsafe(loader)
        try { block(requested) } finally { loader.shutdown(); SingletonImageLoader.setUnsafe(previous) }
    }

    private companion object {
        const val ART = 240
        fun artColor(url: String) = (0xFF shl 24) or (url.hashCode() and 0x7F7F7F) or 0x202020
    }
}