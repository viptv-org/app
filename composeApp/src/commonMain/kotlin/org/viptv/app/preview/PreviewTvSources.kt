package org.viptv.app.preview

import org.viptv.app.features.sources.TvSourceInfo
import org.viptv.engine.wire.SourceCandidate

internal object PreviewTvSources {
    val sources = listOf(
        SourceCandidate("tv-source-1", "addon-a", "LordStreams", "WEB-DL · HEVC · 5.1 · 18.4 GB"),
        SourceCandidate("tv-source-2", "addon-b", "[Provider 2]", "BluRay · HEVC · 5.1 · 22.1 GB"),
        SourceCandidate("tv-source-3", "addon-a", "LordStreams", "WEB-DL · H.264 · 5.1 · 6.2 GB"),
        SourceCandidate("tv-source-4", "addon-c", "[Provider 3]", "WEB-DL · H.264 · 2.0 · 3.9 GB"),
        SourceCandidate("tv-source-5", "addon-b", "[Provider 4]", "WEB · H.264 · 2.0 · 1.4 GB"))
    val info = mapOf(
        "tv-source-1" to TvSourceInfo("4K", "HDR10 ·\nDolby Atmos", "[Add-on A]", true),
        "tv-source-2" to TvSourceInfo("4K", "SDR", "[Add-on B]"),
        "tv-source-3" to TvSourceInfo("1080p", addon = "[Add-on A]"),
        "tv-source-4" to TvSourceInfo("1080p", addon = "[Add-on C]"),
        "tv-source-5" to TvSourceInfo("720p", addon = "[Add-on B]"))
}
