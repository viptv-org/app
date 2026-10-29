package org.viptv.app

import java.io.File
import kotlin.test.*

class NoPlaceholderDialogsTest {
    @Test fun noSourceShowsTheNotAvailableInThisExperimentDialog() {
        // Gradle runs desktop tests from the composeApp module directory.
        val sources = File("src")
        assertTrue(sources.resolve("commonMain").isDirectory, "run from composeApp: ${sources.absolutePath}")
        val placeholder = Regex("""not\s+available\s+in\s+this\s+experiment""", RegexOption.IGNORE_CASE)
        val offenders = sources.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "kts", "xml", "js", "html") && !it.path.contains("${File.separator}desktopTest${File.separator}") }
            .filter { placeholder.containsMatchIn(it.readText()) }
            .map { it.relativeTo(sources).path }.toList()
        assertEquals(emptyList(), offenders, "placeholder dialog text found")
    }
}
