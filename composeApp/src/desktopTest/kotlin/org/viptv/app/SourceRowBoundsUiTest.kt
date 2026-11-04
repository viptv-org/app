@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.ceil
import kotlin.test.*
import org.junit.Rule
import org.viptv.app.features.sources.SourceRow
import org.viptv.app.features.sources.TvSourceInfo
import org.viptv.app.features.sources.TvSourceRow
import org.viptv.app.features.sources.cacheLabel
import org.viptv.app.features.sources.factItems
import org.viptv.app.features.sources.parseReleaseGroup
import org.viptv.app.features.sources.parseTracker
import org.viptv.app.features.sources.releaseGroupLabel
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation

class SourceRowBoundsUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun torrent() = SourceCandidate(
        id = "t", provider_id = "addon:20", label = "Torrentio\n1080p",
        detail = "[EMBER] The Genius Prince's Guide to Raising a Nation Out of Debt (2022) (Batch)\n" +
            "S01E03-A Marriage as a Pretext [EA9B5806] 10-bit.mkv\n👤 62 💾 481.6 MB ⚙️ NyaaSi",
        quality_height = 1080L, release_type = "bluray", seeders = 62, delivery = "direct",
        audio_flags = listOf("dual", "dubbed"),
        presentation = SourcePresentation(provider_name = "Torrentio", quality = "1080p", size_label = "481.6 MB", video_codec = "HEVC", container = "mkv"),
    )

    @Test fun releaseGroupIsParsedFromTheLeadingBracketOrTrailingGroup() {
        assertEquals("EMBER", parseReleaseGroup("[EMBER] The Genius Prince's Guide (Batch)\nS01E03.mkv"))
        assertEquals("Tsundere-Raws", parseReleaseGroup("Show.S01E03.1080p-Tsundere-Raws.mkv"))
        assertNull(parseReleaseGroup("S01E03-A Marriage as a Pretext [EA9B5806].mkv"))
        assertNull(parseReleaseGroup(null))
    }

    @Test fun trackerIsParsedFromTheSeederLine() {
        assertEquals("NyaaSi", parseTracker("S01E03.mkv\n👤 62 💾 481.6 MB ⚙️ NyaaSi"))
        assertNull(parseTracker("S01E03.mkv"))
    }

    @Test fun cacheLabelMapsDeliveryToInstantOrNotCached() {
        assertNull(torrent().copy(delivery = null).cacheLabel())
        assertEquals("Instant", torrent().copy(delivery = "direct").cacheLabel())
        assertEquals("Not cached", torrent().copy(delivery = "torrent").cacheLabel())
    }

    @Test fun factsOmitMissingFactsWithoutEmptySeparators() {
        val full = torrent()
        assertEquals(listOf("481.6 MB", "HEVC 10-bit", "↑62", "NyaaSi", "Torrentio", "Season pack"), full.factItems())
        val bare = full.copy(release_type = null, seeders = null, detail = null,
            presentation = SourcePresentation(provider_name = "Torrentio"))
        assertEquals(listOf("Torrentio"), bare.factItems())
        assertEquals("Torrentio", bare.releaseGroupLabel())
        assertEquals("EMBER", full.releaseGroupLabel())
    }

    @Test fun phoneRowShowsGroupAndFactsInsideItsBounds() {
        for (fontScale in listOf(1f, 1.3f)) {
            val scene = scene(390, 360, fontScale) {
                Column(Modifier.width(390.dp).padding(12.dp)) { SourceRow(torrent(), false, {}, selected = true, bestMatch = true) }
            }
            try {
                frames(scene)
                val where = "phone fontScale=$fontScale"
                val row = nodes(scene).single { it.config.contains(SemanticsActions.OnClick) }
                val placed = unmerged(scene).filter { it.layoutInfo.isPlaced && text(it).isNotBlank() }
                placed.forEach { node ->
                    assertTrue(node.boundsInRoot.top >= row.boundsInRoot.top - .5f, "$where: ${text(node).take(40)} starts above the row")
                    assertTrue(node.boundsInRoot.bottom <= row.boundsInRoot.bottom + .5f, "$where: ${text(node).take(40)} ends below the row")
                }
                val group = placed.single { text(it) == "EMBER" }
                capture(scene, "phone-target-$fontScale")
                assertEquals("EMBER".length, layoutOf(group).getLineEnd(0, visibleEnd = true), "$where: the release group is not ellipsized")
            } finally { scene.close() }
        }
    }

    @Test fun tvRowKeepsTheSameLayoutInsideItsBounds() {
        val scene = scene(1440, 300, 1f, television = true) {
            Column(Modifier.width(1200.dp)) { TvSourceRow(torrent(), TvSourceInfo("1080p"), 1f, null, true, {}, bestMatch = true) }
        }
        try {
            frames(scene)
            val row = nodes(scene).single { it.config.contains(SemanticsActions.OnClick) }
            val placed = unmerged(scene).filter { it.layoutInfo.isPlaced && text(it).isNotBlank() }
            placed.forEach { node ->
                assertTrue(node.boundsInRoot.top >= row.boundsInRoot.top - .5f)
                assertTrue(node.boundsInRoot.bottom <= row.boundsInRoot.bottom + .5f)
            }
            assertTrue(placed.any { text(it) == "EMBER" })
            capture(scene, "tv-target")
        } finally { scene.close() }
    }

    private fun scene(width: Int, height: Int, fontScale: Float = 1f, television: Boolean = false, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f, fontScale)) {
            ViptvTheme(television) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 3) = repeat(count) {
        scene.render(++frame * 16_666_667L).close(); Thread.sleep(1)
    }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
    private fun unmerged(scene: ImageComposeScene): List<SemanticsNode> = nodes(scene)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
        return results.single()
    }
    private fun capture(scene: ImageComposeScene, variant: String) {
        val dir = System.getenv("VIPTV_SOURCE_ROW_CAPTURE_DIR") ?: return
        File(dir).mkdirs()
        scene.render(++frame * 16_666_667L).use { image ->
            image.encodeToData()!!.use { File(dir, "$variant.png").writeBytes(it.bytes) }
        }
    }
}