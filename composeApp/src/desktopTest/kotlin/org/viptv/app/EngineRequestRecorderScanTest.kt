package org.viptv.app

import java.io.File
import kotlin.test.*

// #924's ParentRequiredStateTest threw ConcurrentModificationException because its MockEngine handler appended to an
// ArrayList while the test thread iterated it: record what the engine saw in EngineRequests, never a plain list.
class EngineRequestRecorderScanTest {
    private val tests = File("src/desktopTest/kotlin")

    private val plainList = Regex(
        """(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*(?:MutableList|MutableSet|ArrayList|LinkedHashSet)[^=\n]*)?=\s*""" +
            """(?:mutableListOf|arrayListOf|mutableSetOf|linkedSetOf|ArrayList|LinkedHashSet)\s*<[^>]*>\s*\(\s*\)""")
    private val adHocThreadSafe = Regex(
        """(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*[^=\n]+)?=\s*(?:[A-Za-z0-9_.]*\.)?""" +
            """(?:CopyOnWriteArrayList|ConcurrentLinkedQueue|synchronizedList\s*\(\s*mutableListOf)\s*<[^>]*>\s*\(?""")
    private val synchronizedAppend = Regex("""synchronized\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)\s*\{[^}]*\b\1\s*\+=""")
    private val requestHandler = Regex("""\{\s*(?:[A-Za-z_][A-Za-z0-9_]*\s*:\s*[^->{]*->|(?:request|req)\s*->)""")
    private val requestUse = Regex("""\b(?:request|req)\.(?:url|method|body|headers|attributes)\b""")

    @Test fun noFileReachesForItsOwnThreadSafeRecorder() {
        assertTrue(tests.isDirectory, "run from the composeApp module: ${tests.absolutePath}")
        assertEquals(emptyList(), adHoc().filter { it !in dispatcherObservation },
            "record what the MockEngine handler saw in EngineRequests<…>, not in a CopyOnWriteArrayList, ConcurrentLinkedQueue or synchronizedList")
    }

    @Test fun everyDispatcherObservationExemptionIsStillNeeded() {
        val found = adHoc().toSet()
        assertEquals(emptyList(), dispatcherObservation.filter { it !in found }, "drop the exemptions whose recorder is gone")
    }

    @Test fun noFileWrapsItsAppendsInSynchronized() {
        assertTrue(tests.isDirectory, "run from the composeApp module: ${tests.absolutePath}")
        assertEquals(emptyList(), synchronizedAppends(),
            "EngineRequests is already safe to append to from the engine thread; drop the synchronized(…) wrapper")
    }

    @Test fun theEngineThreadNeverAppendsToTheListTheTestReads() {
        assertTrue(tests.isDirectory, "run from the composeApp module: ${tests.absolutePath}")
        assertEquals(emptyList(), recorders(),
            "record what the MockEngine handler saw in EngineRequests<…>, not in a plain list the engine thread appends to")
    }

    private fun recorders(): List<String> = sources().flatMap { file ->
        val code = masked(file.readText())
        val declared = plainList.findAll(code).map { it.groupValues[1] }.toSet()
        if (declared.isEmpty()) return@flatMap emptyList()
        handlerBodies(code).flatMap { body ->
            declared.filter { name -> appends(body, name) }.map { name -> "${file.name}: $name" }
        }
    }.distinct()

    // Records which thread ran a dispatch, not what the engine answered: the test asserts the work left the main thread.
    private val dispatcherObservation = setOf("RemoteDecodeOffMainTest.kt: runThreads")

    private fun sources(): List<File> = tests.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun adHoc(): List<String> = sources().filter { it.name != "EngineRequests.kt" }.flatMap { file ->
        val code = masked(file.readText())
        if (!code.contains("MockEngine")) return@flatMap emptyList()
        adHocThreadSafe.findAll(code).map { "${file.name}: ${it.groupValues[1]}" }.toList()
    }

    private fun synchronizedAppends(): List<String> = sources().filter { it.name != "EngineRequests.kt" }.flatMap { file ->
        val code = masked(file.readText())
        if (!code.contains("MockEngine")) return@flatMap emptyList()
        synchronizedAppend.findAll(code).map { "${file.name}: ${it.groupValues[1]}" }.toList()
    }

    private fun handlerBodies(code: String): List<String> = requestHandler.findAll(code).mapNotNull { handler ->
        val open = code.lastIndexOf('{', handler.range.first)
        if (open < 0) return@mapNotNull null
        val body = code.substring(open, matching(code, open) + 1)
        body.takeIf { requestUse.containsMatchIn(it) }
    }.toList()

    private fun appends(body: String, name: String): Boolean =
        Regex("""(?<![A-Za-z0-9_.])${Regex.escape(name)}\s*\+=""").containsMatchIn(body) ||
            Regex("""(?<![A-Za-z0-9_.])${Regex.escape(name)}\.add\s*\(""").containsMatchIn(body)

    private fun matching(code: String, open: Int): Int {
        val close = when (code[open]) { '(' -> ')'; '{' -> '}'; '[' -> ']'; else -> return open }
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
}
