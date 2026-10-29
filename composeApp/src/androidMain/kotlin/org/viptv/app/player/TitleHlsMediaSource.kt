package org.viptv.app.player

import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource

// Media3's placeholder reads an explicit zero as the window default, which HLS sets near its live edge.
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
