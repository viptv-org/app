@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.test.*
import org.junit.Rule
import org.viptv.app.features.sources.SourceRow
import org.viptv.app.features.sources.TvSourceInfo
import org.viptv.app.features.sources.TvSourceRow
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate

class SourceRowBoundsUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val longLabel = "1080p · BluRay · x265 10bit · DDP 5.1 640kbps + · HDR10 · extended edition"
    private val longDescription = "The.Release.Name.2026.1080p.BluRay.x265.10bit.DDP5.1.Atmos.HDR10-GROUP.mkv · " +
        "MKV · HEVC · EAC3 · 22.4 GB · extended edition with a tail no two-line window can hold and a second " +
        "sentence that keeps the description past two lines on a wide screen too"
    private val shortDescription = "One.Line.1080p.mkv"

    private fun long(id: String) = SourceCandidate(id, "addon", longLabel, detail = longDescription, quality_height = 1080L,
        release_type = "bluray", dynamic_range = listOf("dv"))

    private fun short(id: String) = SourceCandidate(id, "addon", "Short release", detail = shortDescription, quality_height = 2160L,
        release_type = "bluray", dynamic_range = listOf("dv"))

    @Test fun longTitlesKeepTheirInkClearOfTheFactBadges() {
        for (scale in listOf(0.5f, 1f)) for (fontScale in listOf(1f, 1.3f, 1.6f)) {
            val scene = scene(960, 400, fontScale) {
                Column(Modifier.padding(16.dp)) { TvSourceRow(long("tv"), TvSourceInfo("1080p", "MKV", "PenguPlay"), scale, null, true, {}) }
            }
            try {
                frames(scene)
                assertBadgesClearOfLabel(scene, longLabel, 10.dp * scale, 12.dp * scale, "TV scale=$scale fontScale=$fontScale")
                capture(scene, "tv-badges-$scale-$fontScale")
            } finally { scene.close() }
        }
        for (fontScale in listOf(1f, 1.3f)) {
            val scene = scene(420, 300, fontScale, television = false) {
                Column(Modifier.width(390.dp)) { SourceRow(long("phone"), false, {}) }
            }
            try {
                frames(scene)
                assertBadgesClearOfLabel(scene, longLabel, 5.dp, 6.dp, "phone fontScale=$fontScale")
                capture(scene, "phone-badges-$fontScale")
            } finally { scene.close() }
        }
    }

    private fun assertBadgesClearOfLabel(scene: ImageComposeScene, label: String, padding: Dp, gap: Dp, context: String) {
        val labelNode = nodes(scene).single { text(it) == label }
        val inkRight = labelNode.boundsInRoot.left + ceil(layoutOf(labelNode).getLineRight(0))
        val badge = nodes(scene).first { text(it) == "BluRay" }
        val badgeLeft = badge.boundsInRoot.left - padding.value
        assertTrue(badgeLeft + .5f >= inkRight + gap.value,
            "$context: the label paints to $inkRight but its first fact badge starts at $badgeLeft")
    }

    @Test fun qualityLabelsStayOnOneLineAndShareOneColumn() {
        for (scale in listOf(0.5f, 1f)) for (fontScale in listOf(1f, 1.3f, 1.6f)) {
            val scene = scene(960, 720, fontScale) {
                Column(Modifier.padding(16.dp)) {
                    TvSourceRow(short("a"), TvSourceInfo("1080p", "MKV", "PenguPlay"), scale, null, true, {})
                    TvSourceRow(short("b"), TvSourceInfo("4K"), scale, null, true, {})
                    TvSourceRow(short("c"), TvSourceInfo("720p"), scale, null, true, {})
                    TvSourceRow(short("d"), TvSourceInfo(null, "MKV"), scale, null, true, {})
                }
            }
            try {
                frames(scene)
                val where = "TV scale=$scale fontScale=$fontScale"
                val rows = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }
                assertEquals(4, rows.size)
                for ((quality, row) in listOf("1080p", "4K", "720p", "—").zip(rows)) {
                    val node = nodes(scene).single { text(it) == quality }
                    val layout = layoutOf(node)
                    assertEquals(1, layout.lineCount, "$where: the $quality quality label stays on one line")
                    assertTrue(ceil(layout.getLineRight(0)) <= node.size.width,
                        "$where: the $quality quality label paints inside its own bounds (ink ${layout.getLineRight(0)}, width ${node.size.width})")
                    assertTrue(node.boundsInRoot.left >= row.boundsInRoot.left && node.boundsInRoot.right <= row.boundsInRoot.right,
                        "$where: the $quality quality label stays inside its row")
                }
                val titles = nodes(scene).filter { text(it) == "Short release" }.map { it.boundsInRoot.left }
                assertEquals(1, titles.distinct().size, "$where: every quality label column is the same width")
                capture(scene, "tv-quality-$scale-$fontScale")
            } finally { scene.close() }
        }
    }

    @Test fun everyGlyphOfTheTwoDescriptionLinesIsPainted() {
        for (scale in listOf(0.5f, 1f)) for (fontScale in listOf(1f, 1.3f, 1.6f)) {
            val fontSize = 22.sp * scale
            val lineHeight = 28.sp * scale
            val scene = scene(960, 700, fontScale) {
                Column(Modifier.padding(16.dp)) {
                    TvSourceRow(long("a"), TvSourceInfo("1080p"), scale, null, true, {})
                    TvSourceRow(short("b"), TvSourceInfo("4K"), scale, null, true, {})
                }
            }
            try {
                frames(scene)
                val where = "TV scale=$scale fontScale=$fontScale"
                assertDescriptionFullyPainted(scene, longDescription, fontSize, lineHeight, fontScale, where)
                assertDescriptionFullyPainted(scene, shortDescription, fontSize, lineHeight, fontScale, where)
                capture(scene, "tv-description-$scale-$fontScale")
            } finally { scene.close() }
        }
        for (fontScale in listOf(1f, 1.3f)) {
            val scene = scene(420, 600, fontScale, television = false) {
                Column(Modifier.width(390.dp)) { SourceRow(long("p"), false, {}) }
            }
            try {
                frames(scene)
                assertDescriptionFullyPainted(scene, longDescription, 12.sp, 16.sp, fontScale, "phone fontScale=$fontScale")
                capture(scene, "phone-description-$fontScale")
            } finally { scene.close() }
        }
    }

    private fun assertDescriptionFullyPainted(scene: ImageComposeScene, description: String, fontSize: TextUnit, lineHeight: TextUnit,
        fontScale: Float, context: String) {
        val node = nodes(scene).single { text(it) == description }
        val window = 2 * lineHeight.value * fontScale
        val painted = bands(pixels(scene), node.positionInRoot.x.toInt(), node.size.width, node.positionInRoot.y.toInt(), window)
        val reference = bands(reference(description, node.size.width, fontSize, lineHeight, fontScale), 0, node.size.width, 0, window)
        assertEquals(reference.size, painted.size, "$context: the two-line window paints the same lines as the unclipped text")
        reference.zip(painted).forEach { (expected, actual) ->
            assertEquals(expected.first.toFloat(), actual.first.toFloat(), 1f, "$context: a description line starts where the unclipped text starts it")
            assertEquals(expected.last.toFloat(), actual.last.toFloat(), 1f, "$context: a description line ends where the unclipped text ends it")
        }
    }

    @Test fun rowsKeepOneHeightAndHoldTheirDescriptionWindow() {
        for (scale in listOf(0.5f, 1f)) for (fontScale in listOf(1f, 1.3f, 1.6f)) {
            val scene = scene(960, 900, fontScale) {
                Column(Modifier.padding(16.dp)) {
                    TvSourceRow(long("a"), TvSourceInfo("1080p"), scale, null, true, {})
                    TvSourceRow(short("b"), TvSourceInfo("4K"), scale, null, true, {})
                }
            }
            try {
                frames(scene)
                val where = "TV scale=$scale fontScale=$fontScale"
                val rows = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }
                assertEquals(2, rows.size)
                assertEquals(1, rows.map { it.boundsInRoot.height }.distinct().size, "$where: equal row heights")
                if (fontScale == 1f) assertEquals(138 * scale, rows.first().boundsInRoot.height, .5f,
                    "$where: the design row height is unchanged at the default font scale")
                val node = nodes(scene).single { text(it) == longDescription }
                val top = node.positionInRoot.y
                val bottom = top + 2 * 28 * scale * fontScale
                val row = rows.first { it.boundsInRoot.top <= top }
                assertTrue(top >= row.boundsInRoot.top && bottom <= row.boundsInRoot.bottom,
                    "$where: the two-line description window ${top.toInt()}..${bottom.toInt()} fits inside the row ${row.boundsInRoot}")
            } finally { scene.close() }
        }
    }

    @Test fun phoneRowsKeepTheirDesignHeightAtTheDefaultFontScale() {
        val scene = scene(420, 300, 1f, television = false) {
            Column(Modifier.width(390.dp)) { SourceRow(long("p"), false, {}) }
        }
        try {
            frames(scene)
            val row = nodes(scene).single { it.config.contains(SemanticsActions.OnClick) }
            assertEquals(98f, row.boundsInRoot.height, .5f)
        } finally { scene.close() }
    }

    private fun bands(image: BufferedImage, x0: Int, width: Int, top: Int, height: Float): List<IntRange> {
        val bands = mutableListOf<IntRange>()
        var start: Int? = null
        val last = (top + height).toInt()
        for (y in top until last) {
            val inked = (x0 until x0 + width).any { x -> luminance(image, x, y) > 120 }
            if (inked && start == null) start = y
            if (!inked && start != null) { bands += start!! - top..(y - 1 - top); start = null }
        }
        start?.let { bands += it - top..(last - 1 - top) }
        return bands
    }

    private fun reference(description: String, width: Int, fontSize: TextUnit, lineHeight: TextUnit, fontScale: Float): BufferedImage {
        val scene = ImageComposeScene(width, (4 * lineHeight.value * fontScale).toInt() + 40, Density(1f, fontScale)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                Text(description, Modifier.fillMaxWidth(), fontSize = fontSize, lineHeight = lineHeight, color = Palette.text.copy(alpha = .75f))
            } }
        }
        return try {
            repeat(2) { scene.render((it + 1) * 16_666_667L).close() }
            pixels(scene)
        } finally { scene.close() }
    }

    private fun scene(width: Int, height: Int, fontScale: Float = 1f, television: Boolean = true, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f, fontScale)) {
            ViptvTheme(television) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 3) = repeat(count) {
        scene.render(++frame * 16_666_667L).close(); Thread.sleep(1)
    }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
        return results.single()
    }
    private fun pixels(scene: ImageComposeScene): BufferedImage {
        val bytes = scene.render(++frame * 16_666_667L).use { image -> image.encodeToData()!!.use { it.bytes } }
        return ImageIO.read(ByteArrayInputStream(bytes))
    }
    private fun luminance(image: BufferedImage, x: Int, y: Int): Int {
        val rgb = image.getRGB(x, y)
        return ((rgb shr 16 and 0xFF) * 299 + (rgb shr 8 and 0xFF) * 587 + (rgb and 0xFF) * 114) / 1000
    }
    private fun capture(scene: ImageComposeScene, variant: String) {
        val dir = System.getenv("VIPTV_SOURCE_ROW_CAPTURE_DIR") ?: return
        File(dir).mkdirs()
        scene.render(++frame * 16_666_667L).use { image ->
            image.encodeToData()!!.use { File(dir, "$variant.png").writeBytes(it.bytes) }
        }
    }
}