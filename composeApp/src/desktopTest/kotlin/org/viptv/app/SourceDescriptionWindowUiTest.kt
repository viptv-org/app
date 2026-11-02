@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.LocalReducedMotion
import org.viptv.app.features.sources.SOURCE_DESCRIPTION_DWELL_MILLIS
import org.viptv.app.features.sources.SOURCE_DESCRIPTION_LINE_MILLIS
import org.viptv.app.features.sources.SourceDescriptionWindow
import org.viptv.app.features.sources.SourceRow
import org.viptv.app.features.sources.TvSourceInfo
import org.viptv.app.features.sources.TvSourceRow
import org.viptv.app.features.sources.sourceDescriptionOffset
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import org.junit.Rule
import kotlin.math.abs
import kotlin.test.*

class SourceDescriptionWindowUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val long = "The.Release.Name.2026.2160p.WEB-DL.DV.HDR10.DDP5.1.Atmos.H.265-GROUP.mkv · MKV · HEVC · EAC3 · 14.2 GB · extended edition with a tail no two-line window can hold"
    private val unbroken = "https://files.example.invalid/" + "a".repeat(320) + "/bbbbbbbbbbbbbbbb.mkv"
    private val rows = listOf(
        SourceCandidate("blank", "addon", "Blank description"),
        SourceCandidate("one", "addon", "One line", detail = "One.Line.1080p.mkv"),
        SourceCandidate("long", "addon", "Long description", detail = long),
    )

    @Test fun theWindowDwellsThenTraversesOneLineAndResetsAtTheBottom() {
        val line = 10f
        assertEquals(0f, sourceDescriptionOffset(0, 3, line))
        assertEquals(0f, sourceDescriptionOffset(SOURCE_DESCRIPTION_DWELL_MILLIS - 1, 3, line))
        assertEquals(5f, sourceDescriptionOffset(SOURCE_DESCRIPTION_DWELL_MILLIS + SOURCE_DESCRIPTION_LINE_MILLIS / 2, 3, line))
        assertEquals(10f, sourceDescriptionOffset(SOURCE_DESCRIPTION_DWELL_MILLIS + SOURCE_DESCRIPTION_LINE_MILLIS, 3, line))
        val bottom = SOURCE_DESCRIPTION_DWELL_MILLIS + 3 * SOURCE_DESCRIPTION_LINE_MILLIS
        assertEquals(30f, sourceDescriptionOffset(bottom, 3, line), "the last line is revealed at one line per 1.5 s")
        assertEquals(30f, sourceDescriptionOffset(bottom + SOURCE_DESCRIPTION_DWELL_MILLIS - 1, 3, line))
        assertEquals(0f, sourceDescriptionOffset(bottom + SOURCE_DESCRIPTION_DWELL_MILLIS, 3, line), "the window resets to the top")
        assertEquals(0f, sourceDescriptionOffset(bottom + SOURCE_DESCRIPTION_DWELL_MILLIS + 1, 3, line))
        assertEquals(0f, sourceDescriptionOffset(4_000, 0, line), "a description that fits never moves")
    }

    @Test fun everyRowKeepsOneHeightWhateverTheDescription() {
        val phone = scene(390, 500) { Column(Modifier.width(390.dp)) { rows.forEach { SourceRow(it, false, {}) } } }
        val tv = scene(1920, 700, television = true) {
            Column(Modifier.width(1200.dp)) { rows.forEach { TvSourceRow(it, TvSourceInfo("4K"), 1f, null, true, {}) } }
        }
        try {
            val phoneHeights = render(phone) { rows.map { row -> rowNode(phone, row.label).boundsInRoot.height } }
            assertEquals(1, phoneHeights.distinct().size, "phone rows share one height: $phoneHeights")
            val tvHeights = render(tv) { rows.map { row -> rowNode(tv, row.label).boundsInRoot.height } }
            assertEquals(1, tvHeights.distinct().size, "TV rows share one height: $tvHeights")
        } finally { phone.close(); tv.close() }
    }

    @Test fun anUnbrokenDescriptionWrapsAndKeepsItsSecondLineInsideTheWindow() {
        val source = SourceCandidate("unbroken", "addon", "Unbroken", detail = unbroken)
        val phone = scene(390, 300) { Column(Modifier.width(390.dp)) { SourceRow(source, false, {}) } }
        val tv = scene(1920, 400, television = true) {
            Column(Modifier.width(1200.dp)) { TvSourceRow(source, TvSourceInfo("4K"), 1f, null, true, {}) }
        }
        try {
            val phoneLayout = render(phone) { layoutOf(phone, unbroken) }
            assertTrue(phoneLayout.lineCount > 2, "a long unbroken token wraps in the phone row: ${phoneLayout.lineCount} lines")
            assertFalse(phoneLayout.hasVisualOverflow, "the description lays out in full")
            val tvLayout = render(tv) { layoutOf(tv, unbroken) }
            assertFalse(tvLayout.hasVisualOverflow)
            assertTrue(tvLayout.lineCount > 2, "a long unbroken token wraps in the TV row: ${tvLayout.lineCount} lines")
        } finally { phone.close(); tv.close() }
    }

    @Test fun onlyTheActiveRowScrollsItsDescription() {
        var active by mutableStateOf(false)
        val scene = scene(320, 200) { Column(Modifier.width(320.dp).padding(top = 60.dp)) { SourceDescriptionWindow(long, active, 12.sp, 16.sp, Palette.text) } }
        try {
            val resting = render(scene) { descriptionTop(scene, long) }
            active = true
            frames(scene, 180)
            val scrolled = descriptionTop(scene, long)
            assertTrue(abs(resting - scrolled - 16f) < 2f, "one line of travel at 3 s: $resting -> $scrolled")
            active = false
            frames(scene, 4)
            assertEquals(resting, descriptionTop(scene, long), "blur puts the window back at the top")
        } finally { scene.close() }
    }

    @Test fun aHoveredPointerRowScrollsItsDescription() {
        val source = SourceCandidate("hover", "addon", "Hovered", detail = long)
        val scene = scene(390, 300, pointer = true) { Column(Modifier.width(390.dp)) { SourceRow(source, false, {}) } }
        try {
            val resting = render(scene) { descriptionTop(scene, long) }
            scene.sendPointerEvent(PointerEventType.Move, Offset(200f, 40f))
            frames(scene, 180)
            val scrolled = descriptionTop(scene, long)
            assertTrue(abs(resting - scrolled - 16f) < 2f, "the hovered row scrolls: $resting -> $scrolled")
            scene.sendPointerEvent(PointerEventType.Exit, Offset(200f, 40f))
            frames(scene, 4)
            assertEquals(resting, descriptionTop(scene, long), "pointer exit resets the window")
        } finally { scene.close() }
    }

    @Test fun reducedMotionKeepsTheDescriptionStill() {
        var active by mutableStateOf(false)
        val scene = scene(320, 200) { CompositionLocalProvider(LocalReducedMotion provides true) {
            Column(Modifier.width(320.dp).padding(top = 60.dp)) { SourceDescriptionWindow(long, active, 12.sp, 16.sp, Palette.text) }
        } }
        try {
            val resting = render(scene) { descriptionTop(scene, long) }
            active = true
            frames(scene, 300)
            assertEquals(resting, descriptionTop(scene, long), "reduced motion leaves the first two lines static")
        } finally { scene.close() }
    }

    private fun scene(width: Int, height: Int, television: Boolean = false, pointer: Boolean = false, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television, pointer = pointer) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int) {
        repeat(count) { scene.render(++frame * 16_666_667L).close(); Thread.sleep(1) }
    }
    private fun <T> render(scene: ImageComposeScene, check: () -> T): T { frames(scene, 2); return check() }
    private fun merged(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
    private fun unmerged(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun rowNode(scene: ImageComposeScene, label: String) =
        merged(scene).single { it.config.contains(SemanticsActions.OnClick) && text(it).contains(label) }
    private fun descriptionNode(scene: ImageComposeScene, description: String) = unmerged(scene).single { text(it) == description }
    private fun descriptionTop(scene: ImageComposeScene, description: String) = descriptionNode(scene, description).boundsInRoot.top
    private fun layoutOf(scene: ImageComposeScene, description: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(descriptionNode(scene, description).config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
        return results.single()
    }
}
