package org.viptv.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import org.viptv.app.features.settings.rememberDevicePlaybackState
import org.viptv.app.features.settings.rememberReducedMotion
import org.viptv.app.state.*
import org.viptv.app.state.ApplicationState
import org.viptv.engine.wire.Card

@Composable
internal fun PlayerRoute(state: ApplicationState, controller: ApplicationController, television: Boolean,
    phone: Boolean, browser: Boolean, backChanged: ((() -> Unit)?) -> Unit) {
    val detail = state.detail
    val episodes = playerEpisodes(state.sourceTitle, detail?.episodes.orEmpty() + state.playbackSeason)
    LaunchedEffect(state.playback, state.selectedProfile, state.sourceTitle?.identity, state.sourceTitle?.provider_id, state.sourceTitle?.parent_identity) {
        controller.loadNextEpisode()
        controller.loadPlaybackSeason()
    }
    val fallback = state.sources.firstOrNull { it.id != state.playingSource?.id } ?: state.playingSource
    val preferences = state.playback?.preferences ?: state.preferences?.preferences
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
            skips = state.playbackSegments, skipIntros = preferences?.skip_intros == true,
            nextEpisode = state.nextEpisode.takeIf { preferences?.next_episode_button != false }, fallbackLabel = fallback?.label),
        chooseSources = controller::closePlayer, playEpisode = { controller.closePlayer(); controller.chooseSource(it) },
        retry = fallback?.let { { controller.retrySource(it) } }, backChanged = backChanged,
        playbackKey = state.playingSource?.id ?: state.sourceTitle?.identity, nextEpisodeLoading = state.nextEpisodeLoading,
        trackChosen = controller::rememberTrackChoice, openSettings = controller::openPlayerSettings,
        autoplayNext = rememberDevicePlaybackState().value.autoplayNext, reduceMotion = rememberReducedMotion(),
        openingSource = state.openingSource)
}

internal fun playerEpisodes(current: Card?, loaded: List<Card>): List<Card> {
    val coordinates = current?.identity?.episode ?: return emptyList()
    return phonePlaybackEpisodes(current, loaded)
        .filter { it.identity.episode?.season == coordinates.season }
        .sortedBy { it.identity.episode?.episode }
}
