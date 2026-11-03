package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.connection.SourceGroups
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.state.DetailSourcePreview
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import kotlin.test.Test

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class PhoneSeriesRenderTest {
    @get:Rule val uiThread = UiThreadRule()

    private val series = Card(
        MediaIdentity("tt560", "series", emptyList()),
        provider_id = "addon:21",
        title = "The End of Oak Street",
        description = "After a cosmic event lifts their street out of the suburbs, the Platt family learns what their neighbours were really hiding, and the road home leads somewhere none of them expected.",
        badges = emptyList(),
        metadata = CardMetadata(
            year = 2026, runtime_minutes = 52, genres = listOf("Crime", "Drama"),
            rating = 8.0, certification = "TV-MA", cast = listOf("Evan Peters", "Richard Jenkins", "Niecy Nash"),
        ),
    )

    private fun episode(season: Long, number: Long, progress: Double? = null, badges: List<String> = emptyList(),
        releasedAt: Long? = null) = series.copy(
        identity = MediaIdentity("tt560:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = "Episode $number", description = null,
        metadata = CardMetadata(runtime_minutes = 52), progress = progress, badges = badges,
        released_at_millis = releasedAt)

    private val detail = DetailSnapshot(series, episodes = listOf(
        episode(1, 1, badges = listOf("Watched")),
        episode(1, 2, progress = 0.4),
        episode(1, 3, releasedAt = 1_773_187_200_000L),
    ), seasons = listOf(EpisodeSeason(1, 3, 0)))

    private val resuming = listOf(episode(1, 2, progress = 0.4))

    private fun source(id: String, quality: Long?) = SourceCandidate(id, "addon:LordStreams", "Source $id",
        quality_height = quality, presentation = SourcePresentation(provider_name = "LordStreams"))

    private val preview = DetailSourcePreview(
        rows = listOf(source("a", 1080), source("b", 720)),
        groups = SourceGroups.of(listOf("a", "b"), "a"),
        settled = true)

    @Test fun render() = runBlocking {
        val dir = System.getenv("VIPTV_560_RENDER_DIR")?.let(::File) ?: return@runBlocking
        dir.mkdirs()

        val scrollTop = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme(false) {
            Surface(color = Palette.background, modifier = Modifier.fillMaxSize()) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, continuing = resuming,
                    sourcePreview = preview)
            }
        } }
        var frame = 0L
        suspend fun frames(count: Int = 12) = repeat(count) { scrollTop.render(frame++ * 16_666_667L).close(); yield() }
        frames(); delay(250); frames()
        scrollTopNode(scrollTop)
        frames()
        scrollTop.render(frame++ * 16_666_667L).use { image ->
            image.encodeToData()!!.use { File(dir, "phone-series-after.png").writeBytes(it.bytes) }
        }
        scrollTop.close()

        val scrolled = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme(false) {
            Surface(color = Palette.background, modifier = Modifier.fillMaxSize()) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, continuing = resuming,
                    sourcePreview = preview)
            }
        } }
        var scrolledFrame = 0L
        repeat(12) { scrolled.render(scrolledFrame++ * 16_666_667L).close(); yield() }
        delay(250)
        repeat(12) { scrolled.render(scrolledFrame++ * 16_666_667L).close(); yield() }
        scrolled.render(scrolledFrame++ * 16_666_667L).use { image ->
            image.encodeToData()!!.use { File(dir, "phone-series-after-scrolled.png").writeBytes(it.bytes) }
        }
        scrolled.close()
    }

    private fun scrollTopNode(scene: ImageComposeScene) {
        val node = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
            .firstOrNull { it.config.contains(SemanticsActions.ScrollToIndex) }
        node?.config?.getOrNull(SemanticsActions.ScrollToIndex)?.action?.invoke(0)
    }
}