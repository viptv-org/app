package org.viptv.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.*
import org.viptv.app.features.settings.*
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AutoplayNextSettingTest {
    @get:Rule val uiThread = UiThreadRule()
    private class MemoryStore(var raw: String? = null) : DeviceRecordStore {
        override fun read() = raw
        override fun write(value: String) { raw = value }
    }

    @Test fun onByDefaultAndAChangePersistsForTheNextReader() {
        val store = MemoryStore()
        assertTrue(DevicePlaybackState(store).value.autoplayNext, "On by default")
        DevicePlaybackState(store).change(DevicePlaybackPreferences(autoplayNext = false))
        assertFalse(DevicePlaybackState(store).value.autoplayNext, "A new reader, as the player on its next open, sees Off")
        assertTrue(DevicePlaybackState(MemoryStore("not json")).value.autoplayNext, "An unreadable record falls back to On")
        assertFalse(DevicePlaybackState(MemoryStore("""{"autoplayNext":false,"later":1}""")).value.autoplayNext,
            "A newer build's fields don't throw the record away")
    }

    @Test fun phonePlaybackSwitchAppliesAtOnceWithoutSave() {
        val store = MemoryStore()
        val saved = mutableListOf<ProfilePreferences>()
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            PhoneProfilePreferences("Playback", PreferencesSnapshot(1, ProfilePreferences()), false, null, {}, { saved += it }, remember { DevicePlaybackState(store) })
        } }
        try {
            frames(scene)
            assertEquals(ToggleableState.On, switch(scene).config[SemanticsProperties.ToggleableState])
            capture(scene, "phone-playback-autoplay-setting")
            switch(scene).config[SemanticsActions.OnClick].action!!.invoke(); frames(scene, 20)
            assertEquals(ToggleableState.Off, switch(scene).config[SemanticsProperties.ToggleableState])
            assertFalse(DevicePlaybackState(store).value.autoplayNext); assertTrue(saved.isEmpty(), "Not a profile preference")
        } finally { scene.close() }
        val subtitles = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            PhoneProfilePreferences("Subtitles & audio", PreferencesSnapshot(1, ProfilePreferences()), false, null, {}, {}, remember { DevicePlaybackState(store) })
        } }
        try { frames(subtitles); assertTrue(nodes(subtitles).none { text(it).startsWith(autoplayNextRow) }) } finally { subtitles.close() }
    }

    @Test fun phoneShowsTheSwitchWhileProfilePreferencesFailToLoad() {
        val store = MemoryStore()
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            PhoneProfilePreferences("Playback", null, false, "Could not load profile preferences. Try again.", {}, {}, remember { DevicePlaybackState(store) })
        } }
        try {
            frames(scene); switch(scene).config[SemanticsActions.OnClick].action!!.invoke(); frames(scene, 20)
            assertFalse(DevicePlaybackState(store).value.autoplayNext)
        } finally { scene.close() }
    }

    @Test fun desktopPlaybackSwitchPersists() {
        val store = MemoryStore()
        val scene = ImageComposeScene(1280, 800, Density(1f)) { ViptvTheme {
            ProfilePreferencesSettings("Playback", PreferencesSnapshot(1, ProfilePreferences()), false, null, {}, {}, remember { DevicePlaybackState(store) })
        } }
        try {
            frames(scene); switch(scene).config[SemanticsActions.OnClick].action!!.invoke(); frames(scene, 20)
            assertEquals(ToggleableState.Off, switch(scene).config[SemanticsProperties.ToggleableState])
            assertFalse(DevicePlaybackState(store).value.autoplayNext)
        } finally { scene.close() }
    }

    @Test fun tvAutoplayRowFollowsTheNextEpisodeRowAndPersists() {
        val store = MemoryStore()
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {}, television = true,
                tvPreferences = TvSettingsPreferences(), changeTvPreferences = {}, tvDevicePlayback = remember { DevicePlaybackState(store) })
        } }
        try {
            frames(scene)
            val row = switch(scene)
            assertEquals(ToggleableState.On, row.config[SemanticsProperties.ToggleableState])
            val first = nodes(scene).single { text(it).startsWith("Next episode button") && it.config.contains(SemanticsActions.OnClick) }
            assertEquals(true, first.config.getOrNull(SemanticsProperties.Focused), "Focus starts on the first row")
            assertNotEquals(true, row.config.getOrNull(SemanticsProperties.Focused))
            capture(scene, "tv-playback-autoplay-setting")
            row.config[SemanticsActions.OnClick].action!!.invoke(); frames(scene, 20)
            assertEquals(ToggleableState.Off, switch(scene).config[SemanticsProperties.ToggleableState])
            assertFalse(DevicePlaybackState(store).value.autoplayNext)
        } finally { scene.close() }
    }

    private fun switch(scene: ImageComposeScene) = nodes(scene).single {
        text(it).startsWith(autoplayNextRow) && it.config.contains(SemanticsProperties.ToggleableState) && it.config.contains(SemanticsActions.OnClick)
    }
    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun capture(scene: ImageComposeScene, name: String) { System.getenv("VIPTV_NEXT_EPISODE_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs(); scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    } }
}
