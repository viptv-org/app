package org.viptv.app.player

import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource

/** A growing transcode playlist still represents an on-demand title.
 *
 * Media3's placeholder source treats an explicit zero as a request for the
 * real window default. HLS normally sets that default near its live edge.
 * Keep the title's available window, period identities and timestamps intact,
 * but expose its default at the start and disable live speed correction.
 */
@UnstableApi
internal class TitleHlsMediaSource(source: MediaSource) : WrappingMediaSource(source) {
    override fun getInitialTimeline(): Timeline? = mediaSource.initialTimeline?.let(::TitleHlsTimeline)
    override fun onChildSourceInfoRefreshed(newTimeline: Timeline) = refreshSourceInfo(TitleHlsTimeline(newTimeline))
}

@UnstableApi
private class TitleHlsTimeline(timeline: Timeline) : ForwardingTimeline(timeline) {
    override fun getWindow(windowIndex: Int, window: Timeline.Window, defaultPositionProjectionUs: Long): Timeline.Window =
        super.getWindow(windowIndex, window, defaultPositionProjectionUs).apply {
            defaultPositionUs = 0
            liveConfiguration = null
        }
}
