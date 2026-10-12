package org.viptv.app.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class PlaybackInput(val id: String, val uri: String, val headers: Map<String, String> = emptyMap(), val positionMillis: Long = 0, val profileId: String? = null, val revision: Long = 0) {
    override fun toString() = "PlaybackInput(id=$id, media=<private>)"
}

sealed interface PlayerFact {
    data class Progress(val positionMillis: Long, val durationMillis: Long?) : PlayerFact
    data class Status(val name: String) : PlayerFact
    data class Properties(val volume: Float, val speed: Double, val subtitleSize: Float = 1f) : PlayerFact
    data class Tracks(val options: PlaybackTracks) : PlayerFact
    data object Failed : PlayerFact
    data object ControlFailed : PlayerFact
}

@Composable
expect fun PlayerSurface(input: PlaybackInput, paused: Boolean, modifier: Modifier, observe: (PlayerFact) -> Unit, commands: kotlinx.coroutines.flow.Flow<PlayerCommand>)
