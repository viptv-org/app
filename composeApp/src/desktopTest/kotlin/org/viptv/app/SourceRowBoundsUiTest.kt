@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.*
import kotlinx.serialization.json.*
import org.junit.Rule
import org.viptv.app.connection.withReleaseFacts
import org.viptv.app.features.sources.CacheChip
import org.viptv.app.features.sources.CacheKind
import org.viptv.app.features.sources.LanguageChip
import org.viptv.app.features.sources.SourceRow
import org.viptv.app.features.sources.TvSourceInfo
import org.viptv.app.features.sources.TvSourceRow
import org.viptv.app.features.sources.audioChips
import org.viptv.app.features.sources.cacheChip
import org.viptv.app.features.sources.factItems
import org.viptv.app.features.sources.parseReleaseGroup
import org.viptv.app.features.sources.parseTracker
import org.viptv.app.features.sources.qualityLabel
import org.viptv.app.features.sources.releaseGroupLabel
import org.viptv.app.features.sources.seasonPack
import org.viptv.app.features.sources.subtitleChips
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation

class SourceRowBoundsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun releaseGroupIsParsedFromTheLeadingBracketOrTrailingGroup() {
        assertEquals("EMBER", parseReleaseGroup("[EMBER] The Genius Prince's Guide (Batch)\nS01E03.mkv"))
        assertEquals("Tsundere-Raws", parseReleaseGroup("Show.S01E03.1080p-Tsundere-Raws.mkv"))
        assertNull(parseReleaseGroup("S01E03-A Marriage as a Pretext [EA9B5806].mkv"))
        assertNull(parseReleaseGroup(null))
    }

    @Test fun trackerIsParsedFromTheSeederLine() {
        assertEquals("NyaaSi", parseTracker("S01E03.mkv\n👤 62 💾 481.6 MB ⚙️ NyaaSi"))
        assertNull(parseTracker("S01E03.mkv"))
    }

    @Test fun cacheChipReadsDebridTagsThenDelivery() {
        assertEquals(CacheChip(CacheKind.Instant, "TorBox"), cacheChip("[TB+] Torrentio\n1080p", "direct"))
        assertEquals(CacheChip(CacheKind.Instant, "Real-Debrid"), cacheChip("[RD+] Torrentio\n1080p", "direct"))
        assertEquals(CacheChip(CacheKind.Instant, "AllDebrid"), cacheChip("[AD+] Torrentio\n1080p", "direct"))
        assertEquals(CacheChip(CacheKind.Instant, "Premiumize"), cacheChip("[PM+] Torrentio\n1080p", "direct"))
        assertEquals(CacheChip(CacheKind.Instant, "Debrid-Link"), cacheChip("[DL+] Torrentio\n1080p", "direct"))
        assertEquals(CacheChip(CacheKind.NotCached, "TorBox"), cacheChip("[TB download] Torrentio\n1080p", "torrent"))
        assertEquals(CacheChip(CacheKind.Instant, "Real-Debrid"), cacheChip("🕵️ [RD⚡] MediaFusion 1080p", "direct"))
        assertEquals(CacheChip(CacheKind.NotCached, "Real-Debrid"), cacheChip("🕵️ [RD⏳] MediaFusion", "direct"))
        assertEquals(CacheChip(CacheKind.Instant, null), cacheChip("⚡ AniKage • English DUB", "direct"))
        assertNull(cacheChip("Torrentio\n1080p", "torrent"))
        assertNull(cacheChip("TorrentsDB\nbdrip", "torrent"))
    }

    @Test fun seasonPackMatchesBatchAndFullSeasonMarkers() {
        fun packs(detail: String?) = SourceCandidate("a", "p", "A", detail = detail).seasonPack()
        assertTrue(packs("Tensai Ouji no Akaji (2022) [Batch]"))
        assertTrue(packs("Tensai Ouji no Akaji (2022) (Batch)"))
        assertTrue(packs("Tensai Ouji no Akaji (2022) \"Batch\""))
        assertTrue(packs("Tensai Ouji no Akaji S01 complete"))
        assertTrue(packs("Tensai Ouji no Akaji Season 1-12"))
        assertTrue(packs("Tensai Ouji no Akaji S01E01-E12"))
        assertFalse(packs("Tensai Ouji no Akaji S01E03"))
        assertFalse(packs("Oak Street (2022)"))
    }

    @Test fun audioAndSubtitleChipsKeepLanguageEvidenceSeparate() {
        val source = SourceCandidate("a", "p", "Relax")
        assertNull(source.audioChips())
        assertNull(source.subtitleChips())
        assertEquals(listOf(LanguageChip("Dual audio", true)),
            source.copy(audio_flags = listOf("dual", "dubbed")).audioChips())
        assertEquals(listOf(LanguageChip("Dubbed", true)), source.copy(audio_flags = listOf("dubbed")).audioChips())
        assertEquals(listOf(LanguageChip("Multi audio", true)), source.copy(audio_flags = listOf("multi")).audioChips())
        assertEquals(listOf(LanguageChip("FR", false)),
            source.copy(label = "Johnny English", audio_languages = listOf("fr"), audio_flags = listOf("dual", "dubbed")).audioChips())
        assertNull(source.copy(label = "Johnny English", subtitle_languages = listOf("en")).audioChips())
        assertEquals(listOf(LanguageChip("RU", false)),
            source.copy(audio_languages = listOf("ru")).audioChips())
        assertEquals(listOf(LanguageChip("EN dub", false), LanguageChip("JA", false)),
            source.copy(audio_languages = listOf("en", "ja"), audio_flags = listOf("dual", "dubbed")).audioChips())
        assertEquals(listOf(LanguageChip("EN", false)), source.copy(audio_languages = listOf("en")).audioChips())
        assertEquals(listOf(LanguageChip("EN", false)), source.copy(audio_languages = listOf("en"), audio_flags = listOf("dual")).audioChips())
        assertEquals(listOf(LanguageChip("EN dub", false)), source.copy(audio_languages = listOf("en"), audio_flags = listOf("dual", "dubbed")).audioChips())
        assertEquals(listOf(LanguageChip("EN", false), LanguageChip("ES", false)),
            source.copy(subtitle_languages = listOf("en", "es")).subtitleChips())
        assertEquals(listOf(LanguageChip("Subs", true)), source.copy(subbed = true).subtitleChips())
    }

    @Test fun factsOmitMissingFactsAndKeepTheReleaseGroup() {
        val full = torrent()
        assertEquals(listOf("481.6 MB", "HEVC 10-bit", "↑62", "NyaaSi", "Torrentio", "Season pack"), full.factItems())
        val bare = full.copy(release_type = null, seeders = null, detail = null,
            presentation = SourcePresentation(provider_name = "Torrentio"))
        assertEquals(listOf("Torrentio"), bare.factItems())
        assertEquals("Torrentio", bare.releaseGroupLabel())
        assertEquals("EMBER", full.releaseGroupLabel())
    }

    @Test fun sourceRowsRenderEveryExpectedTextNode() {
        val sources = fixtureSources()
        phone(sources, capturePath = "phone-390")
        desktop(sources, capturePath = "desktop-1440")
        tv(sources, capturePath = "tv-1440")
    }

    @Test fun cacheChipVariantsRenderTheirLabels() {
        val sources = listOf(
            SourceCandidate("c1", "addon:20", "[TB+] Torrentio\n1080p", detail = "[EMBER] S01E03.mkv\n👤 10 💾 481.6 MB ⚙️ NyaaSi", quality_height = 1080, delivery = "direct", seeders = 10, presentation = SourcePresentation(provider_name = "Torrentio", size_label = "481.6 MB")),
            SourceCandidate("c2", "addon:20", "🕵️ [RD⏳] MediaFusion", detail = "[sorrow] S01E03.mkv\n👤 5 💾 876.66 MB ⚙️ NyaaSi", quality_height = 1080, delivery = "direct", seeders = 5, presentation = SourcePresentation(provider_name = "MediaFusion", size_label = "876.66 MB")),
            SourceCandidate("c3", "addon:20", "⚡ AniKage • English DUB", detail = "[Group] S01E03.mkv", quality_height = 1080, delivery = "direct", audio_flags = listOf("dubbed"), audio_languages = listOf("en"), presentation = SourcePresentation(provider_name = "vSources ⚡")),
        )
        val scene = scene(390, 700) { Column(Modifier.width(390.dp)) { sources.forEach { SourceRow(it, false, {}) } } }
        try {
            frames(scene, 4)
            val present = unmerged(scene).map { text(it) }.toSet()
            assertTrue("⚡ Instant · TorBox" in present)
            assertTrue("Not cached · Real-Debrid" in present)
            assertTrue("⚡ Instant" in present)
            capture(scene, "cache-chips")
        } finally { scene.close() }
    }

    private fun phone(sources: List<SourceCandidate>, capturePath: String) {
        val scene = scene(390, 2400) {
            Column(Modifier.width(390.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { sources.forEachIndexed { index, source -> SourceRow(source, false, {}, selected = index == 0, bestMatch = index == 0) } }
        }
        try {
            frames(scene, 4)
            assertEverySourceIsVisible(scene, sources, "phone")
            capture(scene, capturePath)
        } finally { scene.close() }
    }

    private fun desktop(sources: List<SourceCandidate>, capturePath: String) {
        val scene = scene(1440, 1400) {
            Column(Modifier.width(720.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { sources.forEachIndexed { index, source -> SourceRow(source, false, {}, selected = index == 0, bestMatch = index == 0) } }
        }
        try {
            frames(scene, 4)
            assertEverySourceIsVisible(scene, sources, "desktop")
            capture(scene, capturePath)
        } finally { scene.close() }
    }

    private fun tv(sources: List<SourceCandidate>, capturePath: String) {
        val scene = scene(1440, 1100, television = true) {
            Column(Modifier.width(1200.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { sources.forEachIndexed { index, source -> TvSourceRow(source, TvSourceInfo(source.qualityLabel()), 1f, null, true, {}, bestMatch = index == 0) } }
        }
        try {
            frames(scene, 4)
            assertEverySourceIsVisible(scene, sources, "tv")
            capture(scene, capturePath)
        } finally { scene.close() }
    }

    private fun assertEverySourceIsVisible(scene: ImageComposeScene, sources: List<SourceCandidate>, where: String) {
        val rows = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }
        assertEquals(sources.size, rows.size, "$where: one clickable row per source")
        val present = unmerged(scene).map { text(it) }.toSet()
        sources.forEach { source ->
            assertTrue(source.releaseGroupLabel() in present, "$where: missing release group ${source.releaseGroupLabel()}")
            source.qualityLabel()?.let { assertTrue(it in present, "$where: missing quality $it") }
            source.audioChips()?.forEach { chip -> assertTrue(chip.label in present, "$where: missing audio chip ${chip.label}") }
            source.subtitleChips()?.filter { it.label != "Subs" }?.forEach { chip -> assertTrue(chip.label in present, "$where: missing subs chip ${chip.label}") }
            source.factItems(null).filter { it != "Season pack" }.forEach { fact -> assertTrue(present.any { fact in it }, "$where: missing fact $fact") }
        }
        println("=== $where text nodes ===")
        unmerged(scene).filter { text(it).isNotBlank() }.map { text(it) }.distinct().sorted().forEach { println("  $it") }
    }

    private fun torrent() = SourceCandidate(
        id = "t", provider_id = "addon:20", label = "Torrentio\n1080p",
        detail = "[EMBER] The Genius Prince's Guide to Raising a Nation Out of Debt (2022) (Batch)\n" +
            "S01E03-A Marriage as a Pretext [EA9B5806] 10-bit.mkv\n👤 62 💾 481.6 MB ⚙️ NyaaSi",
        quality_height = 1080L, release_type = "bluray", seeders = 62, delivery = "direct",
        audio_flags = listOf("dual", "dubbed"),
        presentation = SourcePresentation(provider_name = "Torrentio", quality = "1080p", size_label = "481.6 MB", video_codec = "HEVC", container = "mkv"),
    )

    private fun fixtureSources(): List<SourceCandidate> {
        val file = File("/tmp/sources-raw-redacted.json")
        if (!file.exists()) return fallbackSources()
        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(file.readText()).jsonObject
        return root["events"]?.jsonArray.orEmpty().flatMap { raw ->
            val event = raw.jsonObject
            val provider = event.str("source") ?: "server"
            event["streams"]?.jsonArray.orEmpty().mapNotNull { streamRaw ->
                val s = streamRaw.jsonObject
                val id = s.str("id") ?: return@mapNotNull null
                SourceCandidate(
                    id = id,
                    provider_id = provider,
                    label = s.str("name") ?: s.str("title") ?: "Source",
                    detail = s.str("detail") ?: s.str("description") ?: s.str("title"),
                    quality_height = s["quality_height"]?.jsonPrimitive?.longOrNull,
                    audio_languages = s.strs("audio_languages"),
                    presentation = s["presentation"]?.takeUnless { it is JsonNull }?.let { json.decodeFromJsonElement<SourcePresentation>(it) },
                ).withReleaseFacts(s)
            }
        }.take(6)
    }

    private fun fallbackSources(): List<SourceCandidate> = listOf(
        torrent(),
        torrent().copy(id = "t2", label = "[TB download] Torrentio\n1080p", delivery = "torrent", seeders = 5),
        torrent().copy(id = "t3", label = "⚡ AniKage • English DUB", delivery = "direct", audio_flags = listOf("dubbed"), audio_languages = listOf("en"), detail = null, seeders = null, presentation = SourcePresentation(provider_name = "vSources ⚡", quality = "1080p")),
        torrent().copy(id = "t4", label = "Torrentio\n720p", quality_height = 720, release_type = "webrip", audio_flags = emptyList(), audio_languages = listOf("ru"), subbed = true, subtitle_languages = listOf("en")),
        torrent().copy(id = "t5", label = "Torrentio\n1080p", audio_flags = emptyList(), audio_languages = emptyList(), detail = null, seeders = null, release_type = null, presentation = SourcePresentation(provider_name = "Torrentio")),
        torrent().copy(id = "t6", label = "🕵️ [RD⏳] MediaFusion", delivery = "direct", audio_flags = emptyList(), audio_languages = emptyList(), subbed = true, subtitle_languages = listOf("ja")),
    )

    private fun scene(width: Int, height: Int, television: Boolean = false, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 3) = repeat(count) {
        scene.render(++frame * 16_666_667L).close()
    }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
    private fun unmerged(scene: ImageComposeScene): List<SemanticsNode> = nodes(scene)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun capture(scene: ImageComposeScene, variant: String) {
        val dir = System.getenv("VIPTV_SOURCE_ROW_CAPTURE_DIR") ?: return
        File(dir).mkdirs()
        scene.render(++frame * 16_666_667L).use { image ->
            image.encodeToData()!!.use { File(dir, "$variant.png").writeBytes(it.bytes) }
        }
    }
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
private fun JsonObject.strs(key: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { v -> v.isString }?.content }