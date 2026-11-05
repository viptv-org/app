package org.viptv.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import org.viptv.app.features.settings.rememberDevicePlaybackState
import org.viptv.app.features.settings.rememberReducedMotion
import org.viptv.app.features.sources.providerLabel
import org.viptv.app.features.sources.qualityLabel
import org.viptv.app.state.*
import org.viptv.app.state.ApplicationState
import org.viptv.engine.wire.Card
import org.viptv.app.connection.seasonNumber

@Composable
internal fun PlayerRoute(state: ApplicationState, controller: ApplicationController, television: Boolean,
    phone: Boolean, browser: Boolean, backChanged: ((() -> Unit)?) -> Unit,
    fullscreen: Boolean = false, toggleFullscreen: (() -> Unit)? = null) {
    val detail = state.detail
    val loaded = detail?.episodes.orEmpty() + state.playbackSeason
    val episodes = playerEpisodes(state.sourceTitle, loaded)
    val nextEpisode = nextEpisodeAfter(state.sourceTitle, loaded)
    LaunchedEffect(state.playback, state.selectedProfile, state.sourceTitle?.identity, state.sourceTitle?.provider_id, state.sourceTitle?.parent_identity) {
        controller.loadNextEpisode()
        controller.loadPlaybackSeason()
    }
    val fallback = state.sources.firstOrNull { it.id != state.playingSource?.id } ?: state.playingSource
    val fallbackNotice = state.fallbackNotice
    val preferences = state.playback?.preferences ?: state.preferences?.preferences
    val title = state.sourceTitle?.let { card ->
        val episode = card.identity.episode
        if (episode != null && detail != null) card.copy(title = detail.item.title,
            subtitle = "S${episode.seasonNumber()} E${episode.episode} · ${card.title}") else card
    }
    PlayerScreen(state.playback, state.paused, state.error, controller::pausePlayback,
        { fact -> controller.observePlaybackFor(state.playback, fact) }, controller::closePlayer,
        title, phone = phone, canvasColor = if (browser) Color.Transparent else Color.Black, television = television,
        tvExtras = TvPlaybackExtras(sourceLabel = state.playingSource?.providerLabel(),
            sourceQuality = state.playingSource?.qualityLabel(), episodes = episodes,
            skips = state.playbackSegments, skipIntros = preferences?.skip_intros == true,
            nextEpisode = nextEpisode.takeIf { preferences?.next_episode_button != false }, fallbackLabel = fallback?.label),
        chooseSources = controller::closePlayer, playEpisode = controller::playEpisode,
        retry = fallback?.let { { controller.retrySource(it) } }, backChanged = backChanged,
        playbackKey = state.playingSource?.id ?: state.sourceTitle?.identity,
        trackChosen = controller::rememberTrackChoice, openSettings = controller::openPlayerSettings,
        preferences = state.preferences?.preferences, updatePreferences = controller::savePlaybackPreferences,
        autoplayNext = rememberDevicePlaybackState().value.autoplayNext, reduceMotion = rememberReducedMotion(),
        fallback = fallbackNotice?.let { PlayerFallbackNotice(it.reason, it.next.label, it.next.fallbackFacts()) },
        stopFallback = if (fallbackNotice != null) controller::stopBestMatchFallback else null,
        audioNotice = state.audioNotice,
        nextSource = if (state.bestMatch != null) controller::playNextSource else null,
        openingSource = state.openingSource,
        fullscreen = fullscreen, toggleFullscreen = toggleFullscreen)
}

internal fun playerEpisodes(current: Card?, loaded: List<Card>): List<Card> {
    val coordinates = current?.identity?.episode ?: return emptyList()
    return phonePlaybackEpisodes(current, loaded)
        .filter { it.identity.episode?.season == coordinates.season }
        .sortedBy { it.identity.episode?.episode }
}
