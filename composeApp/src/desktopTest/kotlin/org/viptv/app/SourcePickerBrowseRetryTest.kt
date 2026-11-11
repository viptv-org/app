package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryCatalog
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.FeatureAvailability
import org.viptv.app.features.browse.BrowserBrowseScreen
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.Choice
import org.viptv.engine.wire.ControlKind
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.QueryControl
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SourcePickerBrowseRetryTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun page(width: Int, height: Int, television: Boolean = false, content: @Composable () -> Unit): ImageComposeScene =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television, pointer = true) {
            Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
        } }

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 30) =
        repeat(count) { scene.render(frame++ * 16_666_667L).close() }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }

    private fun label(node: SemanticsNode): String =
        (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")

    private fun texts(scene: ImageComposeScene): List<String> = nodes(scene).map(::label)

    private fun click(scene: ImageComposeScene, text: String) {
        val node = nodes(scene).first {
            it.config.contains(SemanticsActions.OnClick) && (label(it).contains(text) || it.children.any { child -> label(child).contains(text) })
        }
        assertTrue(node.config[SemanticsActions.OnClick].action?.invoke() == true, "$text is clickable")
        frames(scene)
    }

    private fun visible(scene: ImageComposeScene, text: String): Boolean = texts(scene).any { it.contains(text) }

    private val title = Card(MediaIdentity("tt1", "movie", emptyList()), provider_id = "addon:1", title = "Oak Street", badges = emptyList())

    private val genre = QueryControl("genre", "Genre", ControlKind.SINGLECHOICE, false,
        listOf(Choice("action", "Action"), Choice("drama", "Drama")))
    private val catalog = DiscoveryCatalog("top", "Movies", "movie", "c1", controls = listOf(genre), connection_name = "Cinemeta")
    private val controls = DiscoveryControls("stremio", listOf("movie"), listOf(genre), catalogs = listOf(catalog),
        discovery = FeatureAvailability("Available"), search = FeatureAvailability("Available"),
        filters = FeatureAvailability("Available"), calendar = FeatureAvailability("Unsupported"))

    @Test fun phoneSourcePickerFailureOffersTryAgain() {
        var retried = 0
        var gateways = 0
        val scene = page(390, 844) {
            SourcesScreen(title = title, sources = emptyList(), busy = false, error = "The server did not answer.",
                choose = {}, back = {}, errorCode = "gateway_required", openGateways = { gateways++ }, retry = { retried++ })
        }
        try {
            frames(scene)
            assertTrue(visible(scene, "Couldn’t load sources"), "a failed source picker shows its failure title")
            assertTrue(visible(scene, "The server did not answer."), "the reason is shown")
            assertTrue(visible(scene, "Open Gateways"), "the gateway action is kept on the failure")
            click(scene, "Try again")
            assertEquals(1, retried, "Try again re-runs discovery")
            click(scene, "Open Gateways")
            assertEquals(1, gateways, "Open Gateways still works")
        } finally { scene.close() }
    }

    @Test fun tvSourcePickerFailureOffersTryAgain() {
        var retried = 0
        val scene = page(1920, 1080, television = true) {
            SourcesScreen(title = title, sources = emptyList(), busy = false, error = "The server did not answer.",
                choose = {}, back = {}, television = true, retry = { retried++ })
        }
        try {
            frames(scene)
            assertTrue(visible(scene, "Couldn’t load sources"), "a failed TV source picker shows its failure title")
            assertTrue(visible(scene, "The server did not answer."), "the reason is shown")
            click(scene, "Try again")
            assertEquals(1, retried, "Try again re-runs discovery")
        } finally { scene.close() }
    }

    @Test fun webBrowseFailureOffersTryAgain() {
        var retried = 0
        // A second catalog of the same type is what makes "Choose another catalog" a real way out.
        val peers = controls.copy(catalogs = controls.catalogs + DiscoveryCatalog("new", "New Releases", "movie", "c1",
            controls = listOf(genre), connection_name = "Cinemeta"))
        val scene = page(1440, 900) {
            BrowserBrowseScreen(Screen.Movies, emptyList(), peers, BrowseQuery("movie", "top", "c1"),
                busy = false, error = "The server did not answer.", more = null, open = {}, changed = { _, _ -> },
                multipleChanged = { _, _ -> }, applyQuery = {}, retry = { retried++ })
        }
        try {
            frames(scene)
            assertTrue(visible(scene, "Couldn’t load Movies"), "a failed web Browse shows its failure title")
            assertTrue(visible(scene, "Choose another catalog"), "the catalog action is kept on the failure")
            click(scene, "Try again")
            assertEquals(1, retried, "Try again re-runs the load")
        } finally { scene.close() }
    }
}