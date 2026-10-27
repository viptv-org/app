package org.viptv.app.player

import android.view.View
import android.view.ViewGroup
import androidx.media3.common.*
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.*
import org.viptv.app.connection.subtitleMode
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
    /** Embedded and stream text, drawn in place of PlayerView's own subtitle view so its cues stack too (app#43). */
    private var embedded: SubtitleView? = null
    private val embeddedCues = object : Player.Listener {
        override fun onCues(cueGroup: CueGroup) { embedded?.showCues(cueGroup.cues) }
    }
    private var shown: List<Cue>? = null
    private var scale = input.preferences.subtitle_font_scale.toFloat().coerceIn(.5f, 2f)
    private val ticker = scope.launch { while (isActive) { delay(25); render() } }
    init { player.addListener(embeddedCues) }

    fun bind(value: PlayerView?) {
        if (view === value) return
        view?.subtitleView?.visibility = View.VISIBLE
        listOfNotNull(overlay, embedded).forEach { (it.parent as? ViewGroup)?.removeView(it) }
        view = value
        overlay = value?.let { owner -> subtitleLayer(owner, requireNotNull(owner.overlayFrameLayout)) }
        // Same parent as PlayerView's own view: the video's frame, where positioned and bitmap cues belong.
        embedded = value?.let { owner -> owner.subtitleView?.let { builtIn ->
            builtIn.visibility = View.INVISIBLE
            subtitleLayer(owner, builtIn.parent as ViewGroup).also { it.showCues(player.currentCues.cues) }
        } }
        shown = null
        render()
    }

    private fun subtitleLayer(owner: PlayerView, parent: ViewGroup) = SubtitleView(owner.context).also { layer ->
        layer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layer.profileStyle(input.preferences, scale)
        parent.addView(layer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun size(value: Float) { scale = value; listOfNotNull(overlay, embedded).forEach { it.profileStyle(input.preferences, scale) } }

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
        if (closed || touched || player.currentTracks.isEmpty || player.currentMediaItem == null) return
        if (input.preferences.subtitleMode == "foreign") { foreignAudio(values); return }
        if (desired != null || pending != null) return
        val language = input.preferences.preferred_subtitle_language
        if (player.currentTracks.groups.any { group -> group.type == C.TRACK_TYPE_TEXT &&
                (0 until group.length).any { group.isTrackSelected(it) && group.getTrackFormat(it).language == language } }) return
        preferredProviderSubtitle(input, values)?.let(::attach)
    }

    // PLAY-SUBTITLE-MODE-001 "When audio isn't …": decided again whenever the
    // selected audio's language changes, until the viewer picks a subtitle or Off.
    private var decidedAudio: String? = null
    private var decided = false
    private var foreignSatisfied = false

    private fun foreignAudio(values: List<SubtitleCandidate>) {
        val audio = player.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }?.let { group ->
            (0 until group.length).firstOrNull(group::isTrackSelected)?.let { group.getTrackFormat(it).language.orEmpty() }
        } ?: return
        if (!decided || audio != decidedAudio) { decided = true; decidedAudio = audio; foreignSatisfied = false; automaticOff() }
        if (foreignSatisfied) return
        val preferences = input.preferences
        val embedded = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.mapNotNull { index ->
                val format = group.getTrackFormat(index)
                if (format.id?.startsWith(Media3Tracks.PROVIDER_PREFIX) == true) null
                else SubtitleModeTrack(group.mediaTrackGroup to index, format.language, format.selectionFlags and C.SELECTION_FLAG_FORCED != 0)
            }
        }
        val track = subtitleModeChoice("foreign", preferences.preferred_audio_language, preferences.preferred_subtitle_language, audio, embedded)
        if (track != null) {
            foreignSatisfied = true
            val (group, index) = track.key
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(group, listOf(index))).build()
            return
        }
        if (!audioIsForeign(audio, preferences.preferred_audio_language) || desired != null || pending != null) return
        preferredProviderSubtitle(input, values)?.let { foreignSatisfied = true; attach(it) }
    }

    /** Undo only this mode's own choice: embedded text off and any sidecar it attached. */
    private fun automaticOff() {
        generation++; job?.cancel(); job = null; pending = null
        if (desired != null) clear()
        val parameters = player.trackSelectionParameters
        if (!parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || parameters.overrides.keys.any { it.type == C.TRACK_TYPE_TEXT })
            player.trackSelectionParameters = parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
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
            generation++; job?.cancel(); job = null; pending = null; foreignSatisfied = false
        }
        if (desired?.let { id -> values.none { it.id == id } } == true) { clear(); foreignSatisfied = false }
        autoSelect(values)
    }

    private fun clear() { timeline = null; desired = null; shown = null; tracks.providerSelected(null); render() }
    private fun render() {
        if (closed) return
        embedded?.visibility = if (timeline == null) View.VISIBLE else View.INVISIBLE
        val cues = timeline?.cuesAt(positionMillis().coerceAtLeast(0) * 1000) ?: emptyList()
        if (cues !== shown) {
            overlay?.showCues(cues); shown = cues
            input.diagnostics?.invoke("sidecar_active=${timeline != null} sidecar_cues=${cues.size} sidecar_position_ms=${positionMillis()}")
        }
    }
    fun close() {
        if (closed) return
        generation++; closed = true; job?.cancel(); ticker.cancel(); timeline = null; player.removeListener(embeddedCues); bind(null)
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
