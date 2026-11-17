@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.*
import kotlinx.serialization.json.*
import org.junit.Rule
import org.viptv.app.features.sources.SOURCE_TAGS
import org.viptv.app.features.sources.SourceRow
import org.viptv.app.features.sources.TvSourceInfo
import org.viptv.app.features.sources.TvSourceRow
import org.viptv.app.features.sources.addonText
import org.viptv.app.features.sources.providerLabel
import org.viptv.app.features.sources.qualityLabel
import org.viptv.app.features.sources.sizeLabel
import org.viptv.app.features.sources.sourceTags
import org.viptv.app.features.sources.sourceTextScroll
import org.viptv.app.features.sources.TEXT_DWELL_MILLIS
import org.viptv.app.features.sources.TEXT_LINE_STEP_MILLIS
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation

class SourceRowAddonTextUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private class Row(val stream: JsonObject, val candidate: SourceCandidate, val tags: List<String>, val size: String?)

    private val rows: List<Row> = fixture().map { raw ->
        val row = raw.jsonObject
        Row(row.getValue("stream").jsonObject, json.decodeFromJsonElement<SourceCandidate>(row.getValue("candidate").jsonObject),
            row.getValue("expect").jsonObject.getValue("tags").jsonArray.map { it.jsonPrimitive.content },
            row.getValue("expect").jsonObject["size"]?.jsonPrimitive?.contentOrNull)
    }

    @Test fun everyLayoutShowsTheAddOnTextVerbatimAndOnlyTheFixedTags() {
        for (layout in LAYOUTS) {
            val texts = layout.render()
            rows.forEach { row ->
                val source = row.candidate
                assertTrue(source.addonText()!! in texts, "${layout.name}: ${source.id} must show the add-on text verbatim")
                assertTrue(source.providerLabel() in texts, "${layout.name}: ${source.id} must show its provider")
                source.qualityLabel()?.let { assertTrue(it in texts, "${layout.name}: ${source.id} must show the quality $it") }
                row.tags.forEach { tag -> assertTrue(tag in texts, "${layout.name}: ${source.id} must show the $tag tag") }
                row.size?.let { assertTrue(it in texts, "${layout.name}: ${source.id} must show its size $it") }
                assertNoGuessedLabels(texts, "${layout.name}: ${source.id}")
            }
            assertEquals(rows.flatMap { it.tags }.toSet(), texts.filter { text -> SOURCE_TAGS.any { it.label == text } }.toSet(),
                "${layout.name}: the rows print only the fixed tag set")
            layout.capture()
        }
    }

    @Test fun theAddOnTextIsTheOnlyDescriptionTheRowDraws() {
        val texts = LAYOUTS.first().render()
        rows.forEach { row ->
            val stream = row.stream
            listOfNotNull(stream["title"]?.jsonPrimitive?.contentOrNull, stream["description"]?.jsonPrimitive?.contentOrNull,
                stream["behaviorHints"]?.jsonObject?.get("filename")?.jsonPrimitive?.contentOrNull).forEach { text ->
                text.lineSequence().map { it.trim() }.filter(String::isNotEmpty).forEach { line ->
                    assertTrue(line in row.candidate.addonText()!!, "${row.candidate.id} keeps the add-on's own line $line")
                }
            }
        }
        assertFalse(texts.any { "Audio not listed" in it || "Subs not listed" in it }, texts.toString())
        val languageRow = rows.first { it.candidate.id == "torrentio-eng-ita-languages" }
        assertTrue(languageRow.candidate.audio_languages.isNotEmpty(), "core reads the flag line as languages")
        assertEquals(emptyList(), languageRow.tags, "ENG/ITA in a release name and a flag line never become a chip")
        assertFalse(texts.any { it == "EN" || it == "IT" || it == "EN / IT" }, texts.toString())
    }

    @Test fun theFocusedRowsTextScrollsInsideItsTwoLineWindow() {
        val line = 24f
        val max = line * 2
        assertEquals(0f, sourceTextScroll(0, max, line), "the row starts at its first line")
        assertEquals(0f, sourceTextScroll(TEXT_DWELL_MILLIS - 1, max, line), "it dwells 1.5 s at the top")
        assertEquals(line / 2, sourceTextScroll(TEXT_DWELL_MILLIS + TEXT_LINE_STEP_MILLIS / 2, max, line), "then one line per 1.5 s")
        assertEquals(max, sourceTextScroll(TEXT_DWELL_MILLIS + TEXT_LINE_STEP_MILLIS * 2, max, line), "it stops at the last line")
        val cycle = TEXT_DWELL_MILLIS * 2 + TEXT_LINE_STEP_MILLIS * 2
        assertEquals(max, sourceTextScroll(cycle - 1, max, line), "it dwells at the bottom")
        assertEquals(0f, sourceTextScroll(cycle, max, line), "then resets to the top")
        assertEquals(0f, sourceTextScroll(5_000, 0f, line), "a two-line text never moves")

        val source = SourceCandidate("scroll", "addon:torrentio", "Torrentio\n2160p",
            detail = "Line one of the release\nLine two of the release\nLine three of the release\nLine four of the release",
            quality_height = 2160, presentation = SourcePresentation(provider_name = "Torrentio"))
        val scene = ImageComposeScene(390, 200, Density(1f)) {
            ViptvTheme(false) { Box(Modifier.fillMaxSize().background(Palette.background)) {
                Column(Modifier.width(390.dp)) { SourceRow(source, television = false, choose = {}) }
            } }
        }
        fun textTop(): Float = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            .first { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == source.detail } }.positionInRoot.y
        try {
            var frame = 0L
            repeat(4) { scene.render(frame++ * 16_666_667L).close() }
            val atRest = textTop()
            scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
                .first { it.config.contains(SemanticsActions.RequestFocus) }.config[SemanticsActions.RequestFocus].action!!.invoke()
            repeat(180) { scene.render(frame++ * 16_666_667L).close() }
            assertTrue(textTop() < atRest, "a focused row moves its text up inside the stationary window: ${textTop()} vs $atRest")
        } finally { scene.close() }
    }

    @Test fun tagsComeOnlyFromTheNormalizedReleaseFacts() {
        val base = SourceCandidate("a", "p", "A")
        assertEquals(emptyList(), base.sourceTags())
        assertEquals(listOf("Dubbed"), base.copy(audio_flags = listOf("dubbed")).sourceTags())
        assertEquals(listOf("Multi audio"), base.copy(audio_flags = listOf("multi")).sourceTags())
        assertEquals(listOf("Multi audio"), base.copy(audio_flags = listOf("dual")).sourceTags())
        assertEquals(listOf("Dubbed", "Multi audio"), base.copy(audio_flags = listOf("dubbed", "multi", "dual")).sourceTags())
        assertEquals(listOf("HDR"), base.copy(dynamic_range = listOf("hdr10")).sourceTags())
        assertEquals(listOf("HDR"), base.copy(dynamic_range = listOf("hdr10_plus")).sourceTags())
        assertEquals(listOf("HDR"), base.copy(dynamic_range = listOf("hlg")).sourceTags())
        assertEquals(listOf("HDR", "Dolby Vision"), base.copy(dynamic_range = listOf("dv", "hdr10", "hdr")).sourceTags())
        assertEquals(listOf("Remux"), base.copy(release_type = "remux").sourceTags())
        assertEquals(listOf("3D"), base.copy(three_d = true).sourceTags())
        assertEquals(listOf("CAM"), base.copy(release_type = "cam").sourceTags())
        assertEquals(emptyList(), base.copy(release_type = "bluray").sourceTags(), "a release type alone is not a tag")
        assertEquals(listOf("Dubbed", "Multi audio", "HDR", "Dolby Vision", "Remux", "3D"),
            base.copy(audio_flags = listOf("dubbed", "multi"), dynamic_range = listOf("dv", "hdr"), release_type = "remux", three_d = true).sourceTags())
    }

    @Test fun sizeComesOnlyFromTheDeclaredSizeOrTheAddOnsOwnSizeText() {
        val base = SourceCandidate("a", "p", "A")
        assertNull(base.sizeLabel())
        assertNull(base.copy(detail = "Show 2024 1080p 12.4 GB").sizeLabel(), "a size word in the release name is not the size")
        assertEquals("6.68 GB", base.copy(detail = "Show 2024\n👤 222 💾 6.68 GB ⚙️ Site").sizeLabel())
        assertEquals("2.4 GB", base.copy(presentation = SourcePresentation(size_bytes = 2_576_980_377L)).sizeLabel())
        assertEquals("2.4 GB", base.copy(detail = "Show 2024\n👤 222 💾 6.68 GB ⚙️ Site",
            presentation = SourcePresentation(size_bytes = 2_576_980_377L)).sizeLabel(), "the declared size wins")
    }

    private fun assertNoGuessedLabels(texts: List<String>, where: String) {
        val sizes = rows.mapNotNull { it.size }.toSet()
        val tags = SOURCE_TAGS.map { it.label }
        val guessed = texts.filter { text ->
            text !in tags && (text == "Season pack" || text == "10-bit" || text.startsWith("Audio ") || text.startsWith("Subs ") ||
                text.startsWith("↑") || LANGUAGE_CODES.matches(text) || (SIZE_TEXT.matches(text) && text !in sizes))
        }
        assertTrue(guessed.isEmpty(), "$where shows guessed labels: $guessed")
    }

    private inner class Layout(val name: String, val rowWidth: Int, val television: Boolean = false) {
        fun render(): List<String> {
            val scene = scene()
            try {
                repeat(4) { scene.render(it * 16_666_667L).close() }
                val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
                val heights = nodes.filter { it.config.contains(SemanticsActions.OnClick) }.map { it.boundsInRoot.height }.distinct()
                assertEquals(1, heights.size, "$name rows must share one height: $heights")
                return nodes.flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            } finally { scene.close() }
        }

        fun capture() {
            val dir = System.getenv("VIPTV_SOURCE_TEXT_CAPTURE_DIR")?.let(::File) ?: return
            val scene = scene()
            try {
                repeat(4) { scene.render(it * 16_666_667L).close() }
                dir.mkdirs()
                scene.render(4 * 16_666_667L).use { image ->
                    image.encodeToData()!!.use { File(dir, "$name.png").writeBytes(it.bytes) }
                }
            } finally { scene.close() }
        }

        private fun scene(): ImageComposeScene {
            val sources = rows.map { it.candidate }
            return ImageComposeScene(rowWidth, 2400, Density(1f)) {
                ViptvTheme(television) {
                    Box(Modifier.fillMaxSize().background(Palette.background)) {
                        Column(Modifier.width(rowWidth.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sources.forEach { source ->
                                if (television) TvSourceRow(source, TvSourceInfo(source.qualityLabel()), 1f, null, true, {})
                                else SourceRow(source, television = false, choose = {})
                            }
                        }
                    }
                }
            }
        }
    }

    private val LAYOUTS get() = listOf(
        Layout("phone", 390),
        Layout("tablet", 461),
        Layout("desktop", 720),
        Layout("web", 720),
        Layout("tv", 1200, television = true),
    )

    private companion object {
        val LANGUAGE_CODES = Regex("""[A-Z]{2,3}( / [A-Z]{2,3})*""")
        val SIZE_TEXT = Regex("""[0-9.]+ (MB|GB|TB)""")
        val json = Json { ignoreUnknownKeys = true }

        fun fixture(): List<JsonElement> {
            val classpath = SourceRowAddonTextUiTest::class.java.getResourceAsStream("/source-streams/captured.json")
            val text = classpath?.bufferedReader()?.use { it.readText() }
                ?: File("src/desktopTest/resources/source-streams/captured.json").readText()
            return json.parseToJsonElement(text).jsonObject.getValue("rows").jsonArray.toList()
        }
    }
}
