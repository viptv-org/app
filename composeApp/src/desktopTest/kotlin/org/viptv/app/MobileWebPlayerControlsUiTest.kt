package org.viptv.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.features.player.TvPlaybackExtras
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class MobileWebPlayerControlsUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(number: Long) = Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        metadata = CardMetadata(runtime_minutes = 52), provider_id = "addon:7", title = "Episode $number", badges = emptyList(),
        parent_identity = parent, metadata_origin = origin)
    private val episodes = listOf(episode(24), episode(25))

    private val shortLabel = "Cinemeta"
    private val longLabel = "Torrentio · 1080p · Direct play from a provider with a very long advertised name"
    private val unbrokenLabel = "provider.example/" + "a".repeat(96)

    @Test fun narrowSourceLabelsKeepTheirControlsOnScreen() {
        for (label in listOf(shortLabel, longLabel, unbrokenLabel, null)) {
            for ((width, height) in listOf(320 to 568, 360 to 740, 390 to 844)) {
                val scene = player(width, height, label)
                try {
                    frames(scene)
                    assertSourceLabelOnOneLine(scene, label ?: "Streams", "$width×$height \"$label\"")
                    assertControlsInView(scene, width, height, "$width×$height \"$label\"")
                    assertFalse(hasControl(scene, "Volume"), "$width×$height: the compact player hides the volume slider")
                } finally { scene.close() }
            }
        }
    }

    @Test fun aLandscapePhoneKeepsEveryControlInView() {
        val scene = player(844, 390, longLabel)
        try {
            frames(scene)
            assertSourceLabelOnOneLine(scene, longLabel, "844×390")
            assertControlsInView(scene, 844, 390, "844×390")
        } finally { scene.close() }
    }

    @Test fun widePlayersKeepTheSingleDesktopRow() {
        val scene = player(1280, 720, longLabel)
        try {
            frames(scene)
            assertSourceLabelOnOneLine(scene, longLabel, "1280×720")
            assertControlsInView(scene, 1280, 720, "1280×720")
            assertEquals(control(scene, "Play").boundsInRoot.center.y, control(scene, "Episodes").boundsInRoot.center.y, 1f,
                "the desktop row keeps the transport and the tools side by side")
            assertTrue(hasControl(scene, "Volume"), "the desktop player keeps its volume slider")
        } finally { scene.close() }
    }

    private fun assertSourceLabelOnOneLine(scene: ImageComposeScene, label: String, at: String) {
        val bounds = control(scene, label).boundsInRoot
        assertTrue(bounds.height <= 48f, "$at: the source label stays on one line, was ${bounds.height} tall")
        assertTrue(bounds.width >= 32f, "$at: the source label keeps a usable width, was ${bounds.width}")
    }

    private fun assertControlsInView(scene: ImageComposeScene, width: Int, height: Int, at: String) {
        val viewport = Rect(0f, 0f, width.toFloat(), height.toFloat())
        val controls = controls(scene)
        assertTrue(controls.size >= 6, "$at: the player draws its controls, saw ${controls.map(::label)}")
        controls.forEach { node ->
            val bounds = node.boundsInRoot
            assertTrue(bounds.width >= 32f && bounds.height >= 40f, "$at: ${label(node)} has a ${bounds.width}×${bounds.height} touch target")
            assertTrue(bounds.left >= viewport.left - 1f && bounds.right <= viewport.right + 1f &&
                bounds.top >= viewport.top - 1f && bounds.bottom <= viewport.bottom + 1f, "$at: ${label(node)} at $bounds leaves the $viewport viewport")
        }
    }

    private fun controls(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
        .filter { it.config.contains(SemanticsActions.OnClick) }
    private fun control(scene: ImageComposeScene, label: String) = assertNotNull(
        controls(scene).firstOrNull { text(it) == label || it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true },
        "No control \"$label\"; saw ${controls(scene).map(::label)}")
    private fun hasControl(scene: ImageComposeScene, label: String) = scene.semanticsOwners
        .flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
        .any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true }
    private fun label(node: SemanticsNode) = text(node).ifEmpty {
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
    }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

    private fun player(width: Int, height: Int, sourceLabel: String?): ImageComposeScene {
        var time = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = episode(24), phone = false,
                preview = PlayerFact.Progress(842_000, 3_132_000), chooseSources = {},
                tvExtras = TvPlaybackExtras(sourceLabel = sourceLabel, episodes = episodes, nextEpisode = episode(25)),
                playEpisode = {}, controlInsets = WindowInsets(0, 0, 0, 0))
        } }
        repeat(20) { scene.render(time).close(); time += 16_666_667L; Thread.sleep(4) }
        return scene
    }

    private fun frames(scene: ImageComposeScene, count: Int = 20) {
        var time = 10_000_000_000L
        repeat(count) { scene.render(time).close(); time += 16_666_667L; Thread.sleep(4) }
    }
}