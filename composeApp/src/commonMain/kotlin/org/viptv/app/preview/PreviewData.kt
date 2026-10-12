package org.viptv.app.preview

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
    val hero = Card(MediaIdentity("hero", "movie", emptyList()), "preview", "The End of Oak Street",
        "2026 · Action, Adventure · 1h 40m",
        "The Platt family bands together to navigate their new surroundings after a cosmic event transports their suburban neighborhood to someplace unknown.",
        landscape = art("landscape.jpg"), logo = art("logo.png"), badges = listOf("4K", "HDR", "5.1", "PG-13"))
    val watching = listOf("Monster: The Jeffrey Dahmer Story", "Re:Zero – Starting Life in Another World", "Obsession", "Naruto Shippūden: The Movie")
        .mapIndexed { index, title -> Card(MediaIdentity("watch-$index", if (index < 2) "series" else "movie", emptyList()),
            "preview", title, if (index == 0) "S1 E2 · 38m left" else "1h 40m left",
            landscape = art("history${index + 1}.jpg"), progress = 0.3, badges = emptyList()) }
    val posters = listOf("The Whisper Man", "Weapons", "The Ministry of Ungentlemanly Warfare", "Those Who Wish Me Dead", "The Invite", "Project Hail Mary", "Mayday", "Pressure").mapIndexed { index, title ->
        Card(MediaIdentity("poster-$index", "movie", emptyList()), "preview", title, "Movie",
            poster = art("poster${index + 1}.jpg"), badges = emptyList())
    }
    val home = HomeSnapshot(Scope("preview", "preview", "preview", 1), 1, hero,
        listOf(HomeRow("continue", "Continue Watching", "preview", RowState.READY, watching),
            HomeRow("new", "New this week", "preview", RowState.READY, posters)), true)
    val playerTitle = watching.first().copy(subtitle = "S1 E2 · Please Don’t Go")
    val detail = DetailSnapshot(hero.copy(subtitle = "Movie · " + hero.subtitle), emptyList())
    val sources = listOf(SourceCandidate("source-1", "preview", "LordStreams", "WEB-DL · HEVC · 5.1 · 18.4 GB"),
        SourceCandidate("source-2", "preview", "Provider 2", "BluRay · HEVC · 5.1 · 22.1 GB"))
    val history = watching.mapIndexed { index, card ->
        HistoryDisplay(HistoryEntry(card, 1, if (index == 1) 1 else null), if (index < 2) "Today" else "Yesterday")
    }
}
