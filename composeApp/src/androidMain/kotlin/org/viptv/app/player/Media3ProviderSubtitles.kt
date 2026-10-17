package org.viptv.app.player

import android.view.View
import android.view.ViewGroup
import androidx.media3.common.*
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.*
import org.viptv.engine.wire.SubtitleCandidate

/** Sidecars never replace the primary MediaSource: its rolling HLS origin and audio stay intact. */
@UnstableApi
internal class Media3ProviderSubtitles(private val player: ExoPlayer, private val input: PlaybackInput,
    private val scope: CoroutineScope, private val tracks: Media3Tracks, private val positionMillis: () -> Long,
    private val failed: () -> Unit) {
    private var job: Job? = null
    private var generation = 0L
    private var closed = false
    private var touched = false
    private var pending: String? = null
    private var desired: String? = null
    private var timeline: SidecarCueTimeline<Cue>? = null
    private var view: PlayerView? = null
    private var overlay: SubtitleView? = null
    private var shown: List<Cue>? = null
    private var scale = input.preferences.subtitle_font_scale.toFloat().coerceIn(.5f, 2f)
    private val ticker = scope.launch { while (isActive) { delay(25); render() } }

    fun bind(value: PlayerView?) {
        if (view === value) return
        view?.subtitleView?.visibility = View.VISIBLE
        overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        view = value
        overlay = value?.let { owner -> SubtitleView(owner.context).also { layer ->
            layer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layer.profileStyle(input.preferences, scale)
            requireNotNull(owner.overlayFrameLayout).addView(layer,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } }
        shown = null
        render()
    }

    fun size(value: Float) { scale = value; overlay?.profileStyle(input.preferences, scale) }

    suspend fun select(id: Int?) {
        touched = true
        generation++; job?.cancel(); job = null; pending = null
        val candidate = input.providerSubtitle(id)
        if (candidate == null) {
            // Validate/select embedded tracks before clearing a working provider choice.
            tracks.select(PlaybackTrackKind.Subtitle, id)
            clear()
        } else attach(candidate)
    }

    fun autoSelect(values: List<SubtitleCandidate>) {
        if (closed || touched || desired != null || pending != null || player.currentTracks.isEmpty || player.currentMediaItem == null) return
        val language = input.preferences.preferred_subtitle_language
        if (player.currentTracks.groups.any { group -> group.type == C.TRACK_TYPE_TEXT &&
                (0 until group.length).any { group.isTrackSelected(it) && group.getTrackFormat(it).language == language } }) return
        preferredProviderSubtitle(input, values)?.let(::attach)
    }

    private fun attach(candidate: SubtitleCandidate) {
        val operation = ++generation
        pending = candidate.id
        job = scope.launch {
            try {
                val bytes = requireNotNull(input.subtitleContent)(candidate.id)
                val parsed = withContext(Dispatchers.Default) { parseSidecarCues(bytes) }
                ensureActive()
                if (closed || generation != operation || input.providerSubtitles.value.none { it.id == candidate.id }) return@launch
                // Only text selection changes. The media item, seek position and audio override do not.
                tracks.select(PlaybackTrackKind.Subtitle, null)
                timeline = parsed; desired = candidate.id; shown = null
                tracks.providerSelected(desired)
                render()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (!closed && generation == operation) failed() }
            finally { if (generation == operation) { pending = null; job = null } }
        }
    }

    fun sourcesChanged(values: List<SubtitleCandidate>) {
        if (pending?.let { id -> values.none { it.id == id } } == true) {
            generation++; job?.cancel(); job = null; pending = null
        }
        if (desired?.let { id -> values.none { it.id == id } } == true) clear()
        autoSelect(values)
    }

    private fun clear() { timeline = null; desired = null; shown = null; tracks.providerSelected(null); render() }
    private fun render() {
        if (closed) return
        view?.subtitleView?.visibility = if (timeline == null) View.VISIBLE else View.INVISIBLE
        val cues = timeline?.cuesAt(positionMillis().coerceAtLeast(0) * 1000) ?: emptyList()
        if (cues !== shown) {
            overlay?.setCues(cues); shown = cues
            input.diagnostics?.invoke("sidecar_active=${timeline != null} sidecar_cues=${cues.size} sidecar_position_ms=${positionMillis()}")
        }
    }
    fun close() {
        if (closed) return
        generation++; closed = true; job?.cancel(); ticker.cancel(); timeline = null; bind(null)
    }
}

@UnstableApi
internal fun parseSidecarCues(bytes: ByteArray): SidecarCueTimeline<Cue> {
    require(bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024)
    val header = bytes.take(512).toByteArray().decodeToString().trimStart('\uFEFF', ' ', '\r', '\n')
    val mime = when { header.startsWith("WEBVTT") -> MimeTypes.TEXT_VTT; header.startsWith("[Script Info]") -> MimeTypes.TEXT_SSA; else -> MimeTypes.APPLICATION_SUBRIP }
    val parser = DefaultSubtitleParserFactory().create(Format.Builder().setSampleMimeType(mime).build())
    val groups = mutableListOf<SidecarCueGroup<Cue>>()
    var count = 0L
    parser.parse(bytes, SubtitleParser.OutputOptions.allCues()) { value ->
        check(groups.size < 20_000)
        count += value.cues.size; check(count <= 50_000)
        val start = if (value.startTimeUs == C.TIME_UNSET) 0 else value.startTimeUs.coerceAtLeast(0)
        val end = value.durationUs.takeUnless { it == C.TIME_UNSET }?.let { duration ->
            require(duration >= 0 && duration <= Long.MAX_VALUE - start); start + duration
        }
        groups += SidecarCueGroup(start, end, value.cues)
    }
    require(groups.isNotEmpty()) { "No readable subtitle cues" }
    return SidecarCueTimeline(groups, parser.cueReplacementBehavior == Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE)
}
