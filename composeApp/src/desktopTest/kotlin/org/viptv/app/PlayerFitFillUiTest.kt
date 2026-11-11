package org.viptv.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import org.viptv.app.features.player.*
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlayerFitFillUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(number: Long) = Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        metadata = CardMetadata(runtime_minutes = 52), provider_id = "addon:7", title = "Episode $number", badges = emptyList(),
        parent_identity = parent, metadata_origin = origin)

    @Test fun fitFillTogglesNameAndStateWhileKeepingEveryPhoneActionReachable() {
        for ((width, height) in listOf(320 to 568, 390 to 844)) {
            val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
                PlayerScreen(null, true, null, {}, {}, {}, title = episode(1), phone = true)
            } }
            try {
                frames(scene)
                assertEquals(ToggleableState.Off, toggleState(scene, "Fill video"), "$width×$height starts Fit")
                click(scene, "Fill video"); frames(scene)
                assertEquals(ToggleableState.On, toggleState(scene, "Fit video"), "$width×$height toggles to Fill")
                assertNull(fitFillOrNull(scene, "Fill video"))
                nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }.forEach {
                    assertTrue(it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= width, "$width×$height: phone action overflows with Fill")
                }
                click(scene, "Fit video"); frames(scene)
                assertEquals(ToggleableState.Off, toggleState(scene, "Fill video"), "$width×$height toggles back to Fit")
            } finally { scene.close() }
        }
    }

    @Test fun fillPersistsAcrossSourceSwapButResetsForANewEpisode() {
        var key by mutableStateOf("source-a")
        var current by mutableStateOf(episode(1))
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = current, phone = true, playbackKey = key)
        } }
        try {
            frames(scene)
            click(scene, "Fill video"); frames(scene)
            assertEquals(ToggleableState.On, toggleState(scene, "Fit video"))
            key = "source-b"; frames(scene)
            assertEquals(ToggleableState.On, toggleState(scene, "Fit video"), "A same-item source swap keeps Fill")
            current = episode(2); frames(scene)
            assertEquals(ToggleableState.Off, toggleState(scene, "Fill video"), "A new episode resets to Fit")
        } finally { scene.close() }
    }

    @Test fun theToggleReadsItsModeInWordsBesideFullscreenAndAfterLockOnPhone() {
        for ((width, height, phone) in listOf(Triple(1280, 900, false), Triple(1440, 900, false), Triple(390, 844, true))) {
            val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
                PlayerScreen(null, true, null, {}, {}, {}, title = episode(1), phone = phone, toggleFullscreen = {},
                    tvExtras = TvPlaybackExtras(episodes = listOf(episode(1)), nextEpisode = episode(2)), playEpisode = {})
            } }
            try {
                frames(scene)
                assertEquals("Fit", text(fitFill(scene, "Fill video")), "$width×$height: the toggle reads its mode")
                click(scene, "Fill video"); frames(scene)
                assertEquals("Fill", text(fitFill(scene, "Fit video")), "$width×$height: the toggle follows the mode")
                if (phone) {
                    val fit = fitFill(scene, "Fit video").boundsInRoot
                    val lock = assertNotNull(controlText(scene, "Lock"), "$width×$height: no Lock").boundsInRoot
                    assertTrue(lock.right <= fit.left, "$width×$height: Lock must sit before Fit/Fill: $lock vs $fit")
                    assertTrue(leftNeighbour(scene, fit) == lock, "$width×$height: Fit/Fill follows Lock in the tools row")
                    val next = assertNotNull(controlText(scene, "Next episode"), "$width×$height: no Next").boundsInRoot
                    assertTrue(fit.right <= next.left, "$width×$height: Fit/Fill must sit before Next episode")
                } else {
                    val fit = fitFill(scene, "Fit video").boundsInRoot
                    val fullscreen = assertNotNull(nodes(scene).firstOrNull {
                        it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d == "Enter fullscreen" } == true &&
                            it.config.contains(SemanticsActions.OnClick) }, "$width×$height: no Fullscreen").boundsInRoot
                    assertTrue(fit.right <= fullscreen.left, "$width×$height: Fit/Fill must sit before Fullscreen")
                    assertEquals(fit, leftNeighbour(scene, fullscreen), "$width×$height: Fullscreen follows Fit/Fill")
                }
            } finally { scene.close() }
        }
    }

    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun controlText(scene: ImageComposeScene, value: String) = nodes(scene).firstOrNull {
        it.config.contains(SemanticsActions.OnClick) && text(it) == value
    }

    private fun leftNeighbour(scene: ImageComposeScene, bounds: androidx.compose.ui.geometry.Rect) = nodes(scene)
        .filter { it.config.contains(SemanticsActions.OnClick) && it.boundsInRoot.right <= bounds.left && it.boundsInRoot.width < 4000f }
        .maxByOrNull { it.boundsInRoot.right }!!.boundsInRoot

    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun toggleState(scene: ImageComposeScene, label: String) = fitFill(scene, label).config.getOrNull(SemanticsProperties.ToggleableState)
    private fun fitFill(scene: ImageComposeScene, label: String) = nodes(scene).single {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d == label } == true &&
            it.config.contains(SemanticsActions.OnClick)
    }
    private fun fitFillOrNull(scene: ImageComposeScene, label: String) = nodes(scene).firstOrNull {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d == label } == true &&
            it.config.contains(SemanticsActions.OnClick)
    }
    private fun click(scene: ImageComposeScene, label: String) {
        val node = fitFill(scene, label)
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
}