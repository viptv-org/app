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
    /** Server-shaped My List long enough to scroll: the `Library/Long` preview of the collapsing header (app#58). */
    val longLibrary = LibrarySnapshot(Scope("preview", "preview", "preview", 1), 1, org.viptv.app.connection.defaultLibraryQuery(),
        (0 until 48).map { index -> posters[index % posters.size].let { it.copy(identity = it.identity.copy(id = "saved-$index")) } }, complete = true,
        controls = listOf(QueryControl("type", "Type", ControlKind.SINGLECHOICE, true,
            listOf(Choice("all", "All"), Choice("movie", "Movies"), Choice("series", "Series"), Choice("anime", "Anime"))),
            QueryControl("genre", "Genre", ControlKind.SINGLECHOICE, false, genres.map { Choice(it.label, it.label) })),
        availability = Availability.AVAILABLE)
    val playerTitle = watching.first().copy(subtitle = "S1 E2 · Please Don’t Go")
    val detail = DetailSnapshot(item = hero.copy(subtitle = "Movie · " + hero.subtitle), episodes = emptyList())
    val sources = listOf(SourceCandidate("source-1", "preview", "LordStreams", "WEB-DL · HEVC · 5.1 · 18.4 GB"),
        SourceCandidate("source-2", "preview", "Provider 2", "BluRay · HEVC · 5.1 · 22.1 GB"))
    /** Five qualities from four add-ons: the web picker's Quality and Source dropdowns (app#59). */
    val filterSources = listOf(2160L to "Torrentio", 1080L to "Torrentio", 1080L to "Comet", 720L to "MediaFusion", 2160L to "Comet",
        1080L to "Orion", 480L to "Torrentio", 1080L to "MediaFusion", 2160L to "Torrentio", 720L to "Comet", 360L to "Orion", 1080L to "Torrentio")
        .mapIndexed { index, (height, addon) ->
            SourceCandidate("filter-${index + 1}", "addon:${addon.lowercase()}", "$addon ${if (height == 2160L) "4K" else "${height}p"} #${index + 1}",
                "Oak.Street.2026.${height}p.WEB-DL.DDP5.1.H.265 · ${(4 + index * 1.7).toInt()} GB", quality_height = height,
                presentation = SourcePresentation(provider_name = addon, source_id = addon.lowercase()))
        }
    /** Three options per filter with long add-on names: chip rows that overflow a narrow window (app#59). */
    val chipSources = listOf(1080L to "Torrentio 4K RD+ | ElfHosted", 720L to "MediaFusion | ElfHosted Debrid", 1080L to "MediaFusion | ElfHosted Debrid")
        .mapIndexed { index, (height, addon) ->
            SourceCandidate("chip-${index + 1}", "addon:chip${addon.length}", "${addon.substringBefore(" ")} ${height}p #${index + 1}",
                "Oak.Street.2026.${height}p.WEB-DL.DDP5.1.H.265", quality_height = height,
                presentation = SourcePresentation(provider_name = addon, source_id = addon.substringBefore(" ").lowercase()))
        }
    val history = watching.mapIndexed { index, card ->
        HistoryDisplay(HistoryEntry(card, 1, if (index == 1) 1 else null), if (index < 2) "Today" else "Yesterday")
    }
}
