package org.viptv.app

import org.junit.Rule
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.player.*
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PhonePlayerStatesUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(number: Long) = Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        metadata = CardMetadata(runtime_minutes = 52), provider_id = "addon:7", title = "Episode $number", badges = emptyList(),
        parent_identity = parent, metadata_origin = origin)

    @Test fun scrubbingTimelineSeeksToFractionAndShowsRemainingTime() {
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = episode(24), phone = true,
                preview = PlayerFact.Progress(842_000, 3_132_000))
        } }
        try {
            frames(scene); capture(scene, "phone-player-scrubbing")
            // Scrub the timeline slider: the preview position follows the thumb.
            val slider = nodes(scene).single { it.config.contains(SemanticsActions.SetProgress) }
            slider.config[SemanticsActions.SetProgress].action!!.invoke(0.5f)
            frames(scene, 20)
            // Half of 3_132_000 ms = 26:06 shown while scrubbing.
            assertTrue(nodes(scene).any { text(it) == "26:06" })
            capture(scene, "phone-player-seek-indicator")
        } finally { scene.close() }
    }

    @Test fun lockedScreenShowsOnlyUnlockActionAndUnlockRestoresControls() {
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = episode(24), phone = true,
                preview = PlayerFact.Progress(842_000, 3_132_000))
        } }
        try {
            frames(scene)
            click(scene, "Lock"); frames(scene, 20)
            capture(scene, "phone-player-locked")
            val textClickable = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }
            // While locked only the icon unlock button is interactive.
            assertTrue(textClickable.size <= 2)
            val unlock = nodes(scene).single {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains("Unlock") } == true && it.config.contains(SemanticsActions.OnClick)
            }
            unlock.config[SemanticsActions.OnClick].action!!.invoke(); frames(scene, 20)
            assertTrue(nodes(scene).any { text(it) == "Lock" })
        } finally { scene.close() }
    }

    @Test fun audioAndSubtitlesSheetRendersTrackColumnsAndSizeControl() {
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = episode(24), phone = true,
                preview = PlayerFact.Progress(842_000, 3_132_000))
        } }
        try {
            frames(scene)
            click(scene, "Audio & subtitles"); frames(scene, 20)
            capture(scene, "phone-player-audio-subtitles")
            assertTrue(nodes(scene).any { text(it) == "Audio & subtitles" })
            assertTrue(nodes(scene).any { text(it) == "Audio" })
            assertTrue(nodes(scene).any { text(it) == "Subtitles" })
        } finally { scene.close() }
    }

    @Test fun liveChannelShowsLiveBadgeInsteadOfTimeline() {
        val channel = Card(MediaIdentity("chan-9", "channel", emptyList()), title = "Channel 9 News",
            provider_id = "addon:7", badges = emptyList(), metadata_origin = origin)
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = channel, phone = true)
        } }
        try {
            frames(scene)
            assertTrue(nodes(scene).any { text(it) == "LIVE" })
            assertFalse(nodes(scene).any { it.config.contains(SemanticsActions.SetProgress) })
            capture(scene, "phone-player-live")
        } finally { scene.close() }
    }

    @Test fun startingStateShowsBufferingIndicator() {
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, false, null, {}, {}, {}, title = episode(24), phone = true,
                preview = PlayerFact.Progress(0, null))
        } }
        try {
            frames(scene)
            // The buffering indicator (opening) shows before the first frame.
            capture(scene, "phone-player-buffering")
        } finally { scene.close() }
    }

    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun action(scene: ImageComposeScene, label: String) = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
    private fun click(scene: ImageComposeScene, label: String) { assertTrue(action(scene, label).config[SemanticsActions.OnClick].action!!.invoke()) }
    private fun capture(scene: ImageComposeScene, name: String) {
        System.getenv("VIPTV_PHONE_PLAYER_CAPTURE_DIR")?.let { path ->
            File(path).mkdirs(); scene.render(3_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        }
    }
}
