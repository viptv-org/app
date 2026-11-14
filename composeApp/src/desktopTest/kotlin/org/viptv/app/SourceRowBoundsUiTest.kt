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
import org.viptv.app.features.sources.CacheChip
import org.viptv.app.features.sources.CacheKind
import org.viptv.app.features.sources.SourceRow
import org.viptv.app.features.sources.TvSourceInfo
import org.viptv.app.features.sources.TvSourceRow
import org.viptv.app.features.sources.addonText
import org.viptv.app.features.sources.cacheChip
import org.viptv.app.features.sources.providerLabel
import org.viptv.app.features.sources.qualityLabel
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation

class SourceRowBoundsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

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

    @Test fun sourceRowsRenderEveryExpectedTextNode() {
        val sources = fixtureSources()
        phone(sources, capturePath = "phone-390")
        desktop(sources, capturePath = "desktop-1440")
        tv(sources, capturePath = "tv-1440")
    }

    @Test fun pointerRowsOmitCacheServiceBadges() {
        val sources = listOf(
            SourceCandidate("c1", "addon:20", "[TB+] Torrentio\n1080p", detail = "Show 2024 1080p\n👤 10 💾 481.6 MB ⚙️ Site", quality_height = 1080, delivery = "direct", presentation = SourcePresentation(provider_name = "Torrentio")),
            SourceCandidate("c2", "addon:20", "🕵️ [RD⏳] MediaFusion", detail = "Show 2024 1080p\n👤 5 💾 876.66 MB ⚙️ Site", quality_height = 1080, delivery = "direct", presentation = SourcePresentation(provider_name = "MediaFusion")),
            SourceCandidate("c3", "addon:20", "⚡ AniKage • English DUB", detail = "Show S01E03 1080p\n👤 3 💾 300 MB ⚙️ Site", quality_height = 1080, delivery = "direct", audio_flags = listOf("dubbed"), presentation = SourcePresentation(provider_name = "vSources ⚡")),
        )
        val scene = scene(390, 700) { Column(Modifier.width(390.dp)) { sources.forEach { SourceRow(it, false, {}) } } }
        try {
            frames(scene, 4)
            val present = unmerged(scene).map { text(it) }.toSet()
            assertFalse(present.any { "Instant" in it || "TorBox" in it || "Not cached" in it })
            sources.forEach { assertTrue(it.addonText()!! in present, "missing the add-on text of ${it.id}") }
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
        assertEquals(1, rows.map { it.boundsInRoot.height }.distinct().size, "$where: one row height")
        val present = unmerged(scene).map { text(it) }.toSet()
        sources.forEach { source ->
            assertTrue(source.addonText()!! in present, "$where: missing the add-on text of ${source.id}")
            assertTrue(source.providerLabel() in present, "$where: missing the provider of ${source.id}")
            source.qualityLabel()?.let { assertTrue(it in present, "$where: missing quality $it") }
        }
        println("=== $where text nodes ===")
        unmerged(scene).filter { text(it).isNotBlank() }.map { text(it) }.distinct().sorted().forEach { println("  $it") }
    }

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
                    presentation = s["presentation"]?.takeUnless { it is JsonNull }?.let { json.decodeFromJsonElement<SourcePresentation>(it) },
                )
            }
        }.take(6)
    }

    private fun fallbackSources(): List<SourceCandidate> = listOf(
        SourceCandidate("t1", "addon:20", "Torrentio\n1080p", detail = "[EMBER] Show S01E03 1080p\nShow.S01E03.mkv\n👤 62 💾 481.6 MB ⚙️ Site",
            quality_height = 1080, release_type = "bluray", delivery = "direct", audio_flags = listOf("dual", "dubbed"), dynamic_range = listOf("hdr"),
            presentation = SourcePresentation(provider_name = "Torrentio", quality = "1080p")),
        SourceCandidate("t2", "addon:20", "[TB+] Torrentio\n1080p", detail = "[EMBER] Show S01E03 1080p\nShow.S01E03.mkv\n👤 10 💾 481.6 MB ⚙️ Site",
            quality_height = 1080, release_type = "remux", delivery = "direct", three_d = true,
            presentation = SourcePresentation(provider_name = "Torrentio", quality = "1080p")),
        SourceCandidate("t3", "addon:21", "⚡ AniKage • English DUB", detail = "Show S01E03\n👤 3 💾 300 MB ⚙️ Site",
            quality_height = 1080, delivery = "direct", audio_flags = listOf("dubbed"), presentation = SourcePresentation(provider_name = "vSources ⚡")),
        SourceCandidate("t4", "addon:22", "Comet\n720p", detail = "Show S01E03 720p WEB\n👤 5 💾 120 MB ⚙️ Site",
            quality_height = 720, release_type = "webrip", audio_languages = listOf("ru"), subbed = true, subtitle_languages = listOf("en"),
            presentation = SourcePresentation(provider_name = "Comet", quality = "720p")),
        SourceCandidate("t5", "addon:23", "MediaFusion", detail = "Show S01E03 1080p\n👤 4 💾 900 MB ⚙️ Site",
            quality_height = 1080, release_type = "cam", presentation = SourcePresentation(provider_name = "MediaFusion")),
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
