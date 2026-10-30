package org.viptv.app.preview

import org.viptv.app.features.player.*
import org.viptv.app.player.*
import org.viptv.app.resources.Res

internal object PreviewTvPlayback {
    val frame = Res.getUri("files/ui-preview/tv3title-3.jpg")
    val skipFrame = Res.getUri("files/ui-preview/tv3title-2.jpg")
    val tracks = PlaybackTracks(listOf(
        PlaybackTrack(1, "English · 5.1", true), PlaybackTrack(2, "English · Audio description", false),
        PlaybackTrack(3, "Spanish", false), PlaybackTrack(4, "French", false), PlaybackTrack(5, "German", false)),
        listOf(PlaybackTrack(10, "English", true), PlaybackTrack(11, "English (SDH)", false),
            PlaybackTrack(12, "Spanish", false), PlaybackTrack(13, "Portuguese", false)))
    val extras = TvPlaybackExtras("LordStreams", "4K", "[10:24 PM]", "Monster · S1 E2 · LordStreams 4K",
        listOf(TvSkipRange(0, 90000)), PreviewTvDetails.episodes[2].copy(badges = listOf("TV-MA", "HD")),
        PreviewTvDetails.episodes, listOf(TvScrubThumbnail(0, 3132000, Res.getUri("files/ui-preview/tv3title-4.jpg"))),
        "[Provider 2] 4K", "LordStreams 4K didn’t respond. The next best source picks up at 14:02.",
        creditsLabel = "Monster · S1 E2 · Credits")
}
