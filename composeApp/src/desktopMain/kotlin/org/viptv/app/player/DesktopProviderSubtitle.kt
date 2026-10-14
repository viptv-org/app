package org.viptv.app.player

import io.github.yuroyami.kiteplayer.KitePlayer
import io.github.yuroyami.kiteplayer.SubtitleSource
import io.github.yuroyami.kiteplayer.TrackId
import kotlinx.coroutines.*
import org.viptv.engine.wire.SubtitleCandidate
import java.io.File

internal suspend fun attachProviderSubtitle(player: KitePlayer, input: PlaybackInput, candidate: SubtitleCandidate, directory: File): TrackId {
    val content = shiftSubtitleBytes(requireNotNull(input.subtitleContent)(candidate.id), input.timelineOffsetMillis)
    var file: File? = null
    try {
        withContext(Dispatchers.IO) {
            val header = content.take(128).toByteArray().decodeToString().trimStart('\uFEFF', ' ', '\r', '\n')
            val extension = when { header.startsWith("WEBVTT") -> ".vtt"; header.startsWith("[Script Info]") -> ".ass"; else -> ".srt" }
            file = File.createTempFile("viptv-subtitle-", extension, directory)
            requireNotNull(file).writeBytes(content)
        }
        // This SDK always selects added files. Caller cancellation cancels its acquisition;
        // the surface joins cancelled selection work before applying a newer Off/choice.
        return player.addExternalSubtitle(SubtitleSource(requireNotNull(file).absolutePath,
            title = candidate.label, language = candidate.language))
    } finally {
        withContext(NonCancellable + Dispatchers.IO) { file?.delete() }
    }
}
