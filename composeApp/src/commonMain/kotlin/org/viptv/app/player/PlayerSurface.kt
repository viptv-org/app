package org.viptv.app.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.asStateFlow

class PlaybackInput(val id: String, val uri: String, val headers: Map<String, String> = emptyMap(), positionMillis: Long = 0, val profileId: String? = null, val revision: Long = 0,
    providerSubtitles: List<org.viptv.engine.wire.SubtitleCandidate> = emptyList(),
    val subtitleContent: (suspend (String) -> ByteArray)? = null,
    val timelineOffsetMillis: Long = 0, val durationMillis: Long? = null,
    val remoteSeek: ((Long) -> Unit)? = null,
    preferences: org.viptv.app.connection.ProfilePreferences = org.viptv.app.connection.ProfilePreferences(), val live: Boolean = false,
    val diagnostics: ((String) -> Unit)? = null, val mimeType: String? = null,
    val requiredAudioIndex: Int? = null,
    /** Loopback media of this device's torrent runtime: reads wait on peers, not a server. */
    val localTorrent: Boolean = false) {
    init { require(requiredAudioIndex == null || requiredAudioIndex >= 0) }
    /** Media position a renderer opens at. */
    var positionMillis: Long = positionMillis
        private set
    /** Profile preferences a renderer applies when it opens. */
    var preferences: org.viptv.app.connection.ProfilePreferences = preferences
        private set

    /**
     * The same playback (and server lease) for a renderer that opens later, after this one was
     * unmounted: from title position [titlePositionMillis] with [preferences] (app#44). Only
     * call while no renderer shows this input; a mounted one keeps what it opened with.
     */
    internal fun reopenAt(titlePositionMillis: Long?, preferences: org.viptv.app.connection.ProfilePreferences) {
        if (titlePositionMillis != null && !live) positionMillis = mediaSeekPosition(titlePositionMillis)
        this.preferences = preferences
    }
    private val subtitleChoices = kotlinx.coroutines.flow.MutableStateFlow(providerSubtitles)
    private val subtitleIds = kotlinx.coroutines.flow.MutableStateFlow(providerSubtitles.map { it.id }.distinct())
    val providerSubtitles = subtitleChoices.asStateFlow()
    internal fun updateProviderSubtitles(values: List<org.viptv.engine.wire.SubtitleCandidate>) {
        while (true) {
            val previous = subtitleIds.value
            if (subtitleIds.compareAndSet(previous, (previous + values.map { it.id }).distinct())) break
        }
        subtitleChoices.value = values
    }
    internal fun providerTrackId(id: String): Int = Int.MIN_VALUE + subtitleIds.value.indexOf(id).also { require(it >= 0) }
    internal fun providerSubtitleId(track: Int): String? = (track.toLong() - Int.MIN_VALUE.toLong())
        .takeIf { it >= 0 && it < subtitleIds.value.size }?.let { subtitleIds.value[it.toInt()] }
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

internal fun playbackMimeType(format: String?): String? = when (format?.lowercase()) {
    "hls", "m3u8" -> "application/x-mpegURL"
    "dash", "mpd" -> "application/dash+xml"
    else -> null
}
