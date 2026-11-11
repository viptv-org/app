package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.viptv.app.features.calendar.CalendarEntry
import org.viptv.app.features.calendar.CalendarRow
import org.viptv.app.features.home.MediaCard
import org.viptv.app.features.home.TabletHero
import org.viptv.app.features.live.LiveChannel
import org.viptv.app.features.live.LiveChannelRow
import org.viptv.app.features.live.LivePreview
import org.viptv.app.features.live.LiveProgram
import org.viptv.app.features.live.TvLiveChannelLabel
import org.viptv.app.features.remote.TvRemoteOverlay
import org.viptv.app.features.remote.VizioRemoteEngine
import org.viptv.app.features.settings.VizioTv
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.VizioControllerOutput
import org.viptv.engine.wire.VizioControllerOutputKind
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ClampedTextOverflowTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val overflows = listOf(
        "A long running title that keeps going far past any single line. ".repeat(10),
        "这是一个极长的标题用来稳定地触发省略号而不是生硬裁剪".repeat(10),
        "🎬🍿📺🎞️🎥🍕🎉🏆🥇".repeat(30),
    )

    @Test fun everyClampedSurfaceEllipsizesInsteadOfClippingMidGlyph() {
        for (surface in surfaces()) for (value in overflows) {
            val scene = surface.render(value)
            try {
                frames(scene)
                val overflowing = layouts(scene).filter { it.multiParagraph.didExceedMaxLines }
                assertTrue(overflowing.isNotEmpty(), "${surface.name}: '$value' must overflow at least one clamped line")
                overflowing.forEach { layout ->
                    assertEquals(TextOverflow.Ellipsis, layout.layoutInput.overflow,
                        "${surface.name}: '$value' clamps with ${layout.layoutInput.overflow} instead of an ellipsis")
                }
            } finally { scene.close() }
        }
    }

    private fun surfaces(): List<SurfaceCase> = listOf(
        SurfaceCase("live next line") { text ->
            phoneScene(400, 130) {
                LiveChannelRow(
                    LiveChannel(channel("Channel One"), id = "one", connectionId = "m3u:1",
                        programs = listOf(LiveProgram("now", "Current", 0, 60), LiveProgram("next", text, 60, 120))),
                    now = 30, watch = {}, favourite = null)
            }
        },
        SurfaceCase("tv channel label") { text ->
            tvScene(300, 72) { TvLiveChannelLabel(LiveChannel(channel(text)), 1f, 34.dp, null, null, Modifier, focus = {}, watch = {}) }
        },
        SurfaceCase("calendar subtitle") { text ->
            phoneScene(390, 140) { CalendarRow(entry(text), phone = true, television = false, scale = 1f, focus = {}, open = {}) }
        },
        SurfaceCase("card chip") { text ->
            phoneScene(240, 180) { MediaCard(channel(text, badges = listOf(text)), width = 240, open = {}) }
        },
        SurfaceCase("live hero description") { text ->
            phoneScene(400, 320) {
                LivePreview(LiveChannel(channel("Channel One")), LiveProgram("p", "Programme", 0, 120, description = text), 30,
                    phone = false, television = false, scale = 1f, watch = {}, favourite = null)
            }
        },
        SurfaceCase("tablet hero description") { text ->
            phoneScene(390, 760) { TabletHero(channel(text, description = text), open = {}, play = {}, navigate = {}, save = {}, saved = false) }
        },
        SurfaceCase("watch on tv header") { text ->
            phoneScene(390, 620) {
                val scope = rememberCoroutineScope()
                TvRemoteOverlay(VizioTv(text, "https://$text"), "https://example.invalid/tv/?platform=vizio", false, {}, scope, {}) { CompleteEngine() }
            }
        },
    )

    private class CompleteEngine : VizioRemoteEngine {
        override suspend fun run(operation: String, input: JsonObject) = complete()
        override fun close() = Unit
    }

    private fun channel(title: String, description: String? = null, badges: List<String> = emptyList()) =
        Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1", title = title, badges = badges, description = description)

    private fun entry(subtitle: String): CalendarEntry {
        val parent = Card(MediaIdentity("simkl:tv:20", "series", emptyList()), provider_id = "simkl",
            title = "Scheduled series", badges = emptyList())
        return CalendarEntry("one", "one", "12:00 PM", parent, subtitle = subtitle)
    }

    private fun phoneScene(width: Int, height: Int, content: @Composable () -> Unit) = ImageComposeScene(width, height, Density(1f)) {
        ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
    }

    private fun tvScene(width: Int, height: Int, content: @Composable () -> Unit) = ImageComposeScene(width, height, Density(1f)) {
        ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
    }

    private fun layouts(scene: ImageComposeScene): List<TextLayoutResult> = scene.semanticsOwners
        .flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
        .filter { it.config.contains(SemanticsActions.GetTextLayoutResult) }
        .mapNotNull { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            results.singleOrNull()
        }

    private fun frames(scene: ImageComposeScene) = repeat(30) { scene.render((it + 1) * 16_666_667L).close() }

    private data class SurfaceCase(val name: String, val render: (String) -> ImageComposeScene)
}

private fun complete() = VizioControllerOutput(VizioControllerOutputKind.COMPLETE, null, null, null, null, false)