package org.viptv.app.preview

import androidx.compose.runtime.Composable
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.details.SharedTitlePage
import org.viptv.app.features.home.HomeScreen
import androidx.compose.foundation.layout.Box
import org.viptv.app.resources.Res
import org.viptv.app.state.Screen
import org.viptv.engine.wire.*

/** Isolated ZIP fixture; additional seasons intentionally exist only in this preview. */
@Composable
internal fun PreviewDetails(variant: String, go: (Screen) -> Unit, tablet: Boolean, browser: Boolean, desktop: Boolean) {
    val series = PreviewTvDetails.series.copy(item = PreviewTvDetails.series.item.copy(
        landscape = Res.getUri("files/ui-preview/${if (desktop || browser) "desktop-title.jpg" else "mobile-title.jpg"}"),
        logo = Res.getUri("files/ui-preview/mobile-title-logo.png")))
    val detail = when (variant) {
        "Series" -> series
        "SeriesSeasons" -> series.copy(episodes = series.episodes + series.episodes.take(2).map { episode ->
            episode.copy(identity = episode.identity.copy(episode = episode.identity.episode?.copy(season = 2)))
        })
        else -> PreviewData.detail
    }
    if (variant == "SharedTitle") { SharedTitlePage(series) { go(Screen.Connect) }; return }
    Box {
        if (browser) HomeScreen(PreviewData.home, false, { go(Screen.Details) }, {}, browser = true)
        DetailsScreen(detail, false, null, { go(Screen.Home) }, { go(Screen.Sources) }, tablet = tablet, browser = browser)
    }
}
