package org.viptv.app

import java.io.File
import org.junit.Rule
import kotlin.test.*

class DeterministicImageLoaderGuardTest {
    @get:Rule val uiThread = UiThreadRule()

    private val root = File("src/desktopTest/kotlin")

    private fun sources() = root.walkTopDown().filter { it.extension == "kt" }

    private fun sceneTests() = sources().filter { file -> ("ImageComposeScene" + "(") in file.readText() }

    private fun servesImagesOverARealSocket(file: File) = "HttpServer" in file.readText()

    @Test fun everyDesktopSceneTestInstallsTheDeterministicImageLoader() {
        assertTrue(root.isDirectory, "run from composeApp: ${File(".").absolutePath}")
        val rule = "@get:Rule val deterministicImages = " + "DeterministicImageLoaderRule()"
        val missing = sceneTests()
            .filter { file -> rule !in file.readText() && !servesImagesOverARealSocket(file) }
            .map { it.path }.sorted().toList()
        assertEquals(emptyList(), missing, "Scene tests render artwork through Coil; install $rule (app#844)")
    }

    @Test fun noDesktopSceneTestPacesFramesWithRealTimeSleeps() {
        assertTrue(root.isDirectory, "run from composeApp: ${File(".").absolutePath}")
        val pacing = Regex("""\.close\(\)\s*;\s*Thread\.sleep\(""")
        val offenders = sceneTests()
            .filter { pacing.containsMatchIn(it.readText()) && !servesImagesOverARealSocket(it) }
            .map { it.path }.sorted().toList()
        assertEquals(emptyList(), offenders, "The deterministic loader settles artwork within the frame; a sleep only races it (app#844)")
    }
}