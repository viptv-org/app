package org.viptv.app

import java.io.File
import kotlin.test.*

class ProviderDescriptionScanTest {
    private val floors = File("src").listFiles().orEmpty().filter { it.isDirectory }
        .map { File(it, "kotlin/org/viptv/app/features") }.filter { it.isDirectory }.toList()

    private val readsModelOnly = setOf("LiveRoute.kt", "PlaybackSlider.kt", "TvPlaybackTimeline.kt")

    @Test fun everyProviderDescriptionReachesTheScreenThroughTheSharedAccessor() {
        assertTrue(floors.isNotEmpty(), "run from the composeApp module")
        val escaped = Regex("""\.description\b(?!\.providerText)""")
        val found = floors.flatMap { floor -> floor.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { it.name !in readsModelOnly }
            .flatMap { file -> file.readLines().withIndex().filter { escaped.containsMatchIn(it.value) }.map { "${file.name}:${it.index + 1}" } }
        assertEquals(emptyList(), found, "provider descriptions render through String?.providerText()")
    }
}