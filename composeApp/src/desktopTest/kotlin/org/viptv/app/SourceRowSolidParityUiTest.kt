package org.viptv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
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
        val release = "[EMBER] Episode 2024 1080p WEB-DL DDP5.1 H.264\n[EMBER] Episode.mkv\n👤 12 💾 1.2 GB ⚙️ Site\n🇬🇧 / 🇯🇵"
        val source = SourceCandidate("fixture", "addon:fixture", "[TB+] Torrentio", detail = release,
            quality_height = 1080, release_type = "web_dl", delivery = "direct", audio_languages = listOf("en", "ja"),
            subtitle_languages = listOf("en"), seeders = 12, dynamic_range = listOf("hdr"),
            presentation = SourcePresentation(provider_name = "Torrentio", size_bytes = 1_288_490_189L, video_codec = "AVC"))
        for (width in listOf(320, 720)) {
            val scene = ImageComposeScene(width, 220, Density(1f)) { ViptvTheme {
                Column(Modifier.width(width.dp)) { SourceRow(source, false, {}, bestMatch = true) }
            } }
            fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
            fun labels() = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
                .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
            try {
                scene.renderUntil(what = "source row") { release in labels() }
                System.getenv("VIPTV_SOURCE_PARITY_CAPTURE")?.let { path ->
                    File(path).mkdirs()
                    scene.render(100_000_000L).use { picture -> picture.encodeToData()!!.use {
                        File(path, "source-$width.png").writeBytes(it.bytes)
                    } }
                }
                val text = labels()
                assertTrue(release in text, "The add-on's own lines, verbatim: $text")
                assertTrue("Torrentio" in text, "The provider: $text")
                assertTrue("HDR" in text, "The fixed tag: $text")
                assertTrue("1.2 GB" in text, "The add-on's own size: $text")
                assertTrue("Best match" in text)
                assertFalse(text.any { "Instant" in it || "TorBox" in it || "Not cached" in it }, "No cache/service decoration: $text")
                assertFalse(text.any { "Audio " in it || "Subs" in it || "EN" == it || "JA" == it || "WEB-DL" == it || "AVC" == it }, "No guessed labels: $text")
                val row = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
                    .single { it.config.contains(SemanticsActions.OnClick) }
                assertTrue(row.boundsInRoot.height <= 110f, "Compact source rows: ${row.boundsInRoot}")
            } finally { scene.close() }
        }
    }
}
