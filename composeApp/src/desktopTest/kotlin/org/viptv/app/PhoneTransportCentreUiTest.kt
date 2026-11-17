package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.player.PlayerChromeTag
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.features.player.TransportChromeGap
import org.viptv.app.features.player.TvPlaybackExtras
import org.viptv.app.player.PlayerFact
import org.viptv.app.preview.PreviewData
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.math.abs
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PhoneTransportCentreUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun thePlayButtonSitsAtThePlayersCentreOnEveryPhone() {
        for ((width, height) in listOf(390 to 844, 412 to 915, 844 to 390, 320 to 640)) withPlayer(width, height) { play, chromeTop ->
            assertTrue(play.bottom + TransportChromeGap.value <= chromeTop + 1f, "${width}×$height: the transport at $play meets the chrome at $chromeTop")
            assertEquals(width / 2f, play.center.x, 1f, "${width}×$height: play is off the horizontal centre: $play")
            assertEquals(height / 2f, play.center.y, 1f, "${width}×$height: play is off the vertical centre: $play")
        }
    }

    @Test fun aShortPlayerLiftsTheTransportOnlyByTheOverlap() {
        withPlayer(640, 360) { play, chromeTop ->
            assertTrue(play.center.y < 180f, "the centred transport would meet the chrome, so it must lift: $play")
            assertEquals(chromeTop - TransportChromeGap.value, play.bottom, 1f, "the transport clears the chrome by the gap and no more: $play vs $chromeTop")
            assertEquals(320f, play.center.x, 1f, "lifting keeps the horizontal centre: $play")
        }
    }

    private fun withPlayer(width: Int, height: Int, check: (play: Rect, chromeTop: Float) -> Unit) {
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(false) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                PlayerScreen(null, true, null, {}, {}, {}, PreviewData.playerTitle, PlayerFact.Progress(842_000, 3_132_000), phone = true,
                    tvExtras = TvPlaybackExtras(episodes = PreviewData.playerEpisodes, nextEpisode = PreviewData.playerNextEpisode, sourceLabel = "Streams"),
                    playEpisode = {}, chooseSources = {})
            }
        } }
        try {
            var previous: Rect? = null
            scene.renderUntil(what = "the transport to settle") {
                val play = control(scene, "Play")?.boundsInRoot
                (play != null && play == previous).also { previous = play }
            }
            capture(scene, "phone-transport-${width}x$height")
            val play = assertNotNull(control(scene, "Play")).boundsInRoot
            for (skip in listOf("Back 10 seconds", "Forward 10 seconds")) {
                val bounds = assertNotNull(control(scene, skip), "No \"$skip\"").boundsInRoot
                assertEquals(play.center.y, bounds.center.y, 1f, "${width}×$height: $skip at $bounds is off play's vertical centre $play")
            }
            val chromeTop = assertNotNull(nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == PlayerChromeTag }).boundsInRoot.top
            check(play, chromeTop)
        } finally { scene.close() }
    }

    private fun control(scene: ImageComposeScene, label: String) = nodes(scene).firstOrNull {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true && it.config.contains(SemanticsActions.OnClick)
    }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun capture(scene: ImageComposeScene, name: String) {
        System.getenv("VIPTV_PHONE_TRANSPORT_CAPTURE_DIR")?.let { path ->
            File(path).mkdirs(); scene.render(3_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        }
    }
}
