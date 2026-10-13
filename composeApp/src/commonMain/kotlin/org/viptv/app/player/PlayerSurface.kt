package org.viptv.app.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.asStateFlow

class PlaybackInput(val id: String, val uri: String, val headers: Map<String, String> = emptyMap(), val positionMillis: Long = 0, val profileId: String? = null, val revision: Long = 0,
    providerSubtitles: List<org.viptv.engine.wire.SubtitleCandidate> = emptyList(),
    val subtitleContent: (suspend (String) -> ByteArray)? = null,
    val timelineOffsetMillis: Long = 0, val durationMillis: Long? = null,
    val remoteSeek: ((Long) -> Unit)? = null,
    val preferences: org.viptv.app.connection.ProfilePreferences = org.viptv.app.connection.ProfilePreferences(), val live: Boolean = false) {
    private val subtitleChoices = kotlinx.coroutines.flow.MutableStateFlow(providerSubtitles)
    val providerSubtitles = subtitleChoices.asStateFlow()
    internal fun updateProviderSubtitles(values: List<org.viptv.engine.wire.SubtitleCandidate>) { subtitleChoices.value = values }
    override fun toString() = "PlaybackInput(id=$id, media=<private>)"
}

sealed interface PlayerFact {
    /** Exactness refers to this stated renderer proof, never physical scanout. */
    data class Presentation(val proof: PresentationProof, val atNanos: Long? = null, val exact: Boolean = false) : PlayerFact
    data class Progress(val positionMillis: Long, val durationMillis: Long?) : PlayerFact
    data class Status(val name: String) : PlayerFact
    data class Properties(val volume: Float, val speed: Double, val subtitleSize: Float = 1f) : PlayerFact
    data class Tracks(val options: PlaybackTracks) : PlayerFact
    data object Failed : PlayerFact
    data object ControlFailed : PlayerFact
}

enum class PresentationProof { BrowserCompositorPresented, AndroidWindowGpuCompleted, AndroidSoftwareDrawCompleted, DesktopDrawCompleted, NativeRendererPresented }

@Composable
expect fun PlayerSurface(input: PlaybackInput, paused: Boolean, modifier: Modifier, observe: (PlayerFact) -> Unit, commands: kotlinx.coroutines.flow.Flow<PlayerCommand>)
