@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.viptv.app.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import org.viptv.app.connection.subtitleMode
import platform.AVFoundation.*

internal class AVProviderSubtitles(private val player: AVPlayer, private val input: PlaybackInput,
    private val scope: CoroutineScope, private val observe: (PlayerFact) -> Unit) {
    var text by mutableStateOf("")
        private set
    private var timeline: SidecarCueTimeline<String>? = null
    private var desired: String? = null
    private var pending: String? = null
    private var touched = false
    private var closed = false
    private var generation = 0L
    private var job: Job? = null
    private var reported: PlaybackTracks? = null
    private var audioSelection: AVMediaSelectionOption? = null
    private val attempted = mutableSetOf<String>()
    private var automaticDecision: String? = null

    private fun group(kind: PlaybackTrackKind) = player.currentItem?.asset?.mediaSelectionGroupForMediaCharacteristic(
        if (kind == PlaybackTrackKind.Audio) AVMediaCharacteristicAudible else AVMediaCharacteristicLegible)

    fun refresh() {
        if (closed) return
        val item = player.currentItem ?: return
        val selectedAudio = group(PlaybackTrackKind.Audio)?.let { item.currentMediaSelection.selectedMediaOptionInMediaSelectionGroup(it) }
        if (selectedAudio != audioSelection) {
            audioSelection = selectedAudio
            if (!touched) { clear(); automaticDecision = null; attempted.clear() }
            input.subtitleSync.audioChanged(selectedAudioIdentity())
        }
        if (desired != null) group(PlaybackTrackKind.Subtitle)?.let { group ->
            if (item.currentMediaSelection.selectedMediaOptionInMediaSelectionGroup(group) != null) item.selectMediaOption(null, group)
        }
        val tracks = PlaybackTracks(options(PlaybackTrackKind.Audio), options(PlaybackTrackKind.Subtitle) + input.providerTracks(desired))
        if (tracks != reported) { reported = tracks; observe(PlayerFact.Tracks(tracks)) }
        if (!touched && desired == null && pending == null && item.status == AVPlayerItemStatusReadyToPlay) {
            val preferences = input.preferences
            val provider = preferredProviderSubtitle(input, input.providerSubtitles.value)
            val embedded = options(PlaybackTrackKind.Subtitle)
            val decision = "${selectedAudioIdentity()}:${embedded.map { it.id to it.language }}:${input.providerSubtitles.value.map { it.id }}"
            if (automaticDecision != decision) {
                automaticDecision = decision
                val choice = subtitleModeChoice(preferences.subtitleMode, preferences.preferred_audio_language,
                    preferences.preferred_subtitle_language, selectedAudio?.extendedLanguageTag,
                    embedded.map { track -> SubtitleModeTrack(track.id, track.language) })
                group(PlaybackTrackKind.Subtitle)?.let { group -> item.selectMediaOption(choice?.key?.let { group.options[it] as AVMediaSelectionOption }, group) }
                val eligible = subtitleModeEnabled(preferences.subtitleMode, preferences.preferred_audio_language, selectedAudio?.extendedLanguageTag)
                if (choice == null && eligible && provider != null && provider.id !in attempted) attach(provider.id, false)
            }
        }
    }

    private fun options(kind: PlaybackTrackKind): List<PlaybackTrack> {
        val item = player.currentItem ?: return emptyList()
        val group = group(kind) ?: return emptyList()
        val selected = item.currentMediaSelection.selectedMediaOptionInMediaSelectionGroup(group)
        return group.options.mapIndexed { index, value ->
            val option = value as AVMediaSelectionOption
            PlaybackTrack(index, option.displayName, selected == option && (kind == PlaybackTrackKind.Audio || desired == null), option.extendedLanguageTag)
        }
    }

    fun select(kind: PlaybackTrackKind, id: Int?) {
        if (kind == PlaybackTrackKind.Subtitle) {
            touched = true
            val candidate = input.providerSubtitle(id)
            if (candidate != null) { attach(candidate.id, true); return }
        }
        val item = requireNotNull(player.currentItem)
        val group = group(kind)
        if (id != null) require(group != null && id >= 0 && id < group.options.size)
        if (kind == PlaybackTrackKind.Subtitle) clear()
        group?.let { item.selectMediaOption(id?.let { index -> it.options[index] as AVMediaSelectionOption }, it) }
        if (kind == PlaybackTrackKind.Audio) input.subtitleSync.discontinuity()
        refresh()
    }

    fun sourcesChanged() {
        if ((desired ?: pending)?.let { id -> input.providerSubtitles.value.none { it.id == id } } == true) clear()
        refresh()
    }

    private fun attach(id: String, chosen: Boolean) {
        attempted += id
        val operation = ++generation
        job?.cancel()
        pending = id
        job = scope.launch {
            try {
                val bytes = requireNotNull(input.subtitleContent)(id)
                val parsed = withContext(Dispatchers.Default) { parseProviderTextCues(bytes) }
                ensureActive()
                if (closed || generation != operation || input.providerSubtitles.value.none { it.id == id }) return@launch
                group(PlaybackTrackKind.Subtitle)?.let { player.currentItem?.selectMediaOption(null, it) }
                timeline = parsed
                desired = id
                input.subtitleSync.select(SubtitleSyncIdentity(input.id, selectedAudioIdentity(), id))
                refresh()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (!closed && generation == operation) input.controlFailed(PlaybackControl.Track, error.controlCause(), if (chosen) observe else null)
            } finally { if (generation == operation) { pending = null; job = null } }
        }
    }

    fun render(titleUs: Long) {
        var cues = timeline?.cuesAt(input.subtitleSync.subtitleTimeUs(titleUs)) ?: emptyList()
        if (cues.isEmpty()) cues = timeline?.cuesAt(input.subtitleSync.subtitleTimeUs(titleUs, cueGap = true)) ?: emptyList()
        text = cues.joinToString("\n")
    }

    private fun selectedAudioIdentity() = group(PlaybackTrackKind.Audio)?.options?.indexOf(audioSelection).toString()

    private fun clear() {
        generation++
        job?.cancel(); job = null; pending = null
        timeline = null; desired = null; text = ""
        input.subtitleSync.select(null)
    }

    fun close() { closed = true; clear() }
}
