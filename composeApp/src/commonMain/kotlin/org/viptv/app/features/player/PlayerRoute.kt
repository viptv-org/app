package org.viptv.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import org.viptv.app.state.*
import org.viptv.app.state.ApplicationState

@Composable
internal fun PlayerRoute(state: ApplicationState, controller: ApplicationController, television: Boolean,
    phone: Boolean, browser: Boolean, backChanged: ((() -> Unit)?) -> Unit) {
    val detail = state.detail
    val episodes = detail?.episodes.orEmpty()
    LaunchedEffect(state.playback, state.selectedProfile, state.sourceTitle?.identity, state.sourceTitle?.provider_id, state.sourceTitle?.parent_identity) {
        controller.loadNextEpisode()
    }
    val fallback = state.sources.firstOrNull { it.id != state.playingSource?.id } ?: state.playingSource
    val title = state.sourceTitle?.let { card ->
        val episode = card.identity.episode
        if (episode != null && detail != null) card.copy(title = detail.item.title,
            subtitle = "S${episode.season ?: 1} E${episode.episode} · ${card.title}") else card
    }
    PlayerScreen(state.playback, state.paused, state.error, controller::pausePlayback,
        { fact -> controller.observePlaybackFor(state.playback, fact) }, controller::closePlayer,
        title, phone = phone, canvasColor = if (browser) Color.Transparent else Color.Black, television = television,
        tvExtras = TvPlaybackExtras(sourceLabel = state.playingSource?.label,
            sourceQuality = state.playingSource?.quality_height?.let { if (it == 2160L) "4K" else "${it}p" } ?: state.playingSource?.presentation?.quality, episodes = episodes,
            nextEpisode = state.nextEpisode, fallbackLabel = fallback?.label),
        chooseSources = controller::closePlayer, playEpisode = { controller.closePlayer(); controller.chooseSource(it) },
        retry = fallback?.let { { controller.retrySource(it) } }, backChanged = backChanged,
        playbackKey = state.playingSource?.id ?: state.sourceTitle?.identity, nextEpisodeLoading = state.nextEpisodeLoading)
}
