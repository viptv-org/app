package org.viptv.app.preview

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import org.viptv.app.features.details.TvDetailsScreen
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.app.state.Screen

@Composable
internal fun PreviewTvSeasons(variant: String, go: (Screen) -> Unit) {
    val episodes = remember { PreviewTvDetails.episodes.mapIndexed { index, card ->
        card.copy(identity = card.identity.copy(episode = EpisodeCoordinates("aired", if (index < 2) 1 else 2, (index + 1).toLong())))
    } }
    var loaded by remember { mutableStateOf(variant != "SeriesDelayed") }
    LaunchedEffect(variant) { if (!loaded) { delay(2000); loaded = true } }
    val extras = PreviewTvDetails.seriesExtras.copy(resume = episodes[2], resumeLabel = "Resume S2 E3")
    TvDetailsScreen(PreviewTvDetails.series.copy(episodes = if (loaded) episodes else emptyList()), false, null,
        { go(Screen.Sources) }, extras)
}
