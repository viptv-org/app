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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class PhoneHeroHeaderTest {
    @get:Rule val uiThread = UiThreadRule()

    private val series = Card(MediaIdentity("tt560", "series", emptyList()), provider_id = "addon:21",
        title = "The End of Oak Street", description = "A description.", badges = emptyList(),
        metadata = CardMetadata(year = 2026, runtime_minutes = 52, genres = listOf("Drama"), rating = 8.0,
            certification = "TV-MA"))
    private fun episode(season: Long, number: Long) = series.copy(
        identity = MediaIdentity("tt560:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = "Episode $number", description = null,
        metadata = CardMetadata(runtime_minutes = 52))
    private val detail = DetailSnapshot(series, episodes = (1L..3L).map { episode(1, it) },
        seasons = listOf(EpisodeSeason(1, 3, 0)))

    @Test fun scrollTopKeepsThePrimaryButtonBelowTheHeader() = runBlocking {
        var detail by mutableStateOf(this@PhoneHeroHeaderTest.detail)
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme(false) {
            Surface(color = Palette.background, modifier = Modifier.fillMaxSize()) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = {})
            }
        } }
        var frame = 0L
        repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
        delay(200)
        repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
        val primary = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
            .firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Play" } }
        assertTrue(primary != null, "the primary play button renders")
        val headerBottom = with(Density(1f)) { 56.dp.toPx() }
        assertTrue(primary.boundsInRoot.top >= headerBottom,
            "the primary button sits below the header: ${primary.boundsInRoot}")
        scene.close()
    }
}