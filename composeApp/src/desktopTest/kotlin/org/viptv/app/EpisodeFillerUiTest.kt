package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Rule
import org.viptv.app.connection.FillerKind
import org.viptv.app.connection.cardLookupKey
import org.viptv.app.features.details.DesktopEpisodeRow
import org.viptv.app.features.details.DetailEpisodeCard
import org.viptv.app.features.details.LocalEpisodeFiller
import org.viptv.app.features.details.TvEpisodeCard
import org.viptv.app.state.EpisodeFiller
import org.viptv.app.state.EpisodeFillerLabels
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class EpisodeFillerUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val series = Card(MediaIdentity("tt0988824", "series", emptyList()), provider_id = "addon:7", title = "Naruto Shippuden", badges = emptyList())
    private val titles = mapOf(55L to "The Wind and Fire", 56L to "Team Ten", 57L to "Tenten's Troubles", 58L to "The Fifth Hokage's Return")
    private val episodes = titles.map { (absolute, title) ->
        val number = absolute - 53
        Card(MediaIdentity("tt0988824:3:$number", "series", emptyList(), EpisodeCoordinates("aired", 3, number)),
            provider_id = "addon:7", title = title, badges = emptyList(), parent_identity = series.identity,
            description = "Naruto and his team head out on a new mission while the village prepares for what comes next.",
            metadata = CardMetadata(runtime_minutes = 23), released_at_date = "2008-05-0${number}")
    }
    private val detail = DetailSnapshot(series, episodes = episodes,
        seasons = listOf(EpisodeSeason(1, 32, 0), EpisodeSeason(2, 21, 32), EpisodeSeason(3, 18, 53)))
    private val labels = EpisodeFillerLabels.of(detail,
        EpisodeFiller(cardLookupKey(series), mapOf(56L to FillerKind.Filler, 57L to FillerKind.Filler, 58L to FillerKind.Mixed)))
    private val output = File(System.getProperty("viptv.fillerRenders") ?: "build/reports/episode-filler").apply { mkdirs() }

    private fun spoken(scene: ImageComposeScene): List<String> = scene.semanticsOwners
        .flatMap { it.getAllSemanticsNodes(false) }
        .flatMap { node -> node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }

    private fun scene(width: Int, height: Int, television: Boolean, labels: EpisodeFillerLabels, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            CompositionLocalProvider(LocalEpisodeFiller provides labels) {
                Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
            }
        } }

    private fun layout(name: String, width: Int, height: Int, television: Boolean = false, content: @Composable () -> Unit) {
        for ((state, provided) in listOf("before" to EpisodeFillerLabels.Empty, "after" to labels)) {
            val scene = scene(width, height, television, provided, content)
            try {
                repeat(4) { scene.render(it * 16_666_667L).close() }
                val image = scene.render(100_000_000L)
                output.resolve("$name-$state.png").writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
                image.close()
                val tags = spoken(scene).filter { it.endsWith("episode") }
                if (provided === labels) assertEquals(listOf("Filler episode", "Filler episode", "Mixed canon and filler episode"), tags, name)
                else assertEquals(emptyList(), tags, name)
            } finally { scene.close() }
        }
    }

    @Test fun phoneAndTabletCardsTagFillerAndMixedEpisodes() {
        layout("phone-details", 390, 760) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                episodes.forEach { DetailEpisodeCard(it, true, {}, Modifier.fillMaxWidth()) }
            }
        }
        layout("phone-player-episodes", 390, 640) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                episodes.forEach { DetailEpisodeCard(it, true, {}, Modifier.fillMaxWidth(), playing = it === episodes[1], showAirDate = false, stateCaption = false) }
            }
        }
        layout("tablet-details", 900, 760) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                episodes.forEach { DetailEpisodeCard(it, true, {}, Modifier.fillMaxWidth(), tablet = true) }
            }
        }
        layout("player-episodes-grid", 1280, 420) {
            Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                episodes.forEach { DetailEpisodeCard(it, false, {}, Modifier.width(280.dp), showAirDate = false) }
            }
        }
    }

    @Test fun desktopAndWebRowsTagFillerAndMixedEpisodes() {
        for (width in listOf(1280, 1440)) layout("desktop-details-$width", width, 760) {
            Column(Modifier.padding(horizontal = 40.dp, vertical = 16.dp)) { episodes.forEach { DesktopEpisodeRow(it, {}) } }
        }
        layout("web-dialog", 1000, 720) {
            Column(Modifier.padding(horizontal = 32.dp, vertical = 16.dp)) { episodes.forEach { DesktopEpisodeRow(it, {}, browser = true) } }
        }
    }

    @Test fun tvShelfAndPlayerPickerTagFillerAndMixedEpisodes() {
        val up = FocusRequester()
        layout("tv-shelf", 1920, 620, television = true) {
            Row(Modifier.padding(48.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                episodes.forEach { TvEpisodeCard(it, 0.95f, {}, {}, null, up) }
            }
        }
        layout("tv-player-episodes", 1920, 520, television = true) {
            Row(Modifier.padding(48.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                episodes.forEach { TvEpisodeCard(it, 0.95f, {}, {}, null, up, playing = it === episodes[1], showAirDate = false) }
            }
        }
    }
}
