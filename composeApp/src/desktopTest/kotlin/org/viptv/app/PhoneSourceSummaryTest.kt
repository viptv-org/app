package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.connection.SourceGroups
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.home.CHOOSE_SOURCE_LABEL
import org.viptv.app.state.DetailSourcePreview
import org.viptv.app.state.SOURCES_EMPTY_LABEL
import org.viptv.app.state.SOURCES_UNAVAILABLE_LABEL
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class PhoneSourceSummaryTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val series = Card(MediaIdentity("tt560", "series", emptyList()), provider_id = "addon:21", title = "Fixture show",
        badges = emptyList(), metadata = CardMetadata(year = 2026, genres = listOf("Drama")))
    private fun episode(season: Long, number: Long, progress: Double? = null) = series.copy(
        identity = MediaIdentity("tt560:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = "Episode $number", subtitle = "S$season E$number", progress = progress,
        metadata = CardMetadata(runtime_minutes = 44))
    private val detail = DetailSnapshot(series, episodes = listOf(episode(1, 1), episode(1, 2, progress = 0.4)))
    private val resuming = listOf(episode(1, 2, progress = 0.4))
    private fun source(id: String, provider: String, quality: Long? = 1080) = SourceCandidate(id, "addon:$provider", "Source $id",
        quality_height = quality, presentation = SourcePresentation(provider_name = provider))
    private fun preview(vararg rows: SourceCandidate, settled: Boolean = true, failed: Boolean = false) =
        DetailSourcePreview(rows = rows.toList(), groups = SourceGroups.of(rows.map { it.id }, rows.firstOrNull()?.id),
            settled = settled, failed = failed)

    @Test fun aPendingDiscoveryDrawsALayoutMatchingSkeletonNamedChooseSource() = onEdt {
        Page(390, 844, detail, DetailSourcePreview()).use { page ->
            page.settle()
            page.save("phone-source-summary-pending")
            assertTrue(page.chooseDescriptions().any { it.contains(CHOOSE_SOURCE_LABEL) },
                "the pending control is named Choose source: ${page.chooseDescriptions()}")
            assertTrue(page.texts().none { it.endsWith("source") || it.endsWith("sources") },
                "a pending summary claims no source facts: ${page.texts()}")
        }
    }

    @Test fun theRowPrintsTheQualityProviderAndCountBelowThePrimaryButton() = onEdt {
        Page(390, 844, detail, preview(source("a", "LordStreams"), source("b", "LordStreams", quality = 720))).use { page ->
            page.settle()
            page.save("phone-source-summary")
            assertTrue(page.texts().contains("1080p"), "the best source's quality: ${page.texts()}")
            assertTrue(page.texts().contains("LordStreams · 2 sources"), "provider and count: ${page.texts()}")
            val button = page.merged().first { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any {
                it.text.startsWith("Play") || it.text.startsWith("Resume") } }
            val summary = page.summaryNode() ?: fail("no source summary row")
            assertTrue(summary.boundsInRoot.top >= button.boundsInRoot.bottom - 1f, "the summary sits below the primary button")
        }
    }

    @Test fun oneSourceIsSingularAndAnUnknownQualityReadsAuto() = onEdt {
        Page(390, 844, detail, preview(source("a", "Comet", quality = null))).use { page ->
            page.settle()
            assertTrue(page.texts().contains("Auto"), "an unknown device limit stays unknown: ${page.texts()}")
            assertTrue(page.texts().contains("Comet · 1 source"), "one source is singular: ${page.texts()}")
        }
    }

    @Test fun aSettledEmptyDiscoveryReadsNoSourcesFound() = onEdt {
        Page(390, 844, detail, preview()).use { page ->
            page.settle()
            assertTrue(page.texts().contains(SOURCES_EMPTY_LABEL), page.texts().toString())
            assertTrue(page.summaryNode() != null, "the empty summary stays actionable")
        }
    }

    @Test fun aFailureWithoutRowsReadsSourcesUnavailable() = onEdt {
        Page(390, 844, detail, preview(failed = true)).use { page ->
            page.settle()
            assertTrue(page.texts().contains(SOURCES_UNAVAILABLE_LABEL), page.texts().toString())
            assertTrue(page.summaryNode() != null, "a failed summary opens a fresh picker attempt")
        }
    }

    @Test fun aFailureAfterRowsKeepsThem() = onEdt {
        Page(390, 844, detail, preview(source("a", "LordStreams"), failed = true)).use { page ->
            page.settle()
            assertTrue(page.texts().contains("LordStreams · 1 source"), page.texts().toString())
            assertFalse(page.texts().contains(SOURCES_UNAVAILABLE_LABEL))
        }
    }

    @Test fun theSummaryOpensThePickerForTheResumeEpisode() = onEdt {
        Page(390, 844, detail, preview(source("a", "LordStreams")), continuing = resuming).use { page ->
            page.settle()
            val summary = page.summaryNode() ?: fail("no source summary row")
            summary.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(episode(1, 2).identity, page.chosen?.identity,
                "the summary previews and opens the same target as Resume")
        }
    }

    @Test fun thePageAsksForTheResumeTargetAndCancelsItOnTheWayOut() = onEdt {
        var asked: Card? = null
        var cancelled = 0
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme(false) { DetailsScreen(detail, busy = false, error = null,
            back = {}, sources = {}, continuing = resuming, chooseSource = {}, previewSources = { asked = it },
            cancelSourcesPreview = { cancelled++ }) } }
        try {
            scene.render(0).close()
            assertEquals(episode(1, 2).identity, asked?.identity, "a resumed series previews the resume episode")
        } finally { scene.close() }
        assertEquals(1, cancelled, "leaving the page cancels the preview")
    }

    @Test fun aTitleWithoutArtworkKeepsTheRowBelowItsPlayButton() = onEdt {
        val movie = Card(MediaIdentity("tt561", "movie", emptyList()), provider_id = "addon:21", title = "Fixture film", badges = emptyList())
        Page(360, 844, DetailSnapshot(movie, episodes = emptyList()), preview(source("a", "Comet"))).use { page ->
            page.settle()
            assertTrue(page.texts().contains("Comet · 1 source"), page.texts().toString())
        }
    }

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Page(width: Int, height: Int, initial: DetailSnapshot, initialPreview: DetailSourcePreview?,
        continuing: List<Card> = emptyList()) : AutoCloseable {
        var preview by mutableStateOf(initialPreview)
        var chosen: Card? = null
        private val detail = initial
        private val continuing = continuing
        private val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(false) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, continuing = this@Page.continuing,
                    chooseSource = { chosen = it }, sourcePreview = preview)
            }
        } }
        private var frame = 0L
        suspend fun frames(count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); yield() }
        suspend fun settle() { frames(); delay(200); frames() }
        fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
        fun merged() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
        fun texts() = merged().flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        fun chooseDescriptions() = merged().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
        fun save(name: String) {
            val dir = System.getenv("VIPTV_PHONE_SOURCE_SUMMARY_CAPTURE_DIR")?.let { java.io.File(it) } ?: return
            dir.mkdirs()
            scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { java.io.File(dir, "$name.png").writeBytes(it.bytes) } }
        }
        fun summaryNode() = merged().firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.contains(CHOOSE_SOURCE_LABEL) } &&
                node.config.getOrNull(SemanticsActions.OnClick) != null }
        override fun close() = scene.close()
    }
}