package org.viptv.app.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlayerSurface(input: PlaybackInput, paused: Boolean, fill: Boolean, modifier: Modifier, observe: (PlayerFact) -> Unit, commands: kotlinx.coroutines.flow.Flow<PlayerCommand>) =
    PlatformNativePlayerSurface(input, paused, fill, modifier, observe, commands)

@Composable
internal expect fun PlatformNativePlayerSurface(input: PlaybackInput, paused: Boolean, fill: Boolean, modifier: Modifier, observe: (PlayerFact) -> Unit, commands: kotlinx.coroutines.flow.Flow<PlayerCommand>)
