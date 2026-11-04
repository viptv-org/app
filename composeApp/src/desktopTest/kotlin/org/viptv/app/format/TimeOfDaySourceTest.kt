package org.viptv.app.format

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeOfDaySourceTest {
    @Test
    fun onlyTheSharedFormatterBuildsTimeOfDayText() {
        val sources = File("src")
        assertTrue(sources.resolve("commonMain").isDirectory, "run from composeApp: ${sources.absolutePath}")
        val patterns = listOf("h:mm", "H:mm", "toLocaleTimeString")
        val offenders = sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { !it.path.contains("${File.separator}desktopTest${File.separator}") && it.name != "TimeOfDay.kt" }
            .flatMap { file -> patterns.filter { file.readText().contains(it) }.map { "${file.path}: $it" }.asSequence() }
            .toList()
        assertEquals(emptyList(), offenders, "time-of-day text outside the shared formatter")
    }
}
