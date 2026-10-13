package org.viptv.app.player

import kotlinx.browser.document
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.w3c.dom.*

internal suspend fun selectBrowserSubtitle(input: PlaybackInput, video: HTMLVideoElement, id: Int?,
    tracks: MutableMap<Int, HTMLTrackElement>, blobs: MutableList<String>, failed: () -> Unit): String? {
    val candidate = input.providerSubtitle(id)
    if (id != null) requireNotNull(candidate)
    var chosen = id?.let(tracks::get)
    if (candidate != null && chosen == null) {
        val content = requireNotNull(input.subtitleContent)(candidate.id)
        currentCoroutineContext().ensureActive()
        val uri = subtitleBlob(content.decodeToString()).also { blobs.add(it) }
        chosen = (document.createElement("track") as HTMLTrackElement).apply {
            kind = "subtitles"; label = candidate.label; srclang = candidate.language; src = uri
            onload = { placeProviderCues(this, input.timelineOffsetMillis / 1000.0); null }
            onerror = { _, _, _, _, _ -> failed(); null }
            video.appendChild(this)
        }
        tracks[requireNotNull(id)] = chosen
    }
    tracks.values.forEach { it.track.mode = if (it == chosen) TextTrackMode.SHOWING else TextTrackMode.DISABLED }
    return candidate?.id
}
