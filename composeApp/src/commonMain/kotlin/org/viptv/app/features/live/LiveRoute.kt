package org.viptv.app.features.live

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.viptv.app.components.ServerMessages
import org.viptv.app.state.*
import org.viptv.app.theme.Palette

@Composable
internal fun LiveRoute(state: ApplicationState, controller: ApplicationController, television: Boolean) {
    var clock by remember { mutableStateOf(liveClock()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); clock = liveClock() } }
    val channels = state.livePage?.channels.orEmpty().map { channel ->
        LiveChannel(channel.card, category = channel.group_name, id = channel.id, connectionId = channel.connection_id, groupId = channel.group_id, favorite = channel.id in state.liveFavorites, favoritePending = channel.id in state.liveFavoritePending,
            programs = state.liveGuides[channel.id]?.programs.orEmpty().map { program ->
                LiveProgram(program.id, program.title, clock.minute(program.start_millis), clock.minute(program.end_millis), program.description)
            })
    }
    CompositionLocalProvider(LocalLiveClock provides clock) {
        Box(Modifier.fillMaxSize()) {
            val empty = channels.isEmpty()
            // When no channels render, the empty state inside LiveScreen carries
            // the page reason or the load error; the top-left overlay must not
            // draw a second message on top of it.
            val emptyReason = if (empty) state.livePage?.reason?.let { ServerMessages.human(it, ServerMessages.LIVE_REASON) } ?: state.error
                else state.livePage?.takeIf { it.channels.isEmpty() }?.reason?.let { ServerMessages.human(it, ServerMessages.LIVE_REASON) }
            val guide = state.liveGuideChannel?.let(state.liveGuides::get)
            val notice = if (empty) null else state.error ?: state.liveFavoriteError ?: guide?.reason?.let { ServerMessages.human(it, ServerMessages.LIVE_REASON) }
            val retry: (() -> Unit)? = when {
                state.error != null -> controller::refreshLive
                state.liveFavoriteError != null && state.liveFavoritesCanRetry -> controller::refreshLiveFavorites
                state.liveFavoriteError != null -> null
                state.livePage?.availability == "TemporarilyUnavailable" -> controller::refreshLive
                guide?.availability == "TemporarilyUnavailable" -> controller::retryLiveGuide
                else -> null
            }
            LiveScreen(channels, television, if (channels.any { it.programs.isNotEmpty() }) 0 else null, clock.nowMinute, { controller.playLive(it.id) }, { controller.navigate(Screen.Settings) },
                groupLabels = state.livePage?.groups.orEmpty().associate { it.id to it.label }, groupChanged = controller::filterLive,
                guideRequested = { controller.showLiveGuide(it.id) }, more = if (state.livePage?.next_cursor != null && (!state.liveFavoritesOnly || state.liveFavorites.any { id -> state.livePage.channels.none { it.id == id } })) controller::moreLive else null, busy = state.busy,
                favoriteChanged = if (state.liveFavoritesAvailable) ({ controller.toggleLiveFavorite(it.id) }) else null,
                favoritesOnly = state.liveFavoritesOnly, favoritesFilter = if (state.liveFavoritesAvailable) controller::filterLiveFavorites else null,
                sources = state.livePage?.connections.orEmpty().filter { it.enabled }.associate { it.id to it.name }, selectedSource = state.liveQuery.connection_id,
                sourceChanged = controller::selectLiveConnection, emptyReason = emptyReason,
                statusNotice = notice, noticeRetry = retry)
            if (state.busy || state.liveGuideLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
        }
    }
}
