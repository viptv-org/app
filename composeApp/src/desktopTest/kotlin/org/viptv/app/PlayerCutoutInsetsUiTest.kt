package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.features.player.*
import org.viptv.app.player.PlayerFact
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlayerCutoutInsetsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val width = 844; private val height = 390
    private val cutout = 48; private val bottomBar = 16
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(number: Long) = Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        metadata = CardMetadata(runtime_minutes = 52), provider_id = "addon:7", title = "Episode $number", badges = emptyList(),
        parent_identity = parent, metadata_origin = origin)

    @Test fun onlyThePhonePlayerOptsOutOfTheRootSafeDrawingPadding() {
        assertTrue(playerDrawsEdgeToEdge(Screen.Player, television = false))
        assertFalse(playerDrawsEdgeToEdge(Screen.Player, television = true), "TV keeps its current insets")
        Screen.entries.filter { it != Screen.Player }.forEach { assertFalse(playerDrawsEdgeToEdge(it, television = false), "$it keeps its insets") }
    }

    @Test fun cameraOnTheLeftKeepsControlsOutOfTheCutoutWhileVideoFillsIt() = assertControlsClearTheCutout("left", WindowInsets(left = cutout.dp, bottom = bottomBar.dp))

    @Test fun cameraOnTheRightKeepsControlsOutOfTheCutoutWhileVideoFillsIt() = assertControlsClearTheCutout("right", WindowInsets(right = cutout.dp, bottom = bottomBar.dp))

    private fun assertControlsClearTheCutout(side: String, insets: WindowInsets) {
        val scene = scene(insets) { PlayerScreen(null, true, null, {}, {}, {}, title = episode(24), phone = true,
            preview = PlayerFact.Progress(842_000, 3_132_000), tvExtras = TvPlaybackExtras(episodes = listOf(episode(24), episode(25)), nextEpisode = episode(25)),
            playEpisode = {}, controlInsets = insets) }
        try {
            frames(scene); capture(scene, "phone-player-cutout-$side")
            assertControlsInside(scene, side)
            assertBlack(scene, if (side == "left") 4 else width - 5, height / 2)
        } finally { scene.close() }
    }

    @Test fun panelsReachTheEdgeButKeepTheirContentOutOfTheCutout() {
        val insets = WindowInsets(right = cutout.dp, bottom = bottomBar.dp)
        val scene = scene(insets) { PlayerScreen(null, true, null, {}, {}, {}, title = episode(24), phone = true,
            preview = PlayerFact.Progress(842_000, 3_132_000), tvExtras = TvPlaybackExtras(episodes = listOf(episode(24), episode(25)), nextEpisode = episode(25)),
            playEpisode = {}, controlInsets = insets) }
        try {
            frames(scene)
            click(scene, "Audio & subtitles"); frames(scene, 20); capture(scene, "phone-player-cutout-tracks")
            assertControlsInside(scene, "right"); assertSurface(scene, width - 5, height / 2)
            click(scene, "Close track selection"); frames(scene, 40)
            click(scene, "Episodes"); frames(scene, 60); capture(scene, "phone-player-cutout-episodes")
            assertControlsInside(scene, "right"); assertSurface(scene, width - 5, height / 2)
        } finally { scene.close() }
    }

    @Test fun failureActionsStayOutOfTheCutout() {
        val insets = WindowInsets(left = cutout.dp, bottom = bottomBar.dp)
        val scene = scene(insets) { PlayerScreen(null, true, "The selected source did not respond.", {}, {}, {}, title = episode(2), phone = true,
            tvExtras = TvPlaybackExtras(fallbackLabel = "Fixture 1080p"), retry = {}, chooseSources = {}, controlInsets = insets) }
        try {
            frames(scene); capture(scene, "phone-player-cutout-failure")
            assertControlsInside(scene, "left"); assertBlack(scene, 4, height / 2)
        } finally { scene.close() }
    }

    private fun scene(insets: WindowInsets, content: @Composable () -> Unit) = ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
        Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
    } }

    private fun assertControlsInside(scene: ImageComposeScene, side: String) {
        val safe = Rect(if (side == "left") cutout.toFloat() else 0f, 0f, if (side == "right") (width - cutout).toFloat() else width.toFloat(), (height - bottomBar).toFloat())
        val controls = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) || it.config.contains(SemanticsActions.SetProgress) }
            .filter { it.boundsInWindow.width < width }
        assertTrue(controls.isNotEmpty())
        controls.forEach { node ->
            val bounds = node.boundsInWindow
            assertTrue(bounds.left >= safe.left && bounds.right <= safe.right && bounds.bottom <= safe.bottom,
                "${label(node)} at $bounds leaves the safe area $safe")
        }
    }
    private fun pixel(scene: ImageComposeScene, x: Int, y: Int) =
        scene.render(3_000_000_000L).use { image -> org.jetbrains.skia.Bitmap.makeFromImage(image).use { it.getColor(x, y) } }
    private fun assertBlack(scene: ImageComposeScene, x: Int, y: Int) =
        assertEquals(0xFF000000.toInt(), pixel(scene, x, y), "Pixel ($x, $y) must be the player's black")
    private fun assertSurface(scene: ImageComposeScene, x: Int, y: Int) {
        assertEquals(Palette.surface.toArgb(), pixel(scene, x, y), "The panel surface must reach the screen edge at ($x, $y)")
    }
    private fun label(node: SemanticsNode) = text(node).ifEmpty { node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty() }
    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun click(scene: ImageComposeScene, label: String) {
        val node = nodes(scene).first { (text(it) == label || it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true) &&
            it.config.contains(SemanticsActions.OnClick) }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
    private fun capture(scene: ImageComposeScene, name: String) {
        System.getenv("VIPTV_PHONE_PLAYER_CAPTURE_DIR")?.let { path ->
            File(path).mkdirs(); scene.render(3_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        }
    }
}
