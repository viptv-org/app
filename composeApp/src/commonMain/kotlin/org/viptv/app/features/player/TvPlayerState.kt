package org.viptv.app.features.player

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.viptv.app.player.*
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

/** View state and remote interaction; playback position changes only through player observations. */
internal class TvPlayerState(input: PlaybackInput?, initial: List<PlayerFact>, scene: TvPlayerScene) {
    val live = input?.live == true
    val commands = MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 16)
    var position by mutableLongStateOf(input?.let { it.positionMillis + it.timelineOffsetMillis } ?: 0)
    var duration by mutableStateOf<Long?>(input?.durationMillis)
    var tracks by mutableStateOf(PlaybackTracks())
    var subtitleSize by mutableFloatStateOf(1f)
    var buffering by mutableStateOf(input != null || scene == TvPlayerScene.Buffer)
    var hasPresented by mutableStateOf(input == null && scene != TvPlayerScene.Buffer)
    private var seekTarget: Long? = null
    private var seekStarted = TimeSource.Monotonic.markNow()
    var failed by mutableStateOf(scene == TvPlayerScene.Error)
    var visible by mutableStateOf(scene !in listOf(TvPlayerScene.Skip, TvPlayerScene.Buffer, TvPlayerScene.Error, TvPlayerScene.Next))
    var showTracks by mutableStateOf(scene == TvPlayerScene.Tracks)
    var showNext by mutableStateOf(scene == TvPlayerScene.Next)
    var showEpisodes by mutableStateOf(false)
    /** The player reports the end of the media; the next-episode panel opens once per end. */
    var ended by mutableStateOf(false)
    var endHandled by mutableStateOf(false)
    var scrub by mutableStateOf<Long?>(if (scene == TvPlayerScene.Scrub) 1300000 else null)
    var timelineFocused by mutableStateOf(false)
    var rootFocused by mutableStateOf(false)
    var focusAudio by mutableStateOf(false)
    var dismissTracks: (() -> Unit)? = null
    var interaction by mutableIntStateOf(0)
    var controlError by mutableStateOf<String?>(null)
    /**
     * Position advances but no frame has ever been presented: the device opened a
     * decoder it cannot actually display (a Dolby Vision profile the TV's HEVC
     * decoder reports as unsupported, for instance). Watching a black picture with
     * a moving timeline is worse than saying so.
     */
    var undecodable by mutableStateOf(false)
    private var lastPosition = position
    private var advancingTicks = 0
    init { initial.forEach(::observe) }

    /** Called from a 1 s tick while the player is on screen. */
    fun checkFirstFrame() {
        if (hasPresented || failed || undecodable) return
        val moved = position != lastPosition
        lastPosition = position
        // Audio/clock advancing for 6 consecutive ticks without a single presented
        // frame means the video path is not producing pictures. A stalled clock is
        // a slow start (a torrent waiting on peers), not an undecodable stream.
        advancingTicks = if (moved && !buffering) advancingTicks + 1 else 0
        if (advancingTicks >= 6) undecodable = true
    }

    fun observe(fact: PlayerFact) {
        when (fact) {
            is PlayerFact.Progress -> {
                duration = fact.durationMillis ?: duration
                val target = seekTarget
                if (target == null || kotlin.math.abs(fact.positionMillis - target) <= 1500 || seekStarted.elapsedNow() >= 10.seconds) {
                    position = fact.positionMillis; seekTarget = null
                }
            }
            is PlayerFact.Tracks -> tracks = fact.options
            is PlayerFact.Properties -> subtitleSize = fact.subtitleSize
            is PlayerFact.Status -> {
                buffering = fact.name in listOf("Opening", "Buffering", "Preparing")
                ended = fact.name == "Ended"
                if (!ended) endHandled = false
            }
            is PlayerFact.Presentation -> { hasPresented = true; buffering = false; undecodable = false }
            PlayerFact.Failed -> { buffering = false; failed = true }
            PlayerFact.ControlFailed -> controlError = "Could not apply this playback control."
        }
    }
    /** Close the next-episode panel; at the end it stays closed until the media ends again. */
    fun keepWatching() { showNext = false; visible = false; endHandled = true }
    /** At the end of a series episode with a known next one, offer it once; a withdrawn next closes it. */
    fun offerNext(available: Boolean) {
        if (showNext && !available) showNext = false
        else if (ended && !endHandled && available && !failed && !showNext) {
            showTracks = false; showEpisodes = false; scrub = null; showNext = true
        }
    }
    fun reveal(audio: Boolean = false) { focusAudio = audio; visible = true; interaction++ }
    fun send(command: PlayerCommand, preview: ((PlayerCommand) -> Unit)?) {
        if (live && command is PlayerCommand.Seek) return
        if (command is PlayerCommand.Seek) {
            seekTarget = command.positionMillis; position = command.positionMillis
            seekStarted = TimeSource.Monotonic.markNow()
        }
        commands.tryEmit(command); preview?.invoke(command); interaction++; controlError = null
    }
    fun step(direction: Int) {
        if (live) return
        val length = duration?.takeIf { it > 0 } ?: return
        val increment = if (length < 60000) 1000L else 10000L
        scrub = ((scrub ?: position) + direction * increment).coerceIn(0, length)
        reveal()
    }
    fun commit(preview: ((PlayerCommand) -> Unit)?) {
        scrub?.let { send(PlayerCommand.Seek(it), preview) }; scrub = null
    }
    fun back(close: () -> Unit): Boolean {
        when {
            showTracks -> dismissTracks?.invoke() ?: run { showTracks = false }
            showEpisodes -> showEpisodes = false
            showNext -> keepWatching()
            scrub != null -> { scrub = null; reveal() }
            visible && !failed -> visible = false
            else -> { close(); return false }
        }
        interaction++
        return true
    }
}
