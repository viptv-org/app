package org.viptv.app.player

import org.w3c.dom.HTMLVideoElement

// HTMLMediaElement.buffered is the media's own clock in seconds and keeps disjoint ranges after a seek.
internal fun browserBufferedRanges(video: HTMLVideoElement): List<BufferedRange> {
    val ranges = video.buffered
    return (0 until ranges.length).mapNotNull { index ->
        val start = ranges.start(index)
        val end = ranges.end(index)
        if (!start.isFinite() || !end.isFinite()) null
        else BufferedRange((start * 1000).toLong(), (end * 1000).toLong()).takeIf { it.endMillis > it.startMillis }
    }
}
