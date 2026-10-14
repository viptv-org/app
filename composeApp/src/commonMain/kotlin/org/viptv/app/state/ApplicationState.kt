package org.viptv.app.state

import org.viptv.app.connection.*
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.connection.HistoryDisplay

enum class Screen { Connect, Profiles, ProfilePin, ProfileEdit, Home, Discover, Movies, Series, Calendar, Search, Details, Sources, Player, Live, Library, History, Settings }
data class ApplicationState(
    val starting: Boolean = false,
    val screen: Screen = Screen.Connect,
    val busy: Boolean = false,
    val error: String? = null,
    val profiles: List<Profile> = emptyList(),
    val profileToOpen: String? = null,
    val profileToEdit: String? = null,
    val profileNotice: String? = null,
    val simkl: SimklLinkStatus? = null,
    val profileManagement: ProfileManagement? = null,
    val managingProfiles: Boolean = false,
    val selectedProfile: String? = null,
    val home: HomeSnapshot? = null,
    val results: List<Card> = emptyList(),
    val library: List<Card> = emptyList(),
    val favoriteKeys: Set<Pair<String, String>>? = null,
    val libraryQuery: org.viptv.engine.wire.LibraryQuery = defaultLibraryQuery(),
    val libraryPage: org.viptv.engine.wire.LibrarySnapshot? = null,
    val browseQuery: BrowseQuery = BrowseQuery("movie"),
    val browseControls: DiscoveryControls? = null,
    val browseCards: List<Card> = emptyList(),
    val browseNext: String? = null,
    val livePage: LiveCatalogPage? = null,
    val liveQuery: LiveQuery = LiveQuery(),
    val liveGuides: Map<String, LiveGuideSnapshot> = emptyMap(),
    val calendarDays: List<org.viptv.app.features.calendar.CalendarDay> = emptyList(),
    val calendarEntries: List<org.viptv.app.features.calendar.CalendarEntry> = emptyList(),
    val searchTerm: String = "",
    val detailReturnScreen: Screen = Screen.Home,
    val detail: DetailSnapshot? = null,
    val detailLoadingOffset: Long? = null,
    val detailPageError: String? = null,
    val detailHistory: List<DetailSnapshot> = emptyList(),
    val localMode: Boolean = false,
    val sessionNotice: String? = null,
    val devicePairing: DevicePairingDisplay? = null,
    val plugins: List<org.viptv.engine.wire.PluginDescriptor> = emptyList(),
    val browsingConfiguration: BrowsingConfiguration? = null,
    val addons: List<IntegrationConnection> = emptyList(),
    val preferences: PreferencesSnapshot? = null,
    val preferencesError: String? = null,
    val sourceIntegrations: SourceIntegrationSnapshot = SourceIntegrationSnapshot(),
    val sourceIntegrationError: String? = null,
    val enabledAddonIds: Set<String> = emptySet(),
    val sourceTitle: Card? = null,
    val sourceReturnScreen: Screen = Screen.Home,
    val sources: List<SourceCandidate> = emptyList(),
    val findingSources: Boolean = false,
    val playingSource: SourceCandidate? = null,
    val playback: PlaybackInput? = null,
    val paused: Boolean = false,
    val presentation: PlayerFact.Presentation? = null,
    val history: List<HistoryDisplay> = emptyList(),
)

internal fun ApplicationState.isSaved(card: Card?): Boolean {
    val identity = card?.parent_identity ?: card?.identity ?: return false
    return favoriteKeys?.contains(identity.kind to identity.id)
        ?: library.any { it.identity.kind == identity.kind && it.identity.id == identity.id }
}
