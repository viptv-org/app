package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.features.settings.*
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlaybackTogglePreferencesTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun phonePlaybackTogglesSaveTheProfilePreferenceBody() {
        val saved = mutableListOf<ProfilePreferences>()
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            PhoneProfilePreferences("Playback", PreferencesSnapshot(3, ProfilePreferences()), false, null, {}, { saved += it })
        } }
        try {
            frames(scene)
            assertState(scene, nextEpisodeButtonRow, ToggleableState.On)
            assertState(scene, skipIntrosRow, ToggleableState.Off)
            toggle(scene, nextEpisodeButtonRow); frames(scene, 20)
            toggle(scene, skipIntrosRow); frames(scene, 20)
            assertTrue(saved.isEmpty())
            click(scene, "Save preferences")
            val body = saved.single()
            assertEquals(false, body.next_episode_button)
            assertEquals(true, body.skip_intros)
            val wire = profilePreferencesWire(body)
            assertEquals(false, wire.getValue("next_episode_button").jsonPrimitive.boolean)
            assertEquals(true, wire.getValue("skip_intros").jsonPrimitive.boolean)
        } finally { scene.close() }
    }

    @Test fun desktopPlaybackTogglesSaveTheProfilePreferenceBody() {
        val saved = mutableListOf<ProfilePreferences>()
        val scene = ImageComposeScene(1280, 800, Density(1f)) { ViptvTheme {
            ProfilePreferencesSettings("Playback", PreferencesSnapshot(3, ProfilePreferences()), false, null, {}, { saved += it })
        } }
        try {
            frames(scene)
            assertState(scene, nextEpisodeButtonRow, ToggleableState.On)
            assertState(scene, skipIntrosRow, ToggleableState.Off)
            toggle(scene, nextEpisodeButtonRow); frames(scene, 20)
            assertState(scene, nextEpisodeButtonRow, ToggleableState.Off)
            toggle(scene, skipIntrosRow); frames(scene, 20)
            assertTrue(saved.isEmpty())
            click(scene, "Save preferences")
            val body = saved.single()
            assertEquals(false, body.next_episode_button)
            assertEquals(true, body.skip_intros)
            val wire = profilePreferencesWire(body)
            assertEquals(false, wire.getValue("next_episode_button").jsonPrimitive.boolean)
            assertEquals(true, wire.getValue("skip_intros").jsonPrimitive.boolean)
        } finally { scene.close() }
    }

    private fun switch(scene: ImageComposeScene, label: String) = nodes(scene).single {
        text(it).startsWith(label) && it.config.contains(SemanticsProperties.ToggleableState) && it.config.contains(SemanticsActions.OnClick)
    }
    private fun assertState(scene: ImageComposeScene, label: String, state: ToggleableState) =
        assertEquals(state, switch(scene, label).config[SemanticsProperties.ToggleableState])
    private fun toggle(scene: ImageComposeScene, label: String) =
        assertTrue(switch(scene, label).config[SemanticsActions.OnClick].action!!.invoke())
    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun click(scene: ImageComposeScene, label: String) {
        val node = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
}