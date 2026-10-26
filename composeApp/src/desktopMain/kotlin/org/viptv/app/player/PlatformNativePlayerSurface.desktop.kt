package org.viptv.app.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow

@Composable
internal actual fun PlatformNativePlayerSurface(input: PlaybackInput, paused: Boolean, modifier: Modifier, observe: (PlayerFact) -> Unit, commands: Flow<PlayerCommand>) =
    MpvPlayerSurface(input, paused, modifier, observe, commands)
