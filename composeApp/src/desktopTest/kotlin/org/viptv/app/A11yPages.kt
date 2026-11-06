package org.viptv.app

import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.FeatureAvailability
import org.viptv.app.connection.Profile
import org.viptv.app.features.calendar.CalendarDay
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.ContinueWatchingList
import org.viptv.app.state.Screen
import org.viptv.app.state.SettingsPage
import org.viptv.engine.wire.Availability
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.Choice
import org.viptv.engine.wire.ControlKind
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.engine.wire.LiveChannel
import org.viptv.engine.wire.LivePage
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.QueryControl

internal val a11yProfiles = listOf(Profile("1", "Ana", null))

internal val a11yLockedProfile = Profile("1", "Ana", null, locked = true, lockKind = "profile")

private val a11yDays = (1..7).map { index ->
    CalendarDay("day-$index", "W", "$index", "Day $index", "Day $index")
}

private fun a11yControls() = DiscoveryControls("preview", listOf("movie"), listOf(
    QueryControl("sort", "Sort", ControlKind.SINGLECHOICE, true, listOf(Choice("popular", "Popular"), Choice("new", "New"))),
    QueryControl("genre", "Genre", ControlKind.SINGLECHOICE, false, PreviewData.genres.map { Choice(it.label, it.label) })),
    discovery = FeatureAvailability("Available"), search = FeatureAvailability("Available"),
    filters = FeatureAvailability("Available"), calendar = FeatureAvailability("Available"))

private val a11yLiveChannel = LiveChannel("one", "m3u:1",
    Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1", title = "Fixture news", badges = emptyList()))

private val a11yBase = ApplicationState(selectedProfile = "1", profiles = a11yProfiles, localMode = true)

internal val a11yPages: Map<String, ApplicationState> = mapOf(
    "Home" to a11yBase.copy(screen = Screen.Home, home = PreviewData.home),
    "Discover" to a11yBase.copy(screen = Screen.Discover, browseQuery = BrowseQuery("movie"),
        browseCards = PreviewData.posters, browseControls = a11yControls()),
    "Movies" to a11yBase.copy(screen = Screen.Movies, browseQuery = BrowseQuery("movie"),
        browseCards = PreviewData.posters, browseControls = a11yControls()),
    "Series" to a11yBase.copy(screen = Screen.Series, browseQuery = BrowseQuery("series"),
        browseCards = PreviewData.posters, browseControls = a11yControls()),
    "Anime" to a11yBase.copy(screen = Screen.Anime, browseQuery = BrowseQuery("anime"),
        browseCards = PreviewData.posters, browseControls = a11yControls()),
    "Search" to a11yBase.copy(screen = Screen.Search, searchTerm = "monster", results = PreviewData.posters),
    "Calendar" to a11yBase.copy(screen = Screen.Calendar, calendarDays = a11yDays, calendarAvailable = true),
    "Details" to a11yBase.copy(screen = Screen.Details, detail = PreviewData.detail),
    "Sources" to a11yBase.copy(screen = Screen.Sources, detail = PreviewData.detail, sourceTitle = PreviewData.hero,
        sources = PreviewData.sources, detailReturnScreen = Screen.Home, sourceReturnScreen = Screen.Details),
    "Library" to a11yBase.copy(screen = Screen.Library, library = PreviewData.posters),
    "Live" to a11yBase.copy(screen = Screen.Live, livePage = LivePage(channels = listOf(a11yLiveChannel), groups = emptyList(),
        connections = listOf(IntegrationConnection("m3u:1", "m3u", "7", name = "Playlist", enabled = true, revision = 1,
            capabilities = emptyList())), complete = true, availability = Availability.AVAILABLE)),
    "History" to a11yBase.copy(screen = Screen.History, history = PreviewData.history),
    "Continue watching" to a11yBase.copy(screen = Screen.ContinueWatching,
        continueWatching = ContinueWatchingList("1", PreviewData.home.rows.first().cards, next = null)),
    "Settings" to a11yBase.copy(screen = Screen.Settings),
    "Settings, add-ons" to a11yBase.copy(screen = Screen.Settings, settingsPage = SettingsPage("Add-ons", subpage = true),
        addons = listOf(IntegrationConnection("addon:1", "stremio", "1", name = "Torrentio", enabled = true, revision = 1,
            capabilities = emptyList()))),
)
