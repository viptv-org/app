package org.viptv.app

import java.io.File
import kotlin.test.*

class NoPlaceholderDialogsTest {
    @Test fun noSourceShowsTheNotAvailableInThisExperimentDialog() {
        offenders("""not\s+available\s+in\s+this\s+experiment""")
    }

    @Test fun noSourceOffersAControlThatReportsAMissingConnection() {
        offenders("""Saving channel favourites is not available|Creating pairing codes is not available|Code verification is not available|Resending email codes is not available|Email codes are not available|Playback preferences are not available on this connection|Subtitle preferences are not available on this connection""")
    }

    @Test fun noSettingsSectionFallsBackToAMissingConnectionNotice() {
        offenders("""[${'$'}]section is not available on this connection yet""")
    }

    private fun offenders(expression: String) {
        // Gradle runs desktop tests from the composeApp module directory.
        val sources = File("src")
        assertTrue(sources.resolve("commonMain").isDirectory, "run from composeApp: ${sources.absolutePath}")
        val placeholder = Regex(expression, RegexOption.IGNORE_CASE)
        val offenders = sources.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "kts", "xml", "js", "html") && !it.path.contains("${File.separator}desktopTest${File.separator}") }
            .filter { placeholder.containsMatchIn(it.readText()) }
            .map { it.relativeTo(sources).path }.toList()
        assertEquals(emptyList(), offenders, "placeholder dialog text found")
    }
}
