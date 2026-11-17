package org.viptv.app.preview

import org.viptv.app.features.sources.TvSourceInfo
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation

internal object PreviewTvSources {
    val sources = listOf(
        SourceCandidate("tv-source-1", "addon-a", "Torrentio\n4k HDR", "Oak Street 2026 2160p BluRay\n👤 199 💾 18.4 GB ⚙️ Site",
            quality_height = 2160, release_type = "bluray", dynamic_range = listOf("hdr"), presentation = SourcePresentation(provider_name = "Torrentio")),
        SourceCandidate("tv-source-2", "addon-b", "Comet\n4k DV", "Oak.Street.2026.2160p.DV.HDR.WEB-DL.DDP5.1\n👤 120 💾 22.1 GB ⚙️ Site",
            quality_height = 2160, release_type = "web_dl", dynamic_range = listOf("dv", "hdr"), presentation = SourcePresentation(provider_name = "Comet")),
        SourceCandidate("tv-source-3", "addon-a", "Torrentio\n1080p", "Oak Street 2026 1080p BluRay x264\n👤 62 💾 6.2 GB ⚙️ Site",
            quality_height = 1080, release_type = "bluray", audio_flags = listOf("multi"), presentation = SourcePresentation(provider_name = "Torrentio")),
        SourceCandidate("tv-source-4", "addon-c", "MediaFusion", "Oak Street 2026 1080p WEB-DL\n👤 31 💾 3.9 GB ⚙️ Site",
            quality_height = 1080, release_type = "web_dl", audio_flags = listOf("dubbed"), presentation = SourcePresentation(provider_name = "MediaFusion")),
        SourceCandidate("tv-source-5", "addon-b", "Comet\n720p", "Oak.Street.2026.720p.WEB\n👤 12 💾 1.4 GB ⚙️ Site",
            quality_height = 720, release_type = "web_dl", presentation = SourcePresentation(provider_name = "Comet")))
    val info = mapOf(
        "tv-source-1" to TvSourceInfo("4K"),
        "tv-source-2" to TvSourceInfo("4K"),
        "tv-source-3" to TvSourceInfo("1080p"),
        "tv-source-4" to TvSourceInfo("1080p"),
        "tv-source-5" to TvSourceInfo("720p"))
}
