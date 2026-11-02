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
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(number: Long) = Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        metadata = CardMetadata(runtime_minutes = 52), provider_id = "addon:7", title = "Episode $number", badges = emptyList(),
        parent_identity = parent, metadata_origin = origin)

    @Test fun fitFillTogglesNameAndStateWhileKeepingEveryPhoneActionReachable() {
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = episode(1), phone = true)
        } }
        try {
            frames(scene)
            assertEquals(ToggleableState.Off, toggleState(scene, "Fill video"))
            click(scene, "Fill video"); frames(scene)
            assertEquals(ToggleableState.On, toggleState(scene, "Fit video"))
            assertNull(fitFillOrNull(scene, "Fill video"))
            nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }.forEach {
                assertTrue(it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= 390, "Phone action overflows with Fill")
            }
            click(scene, "Fit video"); frames(scene)
            assertEquals(ToggleableState.Off, toggleState(scene, "Fill video"))
        } finally { scene.close() }
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

    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4) }
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