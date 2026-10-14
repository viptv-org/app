package org.viptv.app.features.live

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.viptv.app.state.*
import org.viptv.app.theme.Palette

@Composable
internal fun LiveRoute(state: ApplicationState, controller: ApplicationController, television: Boolean) {
    var clock by remember { mutableStateOf(liveClock()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); clock = liveClock() } }
    val channels = state.livePage?.channels.orEmpty().map { channel ->
        LiveChannel(channel.card, category = channel.group_name, id = channel.id, connectionId = channel.connection_id, groupId = channel.group_id,
            programs = state.liveGuides[channel.id]?.programs.orEmpty().map { program ->
                LiveProgram(program.id, program.title, clock.minute(program.start_millis), clock.minute(program.end_millis), program.description)
            })
    }
    CompositionLocalProvider(LocalLiveClock provides clock) {
        Box(Modifier.fillMaxSize()) {
            LiveScreen(channels, television, if (channels.any { it.programs.isNotEmpty() }) 0 else null, clock.nowMinute, { controller.playLive(it.id) }, { controller.navigate(Screen.Settings) },
                groupLabels = state.livePage?.groups.orEmpty().associate { it.id to it.label }, groupChanged = controller::filterLive,
                guideRequested = { controller.showLiveGuide(it.id) }, more = if (state.livePage?.next_cursor != null) controller::moreLive else null, busy = state.busy)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
            val notice = state.error ?: state.livePage?.reason
            if (notice != null) Column(Modifier.padding(24.dp)) {
                Text(notice, color = Palette.secondary)
                TextButton(controller::refreshLive, enabled = !state.busy) { Text("Retry") }
            }
        }
    }
}
