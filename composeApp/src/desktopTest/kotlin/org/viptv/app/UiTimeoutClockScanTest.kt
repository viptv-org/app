package org.viptv.app

import java.io.File
import kotlin.test.*

class UiTimeoutClockScanTest {
    private val floors = listOf("commonMain", "desktopMain", "androidMain")
        .map { File("src/$it/kotlin") }.filter { it.isDirectory }

    private val composable = Regex("@Composable")
    private val declaration = Regex("""\bfun\b""")
    private val delayCall = Regex("""(?<![A-Za-z0-9_])delay\(""")

    private fun sources() = floors.flatMap { floor -> floor.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun calls() = sources().flatMap { file -> delays(file).map { "${file.name}: $it" } }

    @Test fun aTimeoutTheViewerSeesCountsTheFramesTheSceneRenders() {
        assertTrue(floors.isNotEmpty(), "run from the composeApp module: ${File("src").absolutePath}")
        assertEquals(emptyList(), calls().filter { it !in dispatcherTime },
            "a visible timeout counts the frame clock through frameDelay(), not the dispatcher's delay()")
    }

    @Test fun everyDispatcherTimeExemptionIsStillNeeded() {
        val found = calls().toSet()
        assertEquals(emptyList(), dispatcherTime.filter { it !in found }, "drop the exemptions whose call is gone")
    }

    private fun delays(file: File): List<String> {
        val text = file.readText()
        val code = masked(text)
        val bodies = composableBodies(code)
        return if (bodies.isEmpty()) emptyList() else delayCall.findAll(code)
            .filter { call -> bodies.any { it.first < call.range.first && call.range.first < it.last } }
            .map { text.substring(it.range.first, matching(code, it.range.last) + 1) }
            .toList()
    }

    private fun composableBodies(code: String): List<IntRange> =
        composable.findAll(code).mapNotNull { annotation ->
            val function = declaration.find(code, annotation.range.last + 1) ?: return@mapNotNull null
            val between = code.substring(annotation.range.last + 1, function.range.first)
            if ("->" in between || '{' in between) return@mapNotNull null
            val parameters = code.indexOf('(', function.range.last + 1)
            if (parameters < 0) return@mapNotNull null
            val close = matching(code, parameters)
            if (code.substring(close + 1).trimStart().startsWith("=")) return@mapNotNull null
            val open = code.indexOf('{', close)
            if (open < 0) return@mapNotNull null
            IntRange(open, matching(code, open))
        }.toList()

    private fun matching(code: String, open: Int): Int {
        val close = when (code[open]) { '(' -> ')'; '{' -> '}'; else -> return open }
        var depth = 0
        for (index in open until code.length) {
            if (code[index] == code[open]) depth++
            else if (code[index] == close && --depth == 0) return index
        }
        return code.length - 1
    }

    private fun masked(source: String): String {
        val code = source.toCharArray()
        fun blank(from: Int, to: Int) { for (at in from until to) if (code[at] != '\n') code[at] = ' ' }
        var at = 0
        while (at < source.length) {
            if (source.startsWith("//", at)) {
                val end = source.indexOf('\n', at).let { if (it < 0) source.length else it }
                blank(at, end); at = end
            } else if (source.startsWith("/*", at)) {
                val end = source.indexOf("*/", at).let { if (it < 0) source.length else it + 2 }
                blank(at, end); at = end
            } else if (source.startsWith("\"\"\"", at)) {
                val end = source.indexOf("\"\"\"", at + 3).let { if (it < 0) source.length else it + 3 }
                blank(at, end); at = end
            } else if (source[at] == '"' || source[at] == '\'') {
                val quote = source[at]
                var end = at + 1
                while (end < source.length && source[end] != quote) end += if (source[end] == '\\') 2 else 1
                blank(at, (end + 1).coerceAtMost(source.length)); at = end + 1
            } else at++
        }
        return String(code)
    }

    // These count dispatcher time on purpose: they poll an engine, retry a request or measure the wall clock, and no viewer waits for a frame to see them.
    private val dispatcherTime = setOf(
        "ArtFailure.kt: delay(ART_RETRY_MILLIS * (attempts + 1))",
        "AutomaticEpisodes.kt: delay(150)",
        "CalendarScreen.kt: delay(CALENDAR_TICK_MILLIS)",
        "LibraryRoute.kt: delay(maxOf(wait, (4000L shl attempt).coerceAtMost(30_000L)).coerceIn(2_000L, 30_000L))",
        "LiveRoute.kt: delay(15_000)",
        "MpvPlayerSurface.desktop.kt: delay(25)",
        "MpvPlayerSurface.desktop.kt: delay(2_000)",
        "PlatformNativePlayerSurface.android.kt: delay(200)",
        "PlatformNativePlayerSurface.android.kt: delay(2000)",
        "PreviewTvPlayer.kt: delay(250)",
        "PreviewTvSeasons.kt: delay(2000)",
        "StremioImportScreen.kt: delay(15_000)",
        "StremioImportScreen.kt: delay(1_000)",
        "TvBillboardSelection.kt: delay(QuietPollMillis)",
        "TvHold.kt: delay(holdMillis)",
        "TvPlayerScreen.kt: delay(1000)",
        "UiPreview.kt: delay(3000)")
}