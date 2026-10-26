package org.viptv.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.viptv.app.player.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

/** Opt-in owned-fixture checks; never constructs an account, vault or application controller. */
internal class DesktopPlaybackProbe(private val result: Path?) {
    val progress = MutableStateFlow<PlayerFact.Progress?>(null)
    val properties = MutableStateFlow<PlayerFact.Properties?>(null)
    val tracks = MutableStateFlow(PlaybackTracks())
    val status = MutableStateFlow("")
    val presented = MutableStateFlow(false)
    private var failure = false
    private var proof: String? = null
    private val diagnostics = ArrayDeque<String>()

    fun observe(fact: PlayerFact) {
        when (fact) {
            is PlayerFact.Progress -> progress.value = fact
            is PlayerFact.Properties -> properties.value = fact
            is PlayerFact.Tracks -> tracks.value = fact.options
            is PlayerFact.Status -> { status.value = fact.name; println("VIPTV_DESKTOP_QA: state=${fact.name}") }
            is PlayerFact.Presentation -> { proof = fact.proof.name; presented.value = true }
            PlayerFact.Failed, PlayerFact.ControlFailed -> failure = true
        }
    }
    fun diagnostic(value: String) {
        if (diagnostics.size == 8) diagnostics.removeFirst()
        diagnostics.addLast(value)
        System.err.println("VIPTV_DESKTOP_QA: $value")
    }
    private fun record(phase: String, passed: Boolean? = null, message: String? = null) {
        println("VIPTV_DESKTOP_QA: phase=$phase")
        val body = buildJsonObject {
            put("phase", phase); passed?.let { put("passed", it) }; message?.let { put("message", it) }
            put("presentation", proof); put("position_ms", progress.value?.positionMillis)
            put("subtitle_scale", properties.value?.subtitleSize); put("audio_tracks", tracks.value.audio.size)
            put("subtitle_selected", tracks.value.subtitles.any { it.selected }); put("control_failure", failure)
            put("diagnostics", JsonArray(diagnostics.map(::JsonPrimitive)))
        }.toString()
        result?.let { Files.writeString(it, body) }
    }

    suspend fun run(input: PlaybackInput, commands: MutableSharedFlow<PlayerCommand>, pause: (Boolean) -> Unit) {
        try {
            if (System.getenv("VIPTV_DESKTOP_QA_PAUSED") == "1") {
                withTimeout(25_000) { presented.filter { it }.first() }
                withTimeout(5_000) { status.first { it == "Paused" } }
                record("paused-first-frame")
                pause(false)
            }
            withTimeout(25_000) { presented.filter { it }.first(); progress.filterNotNull().first { it.positionMillis > input.positionMillis + 400 } }
            withTimeout(5_000) { properties.filterNotNull().first { abs(it.subtitleSize - input.preferences.subtitle_font_scale.toFloat()) < .01f } }
            withTimeout(5_000) { tracks.first { it.subtitles.any { t -> t.selected } } }
            commands.emit(PlayerCommand.Volume(.42f))
            withTimeout(5_000) { properties.filterNotNull().first { abs(it.volume - .42f) < .01f } }
            commands.emit(PlayerCommand.Speed(1.25))
            withTimeout(5_000) { properties.filterNotNull().first { abs(it.speed - 1.25) < .01 } }
            commands.emit(PlayerCommand.Speed(1.0))
            record("playing")
            delay(1000)
            pause(true); withTimeout(5_000) { status.first { it.equals("Paused", true) } }
            delay(200); val before = requireNotNull(progress.value).positionMillis
            delay(600); check(abs(requireNotNull(progress.value).positionMillis - before) < 250) { "Paused position advanced" }
            record("paused")
            pause(false); withTimeout(8_000) { progress.filterNotNull().first { it.positionMillis > before + 400 } }
            commands.emit(PlayerCommand.Seek(8000))
            withTimeout(8_000) { progress.filterNotNull().first { it.positionMillis in 7700..10000 } }
            pause(true); withTimeout(5_000) { status.first { it.equals("Paused", true) } }
            commands.emit(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, null))
            withTimeout(5_000) { tracks.first { it.subtitles.none { t -> t.selected } } }
            record("caption-off"); delay(1200)
            commands.emit(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, input.providerTrackId("desktop-fixture")))
            withTimeout(5_000) { tracks.first { it.subtitles.any { t -> t.id == input.providerTrackId("desktop-fixture") && t.selected } } }
            record("caption-on"); delay(1200)
            commands.emit(PlayerCommand.SubtitleSize(1.25f))
            withTimeout(5_000) { properties.filterNotNull().first { abs(it.subtitleSize - 1.25f) < .01f } }
            record("caption-sized"); delay(1200)
            val audio = tracks.value.audio
            check(audio.size >= 2) { "Owned fixture must expose two audio tracks" }
            commands.emit(PlayerCommand.SelectTrack(PlaybackTrackKind.Audio, audio.last().id))
            withTimeout(5_000) { tracks.first { it.audio.any { t -> t.id == audio.last().id && t.selected } } }
            check(!failure) { "A player/control failure was reported" }
            record("complete", passed = true)
        } catch (error: Exception) {
            record("failed", passed = false, message = error.message ?: error.javaClass.simpleName)
        }
    }
}
