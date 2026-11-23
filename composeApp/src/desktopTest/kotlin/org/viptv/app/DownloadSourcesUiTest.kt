package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.FakeDownloads.Companion.GIB
import org.viptv.app.FakeDownloads.Companion.episode
import org.viptv.app.FakeDownloads.Companion.item
import org.viptv.app.connection.SourceGroups
import org.viptv.app.downloads.DownloadItem
import org.viptv.app.downloads.DownloadState
import org.viptv.app.features.sources.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DownloadSourcesUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val card = episode(1, 3)
    private val sources = listOf(source("s1", "Torrentio"), source("s2", "Comet"), source("s3", "MediaFusion"), source("s4", "Jackettio"))
    private val saved = item(card, DownloadState.Completed, done = 2 * GIB)
    private val saving = item(card, DownloadState.Downloading, done = GIB).copy(id = "other")
    private val groups = SourceGroups(bestMatches = listOf("s1"), bestMatchId = "s1")

    @Test fun aDownloadedSourceIsPinnedAboveBestMatchAndADownloadingOneShowsItsProgress() {
        val chosen = mutableListOf<String>()
        val scene = sources(forDownload = false, downloaded = mapOf("s3" to saved, "s2" to saving)) { chosen += it.id }
        try {
            val shown = texts(scene)
            val pinned = shown.indexOfFirst { it.equals(DOWNLOADED_HEADER, ignoreCase = true) }
            val best = shown.indexOfFirst { it.equals(BEST_MATCHES_HEADER, ignoreCase = true) }
            assertTrue(pinned in 0 until best, "Downloaded is pinned above Best matches: $shown")
            assertTrue(shown.indexOfFirst { it.contains("MediaFusion") } < shown.indexOfFirst { it.contains("Torrentio") },
                "the downloaded source is the first row: $shown")
            assertTrue(shown.any { it.contains(DOWNLOADED_BADGE) } && shown.any { it.contains("On this device · 2.0 GB") }, "$shown")
            assertTrue(nodes(scene).any { description(it) == "Downloading 50%" }, "the downloading source shows its progress")
            click(row(scene, "MediaFusion"))
            assertEquals(listOf("s3"), chosen, "the downloaded row stays selectable outside download mode")
        } finally { scene.close() }
    }

    @Test fun downloadModeDisablesTakenAndBlockedSourcesWithTheirReasons() {
        val chosen = mutableListOf<String>()
        val reason = "Needs the server to convert"
        val scene = sources(forDownload = true, downloaded = mapOf("s3" to saved, "s2" to saving),
            blocked = mapOf("s1" to null, "s4" to reason)) { chosen += it.id }
        try {
            val shown = texts(scene)
            assertTrue(CHOOSE_DOWNLOAD_SOURCE in shown, "$shown")
            assertTrue(shown.any { it.contains(reason) }, "a blocked source shows why: $shown")
            listOf("MediaFusion", "Comet", "Jackettio").forEach { name ->
                assertTrue(row(scene, name).config.contains(SemanticsProperties.Disabled), "$name must not be selectable")
            }
            assertFalse(row(scene, "Torrentio").config.contains(SemanticsProperties.Disabled))
            click(row(scene, "Torrentio"))
            assertEquals(listOf("s1"), chosen)
            assertTrue(nodes(scene).none { description(it) == SOURCE_MENU_TARGET_LABEL }, "download mode has no play menu")
        } finally { scene.close() }
    }

    private fun source(id: String, provider: String) = SourceCandidate(id, "addon:${provider.lowercase()}", "$provider 1080p",
        "Severance.S01E03.1080p.WEB-DL-$provider", quality_height = 1080,
        presentation = SourcePresentation(provider_name = provider, size_bytes = 2 * GIB))

    private fun sources(forDownload: Boolean, downloaded: Map<String, DownloadItem>, blocked: Map<String, String?> = emptyMap(),
        choose: (SourceCandidate) -> Unit): ImageComposeScene {
        val scene = ImageComposeScene(1280, 1000, Density(1f)) { ViptvTheme {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                SourcesScreen(card, sources, false, null, choose, {}, groups = groups, forDownload = forDownload,
                    blockedSources = blocked, downloadedSources = downloaded)
            }
        } }
        scene.renderUntil(what = "the source rows") { texts(scene).any { it.contains("Jackettio") } }
        repeat(30) { scene.render(it * 16_666_667L).close() }
        return scene
    }

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun texts(scene: ImageComposeScene) = nodes(scene).map { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() }
    private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ").orEmpty()
    private fun row(scene: ImageComposeScene, provider: String) = nodes(scene).firstOrNull { node ->
        node.config.contains(SemanticsActions.OnClick) &&
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == provider }
    } ?: fail("no row for $provider")
    private fun click(node: SemanticsNode) { assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()) }
}
