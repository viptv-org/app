package org.viptv.app.preview

import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.GenreChoice
import org.viptv.app.connection.HistoryDisplay
import org.viptv.app.connection.Profile
import org.viptv.app.resources.Res
import org.viptv.engine.wire.*

internal object PreviewData {
    private fun art(name: String) = Res.getUri("files/ui-preview/$name")
    val playerFrame = art("player.jpg")
    val profile = Profile("preview", "vynxc", null, avatarId = "vynxc")
    val profiles = listOf(profile, Profile("zayne", "zayne", null, avatarId = "zayne"),
        Profile("cedes", "cedes", null, locked = true, avatarId = "initial-pink"), Profile("wasim", "[Wasim]", null, avatarId = "initial-blue"))
    val hero = Card(identity = MediaIdentity("hero", "movie", emptyList()), provider_id = "preview", title = "The End of Oak Street",
        subtitle = "2026 · Action, Adventure · 1h 40m",
        description = "The Platt family bands together to navigate their new surroundings after a cosmic event transports their suburban neighborhood to someplace unknown.",
        landscape = art("landscape.jpg"), logo = art("logo.png"), badges = listOf("4K", "HDR", "5.1", "PG-13"))
    val watching = listOf("Monster: The Jeffrey Dahmer Story", "Re:Zero – Starting Life in Another World", "Obsession", "Naruto Shippūden: The Movie")
        .mapIndexed { index, title -> Card(identity = MediaIdentity("watch-$index", if (index < 2) "series" else "movie", emptyList()),
            provider_id = "preview", title = title, subtitle = if (index == 0) "S1 E2 · 38m left" else "1h 40m left",
            landscape = art("history${index + 1}.jpg"), progress = 0.3, badges = emptyList()) }
    val posters = listOf("The Whisper Man", "Weapons", "The Ministry of Ungentlemanly Warfare", "Those Who Wish Me Dead", "The Invite", "Project Hail Mary", "Mayday", "Pressure").mapIndexed { index, title ->
        Card(identity = MediaIdentity("poster-$index", "movie", emptyList()), provider_id = "preview", title = title, subtitle = "Movie",
            poster = art("poster${index + 1}.jpg"), badges = emptyList())
    }
    val home = HomeSnapshot(scope = Scope("preview", "preview", "preview", 1), revision = 1, hero = hero,
        rows = listOf(HomeRow(id = "continue", title = "Continue Watching", provider_id = "preview", state = RowState.READY, cards = watching),
            HomeRow(id = "new", title = "New this week", provider_id = "preview", state = RowState.READY, cards = posters)), complete = true)
    val genres = listOf("Action", "Comedy", "Drama", "Horror", "Science Fiction", "Thriller")
        .map { GenreChoice(it, BrowseQuery("movie", genre = it)) }
    val playerTitle = watching.first().copy(subtitle = "S1 E2 · Please Don’t Go")
    val detail = DetailSnapshot(item = hero.copy(subtitle = "Movie · " + hero.subtitle), episodes = emptyList())
    val sources = listOf(SourceCandidate("source-1", "preview", "LordStreams", "WEB-DL · HEVC · 5.1 · 18.4 GB"),
        SourceCandidate("source-2", "preview", "Provider 2", "BluRay · HEVC · 5.1 · 22.1 GB"))
    val history = watching.mapIndexed { index, card ->
        HistoryDisplay(HistoryEntry(card, 1, if (index == 1) 1 else null), if (index < 2) "Today" else "Yesterday")
    }
}
