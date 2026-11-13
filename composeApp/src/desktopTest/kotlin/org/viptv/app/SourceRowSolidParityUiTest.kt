package org.viptv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Rule
import org.viptv.app.features.sources.SourceRow
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SourceRowSolidParityUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun rowsUsePlainSolidTvDetailsWithoutCacheOrLanguageBadges() {
        val source = SourceCandidate("fixture", "addon:fixture", "[TB+] Torrentio", detail = "[EMBER] Episode.mkv",
            quality_height = 1080, release_type = "web_dl", delivery = "direct", audio_languages = listOf("en", "ja"),
            subtitle_languages = listOf("en"), seeders = 12,
            presentation = SourcePresentation(provider_name = "Torrentio", size_label = "1.2 GB", video_codec = "HEVC"))
        for (width in listOf(320, 720)) {
            val scene = ImageComposeScene(width, 180, Density(1f)) { ViptvTheme {
                Column(Modifier.width(width.dp)) { SourceRow(source, false, {}, bestMatch = true) }
            } }
            fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
            fun labels() = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
                .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
            try {
                scene.renderUntil(what = "source row") { "EMBER" in labels() }
                System.getenv("VIPTV_SOURCE_PARITY_CAPTURE")?.let { path ->
                    File(path).mkdirs()
                    scene.render(100_000_000L).use { picture -> picture.encodeToData()!!.use {
                        File(path, "source-$width.png").writeBytes(it.bytes)
                    } }
                }
                val text = labels()
                assertFalse(text.any { "Instant" in it || "TorBox" in it || "Not cached" in it }, "No cache/service decoration: $text")
                assertTrue("Audio EN/JA · Subs EN" in text, "Plain audio/subtitle facts: $text")
                assertTrue("1.2 GB · HEVC · ↑12 · Torrentio" in text)
                assertTrue("Best match" in text)
                val row = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
                    .single { it.config.contains(SemanticsActions.OnClick) }
                assertTrue(row.boundsInRoot.height <= 80f, "Compact source rows must not inherit oversized default line heights: ${row.boundsInRoot}")
            } finally { scene.close() }
        }
    }
}
