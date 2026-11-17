package org.viptv.app.player

import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@UnstableApi
internal class Media3Tracks(private val player: ExoPlayer, private val input: PlaybackInput, private val emit: (PlayerFact) -> Unit) {
    private data class Key(val group: TrackGroup, val index: Int)
    private val keys = mutableMapOf<Key, Int>()
    private var next = 1
    private val references = mutableMapOf<Int, Key>()
    private val requiredAudio = RequiredAudioSelection(input.requiredAudioIndex)
    private var requiredAudioFailed = false
    private var providerSelection: String? = null
    fun providerSelected(id: String?) { providerSelection = id; changed() }
    fun changed() {
        references.clear()
        var selectedProvider: String? = providerSelection
        fun options(type: Int): List<PlaybackTrack> = player.currentTracks.groups.filter { it.type == type }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.map { group to it }
        }.mapIndexedNotNull { ordinal, (group, index) ->
            val format = group.getTrackFormat(index)
            val external = format.id?.takeIf { it.startsWith(PROVIDER_PREFIX) }?.removePrefix(PROVIDER_PREFIX)
            if (external != null) {
                if (group.isTrackSelected(index)) selectedProvider = external
                return@mapIndexedNotNull null
            }
            val key = Key(group.mediaTrackGroup, index)
            val id = keys.getOrPut(key) { next++ }
            references[id] = key
            val language = format.language?.takeUnless { it == "und" }
            val selected = group.isTrackSelected(index) && (type != C.TRACK_TYPE_TEXT || providerSelection == null)
            if (type == C.TRACK_TYPE_TEXT) embeddedSubtitleTrack(id, ordinal + 1, format.label, language, selected,
                format.selectionFlags and C.SELECTION_FLAG_FORCED != 0, format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0)
            else PlaybackTrack(id, listOfNotNull(format.label?.takeIf { it.isNotBlank() }, language,
                format.channelCount.takeIf { it > 0 }?.let { "${it} channels" }).distinct().joinToString(" · ")
                .ifBlank { "Audio track ${index + 1}" }, selected, language)
        }
        val audio = options(C.TRACK_TYPE_AUDIO)
        val text = options(C.TRACK_TYPE_TEXT)
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        // Adaptive encodings within one group are not proven file-audio ordinals.
        val unambiguous = audioGroups.all { it.length == 1 }
        val candidates = audioGroups.flatMap { group ->
            (0 until group.length).map { index ->
                val id = keys.getOrPut(Key(group.mediaTrackGroup, index)) { next++ }
                AudioCandidate(id, unambiguous && group.isTrackSupported(index))
            }
        }
        when (val choice = requiredAudio.choose(candidates, player.currentTracks.groups.isNotEmpty())) {
            is RequiredAudioChoice.Select -> references[choice.id]?.let { key ->
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .setOverrideForType(TrackSelectionOverride(key.group, listOf(key.index))).build()
            }
            RequiredAudioChoice.Unavailable -> if (!requiredAudioFailed) {
                requiredAudioFailed = true
                player.pause()
                emit(PlayerFact.Failed)
            }
            RequiredAudioChoice.Unchanged -> Unit
        }
        emit(PlayerFact.Tracks(PlaybackTracks(audio, text + input.providerTracks(selectedProvider))))
    }
    fun select(kind: PlaybackTrackKind, id: Int?) {
        val type = if (kind == PlaybackTrackKind.Audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
        val key = id?.let(references::get)
        require(id == null || key != null)
        if (kind == PlaybackTrackKind.Audio) requiredAudio.userSelected()
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type, id == null)
        key?.let { builder.setOverrideForType(TrackSelectionOverride(it.group, listOf(it.index))) }
        player.trackSelectionParameters = builder.build()
    }
    companion object { const val PROVIDER_PREFIX = "viptv-provider:" }
}
