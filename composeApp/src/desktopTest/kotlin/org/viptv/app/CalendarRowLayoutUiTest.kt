package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.calendar.CalendarEntry
import org.viptv.app.features.calendar.CalendarRow
import org.viptv.app.features.calendar.CalendarSource
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.MetadataOrigin
import kotlin.math.abs
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class CalendarRowLayoutUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val parent = Card(MediaIdentity("simkl:tv:20", "series", emptyList()), provider_id = "simkl",
        title = "Scheduled series", badges = emptyList(), metadata_origin = MetadataOrigin("simkl"))
    private val episodeCard = parent.copy(identity = MediaIdentity("simkl:tv:20:2:3", "series", emptyList(), EpisodeCoordinates("aired", 2, 3)),
        parent_identity = parent.identity, title = "Chapter 3")

    private fun entry(time: String) = CalendarEntry("release", "one", time, parent,
        subtitle = "S2 E3 · Chapter 3", aired = true, season = 2, episode = 3, source = CalendarSource.Library, episodeCard = episodeCard)

    @Test fun theFindActionSitsInsideItsCardAtDesktopAndWebWidths() {
        for (width in listOf(1440, 800)) {
            var watched = 0
            val scene = ImageComposeScene(width, 900, Density(1f)) {
                ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    CalendarRow(entry("12:00 AM"), phone = false, television = false, scale = 0.65f, focus = {}, open = {}, watch = { watched++ })
                } }
            }
            try {
                frames(scene)
                val card = nodes(scene).single { it.config.contains(SemanticsActions.OnClick) && text(it).contains("Scheduled series") }
                val action = clickable(scene, "Find S2 E3")
                assertTrue(action.boundsInRoot.left >= card.boundsInRoot.left - 0.5f &&
                    action.boundsInRoot.right <= card.boundsInRoot.right + 0.5f &&
                    action.boundsInRoot.top >= card.boundsInRoot.top - 0.5f &&
                    action.boundsInRoot.bottom <= card.boundsInRoot.bottom + 0.5f,
                    "$width px: the Find action ${action.boundsInRoot} must sit inside its card ${card.boundsInRoot}")
                val labels = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
                val timeNode = labels.single { text(it) == "12:00 AM" }
                assertTrue(abs(action.boundsInRoot.left - timeNode.boundsInRoot.left) <= 1f,
                    "$width px: the Find action aligns with the card's left content edge")
                assertTrue(action.config[SemanticsActions.OnClick].action!!.invoke(), "$width px: the Find action clicks")
                assertEquals(1, watched, "$width px: the Find action drives its own click")
            } finally { scene.close() }
        }
    }

    @Test fun everyTimeLabelStaysOnOneLineAtDesktopAndWebWidths() {
        for (width in listOf(1440, 800)) for (time in listOf("12:00 AM", "12:00 PM", "10:00 AM")) {
            val scene = ImageComposeScene(width, 900, Density(1f)) {
                ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    CalendarRow(entry(time), phone = false, television = false, scale = 0.65f, focus = {}, open = {})
                } }
            }
            try {
                frames(scene)
                val labels = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
                val timeNode = labels.single { text(it) == time }
                assertEquals(1, layout(timeNode).lineCount, "$width px '$time' must not wrap")
            } finally { scene.close() }
        }
    }

    private fun layout(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
        return results.single()
    }
    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render((it + 1) * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun clickable(scene: ImageComposeScene, label: String) = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
}