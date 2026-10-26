package org.viptv.app.player.mpv

import com.sun.jna.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.viptv.app.player.*
import org.viptv.app.connection.subtitleMode
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal data class MpvState(
    val loaded: Boolean = false, val positionMs: Long = 0, val videoMs: Long = 0, val durationMs: Long? = null,
    val status: String = "Opening", val volume: Float = 1f, val speed: Double = 1.0, val subtitleScale: Float = 1f,
    val audio: List<PlaybackTrack> = emptyList(), val subtitles: List<PlaybackTrack> = emptyList(),
    val width: Int = 0, val height: Int = 0, val hwdec: String = "no", val dropped: Long = 0,
    val sourceFps: Double = 0.0, val buffering: Boolean = false, val error: Int? = null,
)

/**
 * libmpv's 8-digit colour form is `#AARRGGBB` (mpv(1)), which matches the
 * profile preference Int. Sending `#RRGGBBAA` swaps alpha into the red
 * channel, so gold (`#FFFFC107`) renders as purple (`#C107FF`).
 */
internal fun mpvSubtitleColour(value: Int): String = "#%08X".format(value)

internal class MpvSession(input: PlaybackInput) {
    val api = MpvNative.api
    // The JVM's startup setlocale(LC_ALL, "") leaves the host's numeric locale
    // in place, and libmpv refuses to create a client unless LC_NUMERIC is "C".
    // Without this, desktop playback fails on every non-C locale (MpvNativeTest
    // reproduced it under LANG=en_US.UTF-8).
    val handle = run { MpvLocale.requireNumericCLocale(); requireNotNull(api.mpv_create()) }
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "mpv-control").apply { isDaemon = true } }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val closing = AtomicBoolean(false)
    private var loaded = false
    @Volatile private var error: Int? = null
    val state = MutableStateFlow(MpvState())
    @Volatile var presentationAllowed = false
    val closed = CompletableDeferred<Unit>()
    val rendererReady = CompletableDeferred<Unit>()

    init {
        try {
            fun option(name: String, value: String) = mpvCheck(api.mpv_set_option_string(handle, name, value))
            option("config", "no"); option("terminal", "no"); option("input-default-bindings", "no")
            option("load-scripts", "no"); option("osc", "no"); option("ytdl", "no")
            option("vo", "libmpv"); option("pause", "yes"); option("keep-open", "yes")
            option("hwdec", "auto-safe"); option("network-timeout", "30")
            option("cache", "yes"); option("cache-pause-initial", "no")
            option("demuxer-max-bytes", (64L shl 20).toString()); option("demuxer-max-back-bytes", (16L shl 20).toString())
            option("demuxer-readahead-secs", "10"); option("video-timing-offset", "0")
            input.preferences.preferred_audio_language?.let { option("alang", it) }
            val subtitles = input.preferences.subtitleMode != "never"
            option("sub-auto", "no"); option("sid", if (subtitles) "auto" else "no")
            if (subtitles) input.preferences.preferred_subtitle_language?.let { option("slang", it) }
            val caption = desktopSubtitleConfig(input.preferences)
            option("sub-scale", caption.fontScale.toString())
            caption.style?.let { style ->
                option("sub-ass-override", "force")
                style.primaryColor?.let { option("sub-color", mpvSubtitleColour(it)) }
                style.backgroundColor?.let { option("sub-back-color", mpvSubtitleColour(it)); option("sub-border-style", "background-box") }
            }
            if (input.headers.isNotEmpty()) setHeaders(input.headers)
            mpvCheck(api.mpv_initialize(handle))
        } catch (failure: Throwable) {
            api.mpv_terminate_destroy(handle); dispatcher.close(); throw failure
        }
        scope.launch {
            while (isActive && !closing.get()) {
                for (i in 0 until 64) {
                    val event = api.mpv_wait_event(handle, 0.0)
                    when (event.getInt(0)) {
                        0 -> break
                        8 -> loaded = true
                        7 -> event.getPointer(16)?.let { end -> if (end.getInt(0) == 4) error = end.getInt(4) }
                    }
                }
                state.value = snapshot()
                delay(50)
            }
        }
    }

    fun rendererFailed() { error = -1; state.value = state.value.copy(error = -1, status = "Failed") }

    private fun setHeaders(headers: Map<String, String>) = MpvParams().use { p ->
        val entries = headers.map { (name, value) ->
            require(name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) && value.none { it == '\r' || it == '\n' || it == '\u0000' })
            p.text("$name: $value")
        }
        val nodes = p.memory(entries.size * 16L)
        entries.forEachIndexed { i, text -> nodes.setPointer(i * 16L, text); nodes.setInt(i * 16L + 8, 1) }
        val list = p.memory(24).also { it.setInt(0, entries.size); it.setPointer(8, nodes) }
        val node = p.memory(16).also { it.setPointer(0, list); it.setInt(8, 7) }
        mpvCheck(api.mpv_set_option(handle, "http-header-fields", 6, node))
    }

    private fun text(name: String): String? = api.mpv_get_property_string(handle, name)?.let { p ->
        try { p.getString(0, "UTF-8") } finally { api.mpv_free(p) }
    }
    private fun number(name: String) = text(name)?.toDoubleOrNull()?.takeIf { it.isFinite() }
    private fun node(name: String): JsonElement = Memory(16).use { p ->
        p.clear()
        if (api.mpv_get_property(handle, name, 6, p) < 0) JsonNull else try { mpvNode(p) } finally { api.mpv_free_node_contents(p) }
    }
    private fun snapshot(): MpvState {
        val tracks = (node("track-list") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        fun list(type: String) = tracks.filter { it["type"]?.jsonPrimitive?.content == type }.mapNotNull { track ->
            val id = track["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            PlaybackTrack(id, track["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: track["lang"]?.jsonPrimitive?.contentOrNull ?: "$type $id", track["selected"]?.jsonPrimitive?.booleanOrNull == true,
                track["lang"]?.jsonPrimitive?.contentOrNull)
        }
        val buffering = text("paused-for-cache") == "yes"
        return MpvState(loaded, ((number("time-pos") ?: 0.0) * 1000).toLong(), ((number("video-pts") ?: number("time-pos") ?: 0.0) * 1000).toLong(),
            number("duration")?.let { (it * 1000).toLong() },
            when { error != null -> "Failed"; !loaded -> "Opening"; text("eof-reached") == "yes" -> "Ended"; buffering -> "Buffering"; text("pause") == "yes" -> "Paused"; else -> "Playing" },
            ((number("volume") ?: 100.0) / 100).toFloat(), number("speed") ?: 1.0, (number("sub-scale") ?: 1.0).toFloat(),
            list("audio"), list("sub"), number("video-params/w")?.toInt() ?: 0, number("video-params/h")?.toInt() ?: 0,
            text("hwdec-current") ?: "no", number("frame-drop-count")?.toLong() ?: 0, number("container-fps") ?: 0.0, buffering, error)
    }
    suspend fun command(vararg args: String) = withContext(dispatcher) {
        check(!closing.get()); require(args.none { '\u0000' in it })
        mpvCheck(api.mpv_command(handle, StringArray(args, "UTF-8")))
    }
    suspend fun property(name: String, value: String) = withContext(dispatcher) {
        check(!closing.get()); mpvCheck(api.mpv_set_property_string(handle, name, value))
    }
    suspend fun open(input: PlaybackInput) {
        rendererReady.await()
        if (input.positionMillis > 0) property("start", (input.positionMillis / 1000.0).toString())
        command("loadfile", input.uri, "replace")
        withTimeout(35_000) { state.first { it.loaded || it.error != null }.also { check(it.error == null) } }
    }
    suspend fun selectedAudio(): Int? = withContext(dispatcher) { number("aid")?.toInt() }
    fun closeAfterRenderer() {
        if (!closing.compareAndSet(false, true)) return
        scope.coroutineContext.cancelChildren()
        CoroutineScope(dispatcher).launch {
            try { api.mpv_terminate_destroy(handle) } finally { dispatcher.close(); closed.complete(Unit) }
        }
    }
}
