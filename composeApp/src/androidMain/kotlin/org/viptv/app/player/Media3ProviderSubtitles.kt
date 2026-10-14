package org.viptv.app.player

import android.content.Context
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import org.viptv.engine.wire.SubtitleCandidate
import java.io.File

@UnstableApi
internal class Media3ProviderSubtitles(private val context: Context, private val player: ExoPlayer,
    private val input: PlaybackInput, private val scope: CoroutineScope, private val tracks: Media3Tracks, private val failed: () -> Unit) {
    private var job: Job? = null
    private var touched = false
    private var desired: String? = null
    private val files = mutableListOf<File>()
    suspend fun select(id: Int?) {
        touched = true; job?.cancelAndJoin(); desired = null
        val candidate = input.providerSubtitle(id)
        if (candidate == null) { tracks.select(PlaybackTrackKind.Subtitle, id); return }
        attach(candidate)
    }
    fun autoSelect(values: List<SubtitleCandidate>) {
        if (touched || desired != null || job?.isActive == true || player.currentTracks.isEmpty || player.currentMediaItem == null) return
        val language = input.preferences.preferred_subtitle_language
        if (player.currentTracks.groups.any { group -> group.type == C.TRACK_TYPE_TEXT &&
                (0 until group.length).any { group.isTrackSelected(it) && group.getTrackFormat(it).language == language } }) return
        preferredProviderSubtitle(input, values)?.let(::attach)
    }
    private fun attach(candidate: SubtitleCandidate) {
        job = scope.launch {
            var created: File? = null
            try {
                val bytes = shiftSubtitleBytes(requireNotNull(input.subtitleContent)(candidate.id), input.timelineOffsetMillis)
                val mime = withContext(Dispatchers.IO) {
                    val header = bytes.take(128).toByteArray().decodeToString().trimStart('\uFEFF', ' ', '\r', '\n')
                    val type = when { header.startsWith("WEBVTT") -> MimeTypes.TEXT_VTT; header.startsWith("[Script Info]") -> MimeTypes.TEXT_SSA; else -> MimeTypes.APPLICATION_SUBRIP }
                    created = File.createTempFile("viptv-media3-", ".subtitle", context.cacheDir).also { it.writeBytes(bytes) }
                    type
                }
                ensureActive()
                val file = requireNotNull(created); files += file; created = null
                val item = requireNotNull(player.currentMediaItem)
                desired = candidate.id
                val subtitle = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.fromFile(file)).setMimeType(mime)
                    .setLanguage(candidate.language).setLabel(candidate.label).setId(Media3Tracks.PROVIDER_PREFIX + candidate.id)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
                val position = player.currentPosition
                player.setMediaItem(item.buildUpon().setSubtitleConfigurations(listOf(subtitle)).build(), position)
                player.prepare()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { desired = null; failed() }
            finally { withContext(NonCancellable + Dispatchers.IO) { created?.delete() } }
        }
    }
    fun applySelection() {
        val id = desired ?: return
        val group = player.currentTracks.groups.firstOrNull { group -> (0 until group.length).any { group.getTrackFormat(it).id == Media3Tracks.PROVIDER_PREFIX + id } } ?: return
        val index = (0 until group.length).first { group.getTrackFormat(it).id == Media3Tracks.PROVIDER_PREFIX + id }
        if (!group.isTrackSelected(index)) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index))).build()
    }
    fun close() { job?.cancel(); files.forEach { it.delete() }; files.clear() }
}
