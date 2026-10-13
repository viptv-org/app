package org.viptv.app.preview

import org.viptv.app.resources.Res
import org.viptv.engine.wire.*

internal object PreviewTvHome {
    val watching = PreviewData.watching.mapIndexed { index, card -> card.copy(
        subtitle = when(index) { 0 -> "S1 E2 · 38m left"; 1 -> "Up next · S4 E16"; 2 -> "1h 40m left"; else -> "1h 12m left" },
        progress = when (index) { 0 -> .30; 2 -> .03; 3 -> .18; else -> null },
        badges = if (index == 1) listOf("New episode") else emptyList()) } +
        Card(MediaIdentity("tv-continue-grey", "movie", emptyList()), "preview", "In the Grey", "36m left",
            landscape = Res.getUri("files/ui-preview/tv-continue-grey.jpg"), progress = .64, badges = emptyList())
    val movies = listOf("Project Hail Mary", "Mayday", "Weapons", "The Whisper Man", "The Invite", "The End of Oak Street", "Pressure",
        "One Night Only", "Those Who Wish Me Dead", "Backrooms", "Practical Magic", "The Ministry of Ungentlemanly Warfare", "Tony", "Obsession")
        .mapIndexed { index, title -> Card(MediaIdentity("tv-movie-$index", "movie", emptyList()), "preview", title,
            poster = Res.getUri("files/ui-preview/tv-movie-$index.jpg"), badges = if (index in listOf(0, 2)) listOf("New") else emptyList()) }
    private val newThisWeek = listOf(0, 1, 2, 3, 4, 6, 8, 9).mapIndexed { index, movie ->
        movies[movie].copy(badges = if (index < 3) listOf("New") else emptyList())
    }
    val home = PreviewData.home.copy(hero = PreviewData.hero.copy(description =
        "The Platt family bands together to navigate their new surroundings after a cosmic event transports their neighborhood to someplace unknown."),
        rows = listOf(HomeRow("continue", "Continue Watching", "preview", RowState.READY, watching),
        HomeRow("new", "New this week", "preview", RowState.READY, newThisWeek)))
    val summaries = mapOf(watching[0].identity.id to PreviewTvDetails.series.item.copy(subtitle = "Series · 2022 · Crime, Drama",
        description = "This true-crime anthology series delivers a multi-layered psychological study of individuals whose extreme actions shocked society.",
        progress = 0.3, badges = listOf("TV-MA", "HD", "5.1")),
        watching[1].identity.id to watching[1].copy(description = "A new chapter in the story of Subaru and his companions."))
    val genres = listOf("Action", "Adventure", "Comedy", "Drama", "Horror", "Sci-fi", "Thriller", "Documentary")
    val membership = movies.associate { it.identity.id to setOf(if (it.title in listOf("Mayday", "Pressure", "The Ministry of Ungentlemanly Warfare")) "Action" else "Drama") }
}
