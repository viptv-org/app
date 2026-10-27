package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.jetbrains.skia.Bitmap
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.pointerSelectable
import org.viptv.app.components.pointerToggleable
import org.viptv.app.features.calendar.CalendarDates
import org.viptv.app.features.calendar.CalendarDay
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.awt.EventQueue
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

/**
 * app#56: every pointer-clickable control shows the hand cursor, and a shaped
 * control's hover highlight stays inside its shape (no square highlight on a
 * rounded pill, the owner's Calendar report).
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class PointerAffordanceTest {
    private fun cursor(modifier: Modifier): PointerIcon? = modifier.foldIn<PointerIcon?>(null) { found, element ->
        found ?: (element as? InspectableValue)?.takeIf { it.nameFallback == "pointerHoverIcon" }
            ?.inspectableElements?.firstOrNull { it.name == "icon" }?.value as? PointerIcon
    }

    @Test fun theSharedClickableModifiersSetTheHandCursor() {
        assertEquals(PointerIcon.Hand, cursor(Modifier.pointerClickable {}))
        assertEquals(PointerIcon.Hand, cursor(Modifier.pointerClickable(shape = RoundedCornerShape(8.dp)) {}))
        assertEquals(PointerIcon.Hand, cursor(Modifier.pointerClickable(null, null) {}))
        assertEquals(PointerIcon.Hand, cursor(Modifier.pointerSelectable(true) {}))
        assertEquals(PointerIcon.Hand, cursor(Modifier.pointerToggleable(true) {}))
        assertNull(cursor(Modifier.pointerClickable(enabled = false) {}), "a disabled control keeps the arrow")
    }

    private val edt = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = !EventQueue.isDispatchThread()
        override fun dispatch(context: CoroutineContext, block: Runnable) = EventQueue.invokeLater(block)
    }
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(edt, block)

    private class Frames(val scene: ImageComposeScene) {
        var frame = 0L
        fun pixels(): Bitmap {
            repeat(3) { scene.render(frame++ * 16_666_667L).close() }
            return scene.render(frame++ * 16_666_667L).use { Bitmap.makeFromImage(it) }
        }
    }

    /** Hovers [bounds] and checks its centre lights up while the corner just outside the rounding does not. */
    private fun assertHighlightStaysInsideTheShape(frames: Frames, bounds: Rect, what: String) {
        val corner = (bounds.left + 1).toInt() to (bounds.top + 1).toInt()
        val centre = bounds.center.x.toInt() to bounds.center.y.toInt()
        val before = frames.pixels()
        frames.scene.sendPointerEvent(PointerEventType.Move, bounds.center)
        val after = frames.pixels()
        assertNotEquals(before.getColor(centre.first, centre.second), after.getColor(centre.first, centre.second),
            "$what: hovering lights up the control")
        assertEquals(before.getColor(corner.first, corner.second), after.getColor(corner.first, corner.second),
            "$what: the corner outside the rounded shape stays unhighlighted")
    }

    @Test fun aRoundedHoveredControlKeepsItsCornersUnhighlighted() = onEdt {
        val scene = ImageComposeScene(240, 120, Density(1f)) {
            ViptvTheme(pointer = true) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    val pill = RoundedCornerShape(24.dp)
                    Box(Modifier.offset(40.dp, 36.dp).size(160.dp, 48.dp).background(Palette.surface, pill)
                        .pointerClickable(shape = pill) {})
                }
            }
        }
        try {
            val frames = Frames(scene)
            assertEquals(Palette.background.toArgb(), frames.pixels().getColor(41, 37), "the corner is page ground")
            assertHighlightStaysInsideTheShape(frames, Rect(Offset(40f, 36f), androidx.compose.ui.geometry.Size(160f, 48f)), "pill")
        } finally { scene.close() }
    }

    @Test fun aHoveredCalendarDayIsHighlightedAsAPillNotASquare() = onEdt {
        val days = listOf(CalendarDay("2026-10-26", "Mon", "26", "Today", "Monday 26 October"),
            CalendarDay("2026-10-27", "Tue", "27", "Monday", "Tuesday 27 October"))
        val scene = ImageComposeScene(800, 200, Density(1f)) {
            ViptvTheme(pointer = true) {
                Box(Modifier.fillMaxSize().background(Palette.background).padding(20.dp)) {
                    CalendarDates(days, emptyList(), selected = days[0].id, phone = false, scale = .5f) {}
                }
            }
        }
        try {
            val frames = Frames(scene); frames.pixels()
            val day = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }.first {
                it.config.contains(SemanticsActions.OnClick) &&
                    it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == "Monday" }
            }
            assertHighlightStaysInsideTheShape(frames, day.boundsInRoot, "Calendar day")
        } finally { scene.close() }
    }

    /**
     * New UI gets the hand cursor by default: a raw `clickable` / `selectable` /
     * `toggleable`, or a Material clickable without the cursor wrapper import,
     * fails here.
     */
    @Test fun everyClickableInTheAppGoesThroughThePointerAffordances() {
        val roots = listOf("commonMain", "desktopMain", "wasmJsMain").map { File("src/$it/kotlin") }
        assertTrue(roots.first().isDirectory, "run from composeApp: ${File(".").absolutePath}")
        val raw = Regex("""\.(clickable|selectable|toggleable|combinedClickable)\s*[({]""")
        val material = listOf("Button", "OutlinedButton", "TextButton", "IconButton", "Checkbox", "Switch",
            "FilterChip", "DropdownMenuItem", "Tab")
        val problems = mutableListOf<String>()
        roots.flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }.forEach { file ->
            if (file.name in setOf("PointerAffordance.kt", "PointerMaterial.kt")) return@forEach
            val text = file.readText()
            raw.findAll(text).forEach { problems += "${file.path}: raw .${it.groupValues[1]} — use pointer${it.groupValues[1].replaceFirstChar(Char::uppercase)}" }
            if (text.contains("package org.viptv.app.components\n")) return@forEach
            val used = material.filter { Regex("""(?<![\w.])$it\(""").containsMatchIn(text) && !text.contains("fun $it(") } +
                listOfNotNull("Surface".takeIf { Regex("""(?<![\w.])Surface\(\s*onClick""").containsMatchIn(text) })
            used.filterNot { text.contains("import org.viptv.app.components.$it\n") }
                .forEach { problems += "${file.path}: $it( without import org.viptv.app.components.$it" }
            Regex("""androidx\.compose\.material3\.(${material.joinToString("|")})\(""").findAll(text)
                .forEach { problems += "${file.path}: qualified material3.${it.groupValues[1]} skips the hand cursor" }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }
}
