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
    val longLibrary = LibrarySnapshot(Scope("preview", "preview", "preview", 1), 1, org.viptv.app.connection.defaultLibraryQuery(),
        (0 until 48).map { index -> posters[index % posters.size].let { it.copy(identity = it.identity.copy(id = "saved-$index")) } }, complete = true,
        controls = listOf(QueryControl("type", "Type", ControlKind.SINGLECHOICE, true,
            listOf(Choice("all", "All"), Choice("movie", "Movies"), Choice("series", "Series"), Choice("anime", "Anime"))),
            QueryControl("genre", "Genre", ControlKind.SINGLECHOICE, false, genres.map { Choice(it.label, it.label) })),
        availability = Availability.AVAILABLE)
    val playerTitle = watching.first().copy(identity = watching.first().identity.copy(episode = EpisodeCoordinates("aired", 1, 2)),
        subtitle = "S1 E2 · Please Don’t Go")
    val playerNextEpisode = playerTitle.copy(identity = playerTitle.identity.copy(id = "player-next", episode = EpisodeCoordinates("aired", 1, 3)),
        title = "The Long Way Home", subtitle = "S1 E3 · The Long Way Home")
    val playerEpisodes = listOf(playerTitle, playerNextEpisode)
    val detail = DetailSnapshot(item = hero.copy(subtitle = "Movie · " + hero.subtitle), episodes = emptyList())
    val sources = listOf(
        SourceCandidate("source-1", "preview", "Torrentio\n4k HDR", "Oak Street 2026 2160p BluRay\n👤 199 💾 18.4 GB ⚙️ Site",
            quality_height = 2160, release_type = "bluray", dynamic_range = listOf("hdr"),
            presentation = SourcePresentation(provider_name = "Torrentio")),
        SourceCandidate("source-2", "preview", "Comet\n1080p", "Oak.Street.2026.1080p.WEB-DL.DDP5.1.H.264-GRP\n👤 88 💾 6.2 GB ⚙️ Site",
            quality_height = 1080, release_type = "web_dl", audio_flags = listOf("multi"),
            presentation = SourcePresentation(provider_name = "Comet")))
    val filterSources = listOf(2160L to "Torrentio", 1080L to "Torrentio", 1080L to "Comet", 720L to "MediaFusion", 2160L to "Comet",
        1080L to "Orion", 480L to "Torrentio", 1080L to "MediaFusion", 2160L to "Torrentio", 720L to "Comet", 360L to "Orion", 1080L to "Torrentio")
        .mapIndexed { index, (height, addon) ->
            SourceCandidate("filter-${index + 1}", "addon:${addon.lowercase()}", "$addon\n${if (height == 2160L) "4k" else "${height}p"}",
                "Oak Street 2026 ${height}p WEB-DL\n👤 ${40 + index} 💾 ${(4 + index * 1.7).toInt()} GB ⚙️ Site", quality_height = height,
                release_type = "web_dl", dynamic_range = if (height == 2160L) listOf("hdr") else emptyList(),
                presentation = SourcePresentation(provider_name = addon, source_id = addon.lowercase()))
        }
    val chipSources = listOf(1080L to "Torrentio 4K RD+ | ElfHosted", 720L to "MediaFusion | ElfHosted Debrid", 1080L to "MediaFusion | ElfHosted Debrid")
        .mapIndexed { index, (height, addon) ->
            SourceCandidate("chip-${index + 1}", "addon:${addon.substringBefore(" ").lowercase()}", "[TB+] ${addon.substringBefore(" ")}",
                "Oak Street 2026 ${height}p WEB-DL\n👤 ${20 + index} 💾 ${(1 + index * 2.5).toInt()} GB ⚙️ Site", quality_height = height,
                release_type = "web_dl", audio_flags = listOf("dubbed"), delivery = "direct",
                presentation = SourcePresentation(provider_name = addon, source_id = addon.substringBefore(" ").lowercase()))
        }
    val history = watching.mapIndexed { index, card ->
        HistoryDisplay(HistoryEntry(card, 1, if (index == 1) 1 else null), if (index < 2) "Today" else "Yesterday")
    }
}
