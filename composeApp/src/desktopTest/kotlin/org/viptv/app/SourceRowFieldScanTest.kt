package org.viptv.app

import java.io.File
import kotlin.test.*

class SourceRowFieldScanTest {
    @Test fun noSourceRowDisplayReadsLanguagesCodecsTrackersOrGroups() {
        val offenders = displayFiles().flatMap { file ->
            val text = file.readText()
            if (!text.contains("SourceCandidate")) return@flatMap emptyList()
            val found = FORBIDDEN.filter { token -> token in text }
            val rowCode = file.path.contains("/features/sources/") && file.name != "SourcePresentation.kt"
            val detail = if (!rowCode) emptyList()
                else text.lineSequence().withIndex().filter { (_, line) -> DETAIL_READ.containsMatchIn(line) }
                    .map { "reads detail on line ${it.index + 1}" }.toList()
            (found + detail).map { "${file.path}: $it" }
        }
        assertTrue(offenders.isEmpty(), "source rows must read only the add-on's text and the fixed tag facts:\n${offenders.joinToString("\n")}")
    }

    @Test fun theFixedTagSetIsTheOnlyThingTheRowPrints() {
        val tags = org.viptv.app.features.sources.SOURCE_TAGS.map { it.label }
        assertEquals(listOf("Dubbed", "Multi audio", "HDR", "Dolby Vision", "Remux", "3D", "CAM"), tags)
    }

    private fun displayFiles(): List<File> = SOURCE_SETS.flatMap { set ->
        File("src/$set/kotlin/org/viptv/app/features").takeIf(File::isDirectory)
            ?.walkTopDown()?.filter { it.isFile && it.extension == "kt" }?.toList().orEmpty()
    }

    private companion object {
        val SOURCE_SETS = listOf("commonMain", "desktopMain", "androidMain", "iosMain", "wasmJsMain")
        val FORBIDDEN = listOf(
            "audio_languages", "subtitle_languages", ".subbed", "video_codec", "audio_codecs",
            "presentation?.container", "presentation.container", ".seeders", ".size_label",
            "parseTracker", "parseReleaseGroup", "releaseGroup", "seasonPack", "bitDepth",
            "rowAudioSubs", "audioChips", "subtitleChips", "factBadges", "factItems",
        )
        val DETAIL_READ = Regex("""\.detail\b""")
    }
}
