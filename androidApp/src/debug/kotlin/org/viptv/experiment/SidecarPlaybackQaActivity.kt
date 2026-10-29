package org.viptv.experiment

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.viptv.app.player.*
import org.viptv.app.connection.ProfilePreferences
import org.viptv.engine.wire.SubtitleCandidate

class SidecarPlaybackQaActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 16)
    private var paused by mutableStateOf(false)
    private var surfaceVisible by mutableStateOf(true)
    private var progress: PlayerFact.Progress? = null
    private var tracks = PlaybackTracks()
    private val diagnostics = mutableMapOf<String, String>()
    private var failures = 0
    private var controlFailures = 0
    private var subtitleScale = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = requireNotNull(intent.getStringExtra("qa_media_uri"))
        val parsed = android.net.Uri.parse(uri)
        require(parsed.scheme == "http" && parsed.host == "10.0.2.2")
        val candidates = listOf("srt", "vtt", "ass", "slow", "invalid").map { SubtitleCandidate(it, "local-qa", "en", it) }
        val input = PlaybackInput("sidecar-isolated-qa", uri, timelineOffsetMillis = 600_000, durationMillis = 660_000,
            preferences = ProfilePreferences(subtitle_font_scale = 1.25, subtitle_text_color = "#FFE16B", subtitle_background_color = "#B3000000"),
            providerSubtitles = candidates, subtitleContent = { id ->
                if (id == "slow") withContext(NonCancellable) { delay(700) }
                subtitleFixture(id).encodeToByteArray()
            }, diagnostics = { line ->
                line.split(' ').mapNotNull { token -> token.split('=', limit = 2).takeIf { it.size == 2 } }
                    .forEach { diagnostics[it[0]] = it[1] }
            })
        setContent {
            if (surfaceVisible) PlayerSurface(input, paused, Modifier.layout { child, constraints ->
                val measured = child.measure(constraints.copy(minWidth = constraints.maxWidth, minHeight = constraints.maxHeight))
                layout(measured.width, measured.height) { measured.place(0, 0) }
            }, { fact -> when (fact) {
                is PlayerFact.Progress -> progress = fact
                is PlayerFact.Tracks -> tracks = fact.options
                is PlayerFact.Properties -> subtitleScale = fact.subtitleSize
                PlayerFact.Failed -> failures++
                PlayerFact.ControlFailed -> controlFailures++
                else -> Unit
            } }, commands)
        }
        scope.launch {
            try {
                await { progress != null && tracks.audio.any { it.selected } }
                await { (diagnostics["window_offset_ms"]?.toLongOrNull() ?: 0) >= 4_000 }
                paused = true; delay(400)
                val position = requireNotNull(progress).positionMillis
                val windowOffset = diagnostics["window_offset_ms"]
                val audio = tracks.audio.first { it.selected }.id
                val embedded = tracks.subtitles.firstOrNull { it.id >= 0 }?.id
                    ?: error("Fixture embedded subtitle track missing")
                commands.emit(PlayerCommand.SelectTrack(PlaybackTrackKind.Audio, audio))
                commands.emit(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, embedded))
                await { tracks.subtitles.any { it.id == embedded && it.selected } }
                val transitions = diagnostics["media_item_transitions"]
                require(transitions == "1")
                require(subtitleScale == 1.25f) { "Saved subtitle font scale ignored" }
                for (index in 0..2) {
                    val id = Int.MIN_VALUE + index
                    commands.emit(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, id))
                    await { tracks.subtitles.any { it.id == id && it.selected } && diagnostics["sidecar_cues"] == "1" }
                    delay(150)
                    require(requireNotNull(progress).positionMillis == position) { "Subtitle changed title position" }
                    require(tracks.audio.single { it.selected }.id == audio) { "Subtitle changed audio selection" }
                    Log.i("VIPTV_SIDECAR_QA", "phase=${listOf("srt", "vtt", "ass")[index]} position_ms=$position")
                    delay(500)
                }
                val target = position + 1500
                commands.emit(PlayerCommand.Seek(target))
                await { progress?.positionMillis?.let { kotlin.math.abs(it - target) < 100 } == true }
                require(diagnostics["sidecar_cues"] == "1") { "Seek lost active sidecar" }
                commands.emit(PlayerCommand.Seek(position))
                await { progress?.positionMillis?.let { kotlin.math.abs(it - position) < 100 } == true }
                commands.emit(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, Int.MIN_VALUE + 3))
                delay(50)
                commands.emit(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, Int.MIN_VALUE + 1))
                await { tracks.subtitles.any { it.id == Int.MIN_VALUE + 1 && it.selected } }
                delay(900)
                require(tracks.subtitles.single { it.selected }.id == Int.MIN_VALUE + 1) { "Cancelled subtitle replaced selection" }
                commands.emit(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, Int.MIN_VALUE + 4))
                await { controlFailures == 1 }
                require(tracks.subtitles.single { it.selected }.id == Int.MIN_VALUE + 1)
                commands.emit(PlayerCommand.SubtitleSize(1.5f))
                await { subtitleScale == 1.5f }
                commands.emit(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, null))
                await { tracks.subtitles.none { it.selected } && diagnostics["sidecar_active"] == "false" }
                commands.emit(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, embedded))
                await { tracks.subtitles.any { it.id == embedded && it.selected } }
                delay(2200)
                require(diagnostics["media_item_transitions"] == transitions) { "Primary media item replaced" }
                require(failures == 0)
                commands.emit(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, Int.MIN_VALUE + 3))
                delay(50); surfaceVisible = false; delay(900)
                require(controlFailures == 1) { "Closed request emitted a failure" }
                tracks = PlaybackTracks(); surfaceVisible = true
                await { tracks.audio.isNotEmpty() }
                require(tracks.subtitles.none { it.id < 0 && it.selected }) { "Closed request crossed session replacement" }
                Log.i("VIPTV_SIDECAR_QA", "PASS position_ms=$position window_offset_ms=$windowOffset media_item_transitions=$transitions formats=srt,vtt,ass audio_preserved=true embedded_restored=true seek_aligned=true cancellation_isolated=true replacement_isolated=true malformed_preserved=true off=true")
            } catch (error: Exception) {
                Log.e("VIPTV_SIDECAR_QA", "FAIL ${error.javaClass.simpleName}: ${error.message}", error)
            }
        }
    }
    private suspend fun await(predicate: () -> Boolean) = withTimeout(30_000) { while (!predicate()) { check(failures == 0) { "Media failed" }; delay(25) } }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
