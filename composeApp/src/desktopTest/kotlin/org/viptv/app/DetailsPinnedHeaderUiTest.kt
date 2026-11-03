package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.components.PinnedHeaderSurface
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.CardMetadata
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.EpisodeSeason
import org.viptv.engine.wire.MediaIdentity
import java.io.File
import kotlin.math.abs
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class DetailsPinnedHeaderUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val title = "The End of Oak Street"
    private val series = Card(MediaIdentity("oak-street", "series", emptyList()), provider_id = "addon:21",
        title = title, description = "After a cosmic event lifts their street out of the suburbs, the Platt family " +
            "learns what their neighbours were really hiding.", badges = listOf("4K"),
        metadata = CardMetadata(year = 2026, runtime_minutes = 44, genres = listOf("Drama", "Mystery")))
    private fun episode(season: Long, number: Long) = series.copy(
        identity = MediaIdentity("oak-street:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = "Episode $season-$number", description = null, metadata = null)
    private val detail = DetailSnapshot(series, episodes = (1L..10L).map { episode(1, it) },
        seasons = listOf(EpisodeSeason(1, 10, 0)))

    private val raised = Palette.raised.toArgb()
    private val background = Palette.background.toArgb()

    @Test fun theSurfaceIsClearAtTheTopAndOpaqueOnceScrolled() {
        for (scrolled in listOf(false, true)) {
            val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme(false) {
                Box(Modifier.fillMaxSize().background(Palette.raised)) {
                    PinnedHeaderSurface(scrolled, Modifier.align(Alignment.TopStart).fillMaxWidth()) {}
                }
            } }
            try {
                frames(scene)
                if (scrolled) {
                    assertNear(background, pixel(scene, 340, 20), "once scrolled the header hides the content")
                    assertNear(background, pixel(scene, 340, 58), "the fade starts at the header's background")
                    assertNear(raised, pixel(scene, 340, 82), "the fade lets the content back through below the header")
                } else {
                    assertEquals(raised, pixel(scene, 340, 20), "at the top the header is transparent over the content")
                }
            } finally { scene.close() }
        }
    }

    @Test fun scrolledDetailsContentDoesNotShowThroughTheHeader() {
        val scene = detailsScene()
        try {
            frames(scene)
            assertEquals(raised, pixel(scene, 340, 20), "at the top the hero artwork sits behind the transparent header")
            scrollBy(scene, 260f)
            frames(scene)
            assertEquals(background, pixel(scene, 340, 20),
                "once scrolled the header is opaque: content must not read through the title or back control")
        } finally { scene.close() }
    }

    @Test fun capturesDetailsAndASettingsSubpageForThePr() {
        val dir = System.getenv("VIPTV_585_RENDER_DIR") ?: return
        File(dir).mkdirs()
        var scene = detailsScene()
        try {
            frames(scene)
            capture(scene, "details-top")
            scrollBy(scene, 600f)
            frames(scene)
            capture(scene, "details-scrolled")
        } finally { scene.close() }
        scene = settingsScene()
        try {
            frames(scene)
            capture(scene, "settings-playback-top")
            scrollBy(scene, 600f)
            frames(scene)
            capture(scene, "settings-playback-scrolled")
        } finally { scene.close() }
    }

    private fun detailsScene(): ImageComposeScene {
        frame = 0
        return ImageComposeScene(390, 844, Density(1f)) { ViptvTheme(false) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = {})
            }
        } }
    }

    private fun settingsScene(): ImageComposeScene {
        frame = 0
        return ImageComposeScene(390, 844, Density(1f)) { ViptvTheme(false) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                SettingsScreen(local = false, addons = emptyList(), enabledAddonIds = emptySet(), busy = false,
                    error = null, sessionNotice = null, install = {}, toggle = { _, _ -> }, remove = {}, profiles = {},
                    leave = {}, television = false, initialSection = "Playback",
                    sectionPreferences = { _, _ ->
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            repeat(30) { Text("Playback preference $it", Modifier.padding(vertical = 14.dp), color = Palette.text) }
                        }
                    })
            }
        } }
    }

    private fun capture(scene: ImageComposeScene, name: String) =
        scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(System.getenv("VIPTV_585_RENDER_DIR"), "$name.png").writeBytes(it.bytes) } }

    private fun scrollBy(scene: ImageComposeScene, px: Float) {
        val scroll = nodes(scene).filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
            .maxByOrNull { it.boundsInRoot.height } ?: fail("no scrolling page")
        scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, px)
    }

    private fun pixel(scene: ImageComposeScene, x: Int, y: Int): Int =
        scene.render(frame++ * 16_666_667L).use { image ->
            Bitmap.makeFromImage(image).use { pixels -> pixels.getColor(x, y) }
        }

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)

    private fun assertNear(expected: Int, actual: Int, message: String) =
        assertTrue(distance(expected, actual) <= 4, "$message: expected ${hex(expected)}, was ${hex(actual)}")
    private fun distance(a: Int, b: Int) = (0..2).maxOf { abs(((a shr (it * 8)) and 0xFF) - ((b shr (it * 8)) and 0xFF)) }
    private fun hex(color: Int) = "#" + color.toUInt().toString(16).padStart(8, '0')

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
}