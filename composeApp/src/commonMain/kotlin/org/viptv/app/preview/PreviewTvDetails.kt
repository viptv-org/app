package org.viptv.app.preview

import org.viptv.app.features.details.TvDetailExtras
import org.viptv.app.resources.Res
import org.viptv.engine.wire.*

internal object PreviewTvDetails {
    private fun art(name: String) = Res.getUri("files/ui-preview/$name")
    val movie = PreviewData.detail.copy(item = PreviewData.detail.item.copy(landscape = art("tv3movie-0.jpg"), logo = art("tv3movie-1.png")))
    val recommendations = listOf("Obsession", "The Whisper Man", "Camp Miasma", "Backrooms", "Tony", "Pressure", "Weapons", "Mayday")
        .mapIndexed { index, title -> Card(identity = MediaIdentity("tv-related-$index", "movie", emptyList()), provider_id = "preview", title = title,
            poster = art("tv3movie-${index + 2}.jpg"), badges = emptyList()) }
    private val titles = listOf("Episode One", "Please Don’t Go", "Doin’ A Dahmer", "The Good Boy Box")
    private val descriptions = listOf(
        "After throwing his neighbor off the stench coming from his apartment, Jeff heads to a local bar, where a…",
        "A young Jeff contends with troubles at home and school. Years later, his strange behavior evolves into…",
        "Left to live alone after his parents’ turbulent divorce, high school senior Jeff breaks routine when he invite…",
        "From high school to the Army, Jeff struggles to find stability. While living in his grandmother’s house, Jeff…")
    val episodes = titles.mapIndexed { index, title -> Card(
        identity = MediaIdentity("tv-episode-$index", "series", emptyList(), EpisodeCoordinates("aired", 1, (index + 1).toLong())),
        provider_id = "preview", title = title, subtitle = if (index == 0) "52m · Watched" else if (index == 1) "52m · 38m left" else if (index == 2) "52m" else "50m",
        description = descriptions[index], landscape = art("tv3title-${index + 2}.jpg"), progress = if (index == 1) 0.27 else null, badges = if (index == 0) listOf("Watched") else emptyList()) }
    val series = DetailSnapshot(item = Card(identity = MediaIdentity("tv-series", "series", emptyList()), provider_id = "preview", title = "Monster: The Jeffrey Dahmer Story",
        subtitle = "Series · 2022 · Biography, Crime, Drama · IMDb 7.7",
        description = "This true-crime anthology series delivers a multi-layered psychological study of individuals whose extreme actions shocked society. The series follows the victims’ families and the systemic failures that allowed the crimes to continue for years.",
        landscape = art("tv3title-0.jpg"), logo = art("tv3title-1.png"), badges = listOf("TV-MA", "HD", "5.1", "CC")), episodes = episodes)
    val movieExtras = TvDetailExtras(recommendations = recommendations)
    val seriesExtras = TvDetailExtras("Evan Peters, Richard Jenkins, Niecy Nash", episodes[1], "Resume S1 E2", "38m left", recommendations)
}
