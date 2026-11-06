@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt
import org.viptv.app.features.live.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvGuideChannelLabelTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val channels = listOf(
        channel("101", "BBC One HD"), channel("402", "Sky Sports Main Event"), channel("205", "CNN International"),
        channel("240", "Discovery Channel"), channel("601", "Cartoon Network"), channel("260", "National Geographic"))

    private fun channel(number: String, title: String) = LiveChannel(
        Card(MediaIdentity("live-$number", "channel", emptyList()), provider_id = "m3u:1", title = title, badges = emptyList()),
        id = number, connectionId = "m3u:1", number = number, programs = listOf(LiveProgram("p$number", "Programme $number", 0, 120)))

    @Test fun everyChannelNumberStaysOnOneLine() = checkNumbers(1f)

    @Test fun everyChannelNumberStaysOnOneLineAtLargeDisplayText() = checkNumbers(1.3f)

    private fun checkNumbers(fontScale: Float) {
        Page(fontScale) { LiveScreen(channels, television = true, start = 0, now = 30, watch = {}) }.use { page ->
            val numbers = page.nodes().filter { page.text(it) in channels.map { it.number } }
            assertEquals(channels.size, numbers.size, "every channel label draws its number")
            val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f, fontScale), LayoutDirection.Ltr)
            for (node in numbers) {
                val lines = node.boundsInRoot.height / (26f * fontScale)
                assertTrue(lines < 1.6f, "channel number ${page.text(node)} wraps onto ${"%.1f".format(lines)} lines")
                val needed = measurer.measure(page.text(node), TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), softWrap = false).size.width
                assertTrue(node.boundsInRoot.width >= needed, "channel number ${page.text(node)} is cut off: " +
                    "${node.boundsInRoot.width} px column for $needed px of digits")
            }
            val names = page.nodes().filter { page.text(it) in channels.map { it.card.title } }
            assertEquals(channels.size, names.size, "every channel label draws its name")
            val lefts = names.map { it.boundsInRoot.left }.distinct()
            assertEquals(1, lefts.size, "names share one column, whatever the number's digit count: $lefts")
            for (node in numbers) {
                val name = names.firstOrNull { kotlin.math.abs(it.boundsInRoot.center.y - node.boundsInRoot.center.y) < 30f }
                    ?: fail("no name beside ${page.text(node)}: " + names.joinToString { "${page.text(it)}@${it.boundsInRoot}" })
                assertTrue(node.boundsInRoot.right <= name.boundsInRoot.left,
                    "${page.text(node)} overlaps ${page.text(name)}")
            }
        }
    }

    private class Page(fontScale: Float = 1f, content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        private val scene = ImageComposeScene(1920, 1080, Density(1f, fontScale)) {
            ViptvTheme(television = true) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }.also { s -> repeat(30) { s.render(frame++ * 16_666_667L).close() } }

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        // The channel label merges its descendants, so the number and the name are only separate nodes in the unmerged tree.
        fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }

        override fun close() = scene.close()
    }
}