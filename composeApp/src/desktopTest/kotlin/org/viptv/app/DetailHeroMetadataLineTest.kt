package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.CardMetadata
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.MediaIdentity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailHeroMetadataLineTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val movie = Card(MediaIdentity("oak-street", "movie", emptyList()), provider_id = "addon:21",
        title = "The End of Oak Street", badges = listOf("4K"),
        landscape = "https://images.example/oak-street.jpg", poster = "https://images.example/oak-street.jpg",
        description = "After a cosmic event lifts their street out of the suburbs, the Platt family learns what their neighbours were hiding.",
        metadata = CardMetadata(year = 2026, runtime_minutes = 100, genres = listOf("Drama", "Mystery"), rating = 8.0))
    private val detail = DetailSnapshot(movie, episodes = emptyList())

    private val facts = listOf("2026", "1h 40m", "Drama / Mystery", "★ 8.0")

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Page(width: Int, height: Int, val tablet: Boolean = false, val browser: Boolean = false,
        val television: Boolean = false) : AutoCloseable {
        var detail by mutableStateOf(this@DetailHeroMetadataLineTest.detail)
        private var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, television = television,
                    tablet = tablet, browser = browser, continuing = emptyList())
            }
        } }

        suspend fun settle() {
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
            delay(200)
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
        }

        fun texts(): List<String> {
            val acc = mutableListOf<String>()
            fun visit(node: SemanticsNode) {
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().forEach { acc += it.text }
                node.children.forEach(::visit)
            }
            scene.semanticsOwners.forEach { visit(it.rootSemanticsNode) }
            return acc
        }
        override fun close() = scene.close()
    }

    private val layouts = listOf<Pair<String, () -> Page>>(
        "phone" to { Page(390, 844) },
        "tablet" to { Page(1280, 800, tablet = true) },
        "desktop" to { Page(1280, 800) },
        "browser" to { Page(1280, 900, browser = true) },
        "tv" to { Page(1920, 1080, television = true) },
    )

    @Test fun everyHeroDrawsTheSameMetadataFacts() = onEdt {
        for ((name, layout) in layouts) layout().use { page ->
            page.settle()
            // The hero may draw the line as one text node or as parts between separators; both must carry the same facts.
            val drawn = page.texts().joinToString(" ")
            for (fact in facts) assertTrue(fact in drawn, "$name: the metadata line reads \"$fact\": $drawn")
            assertTrue("IMDb" !in drawn, "$name: no rating reads IMDb: $drawn")
            assertTrue("Drama, Mystery" !in drawn, "$name: genres join with a slash, not a comma: $drawn")
        }
    }

    @Test fun noScreenBuildsItsOwnRatingOrGenreLine() {
        val root = File("src/commonMain/kotlin/org/viptv/app/features")
        assertTrue(root.isDirectory, "run from the composeApp module")
        val handmade = Regex(""""IMDb |genres.*joinToString\(", "\)""")
        val found = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> file.readLines().withIndex().filter { handmade.containsMatchIn(it.value) }
                .map { "${file.name}:${it.index + 1}" } }
            .toList()
        assertEquals(emptyList(), found, "the metadata line is built by metadataParts()/heroMetadataParts()")
    }
}