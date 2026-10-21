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

enum class Screen { Connect, RecoveryCodes, Profiles, ProfilePin, ProfileEdit, Home, Discover, Movies, Series, Anime, Calendar, Search, Details, Sources, Player, Live, Library, History, Settings }
data class ApplicationState(
    val starting: Boolean = false,
    val recoveryKit: RecoveryKit? = null,
    val screen: Screen = Screen.Connect,
    /** Screen-level browser history for the desktop chrome's back/forward. */
    val navBack: List<Screen> = emptyList(),
    val navForward: List<Screen> = emptyList(),
    val settingsEntrySection: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val profiles: List<Profile> = emptyList(),
    val profileToOpen: String? = null,
    val profileToEdit: String? = null,
    val simkl: SimklLinkStatus? = null,
    val accountDevices: AccountDevices? = null,
    val accountIdentity: AccountIdentity? = null,
    val profileManagement: ProfileManagement? = null,
    val parentSettings: ProfileManagement? = null,
    val parentTitles: ParentTitlesState? = null,
    val parentSettingsNotice: String? = null,
    val parentSettingsSaved: Long = 0,
    val policyRefreshNeeded: Boolean = false,
    val managingProfiles: Boolean = false,
    val selectedProfile: String? = null,
    val home: HomeSnapshot? = null,
    /** Detail-enriched display fields for the TV billboard, keyed by identity id.
     *  Feed rows (Continue Watching) can arrive without a logo or description;
     *  the billboard fills them in once per title instead of showing a sparse summary. */
    val summaryCards: Map<String, Card> = emptyMap(),
    /** Home-row paging: a busy row appends skeletons, a failed row keeps the cards it shows. */
    val homeRowBusy: Set<String> = emptySet(),
    val homeRowErrors: Map<String, String> = emptyMap(),
    val results: List<Card> = emptyList(),
    val searchRows: List<SearchResultRow> = emptyList(),
    val library: List<Card> = emptyList(),
    val favoriteKeys: Set<Pair<String, String>>? = null,
    val libraryMembership: org.viptv.engine.wire.LibraryMembershipSnapshot? = null,
    val libraryQuery: org.viptv.engine.wire.LibraryQuery = defaultLibraryQuery(),
    val libraryPage: org.viptv.engine.wire.LibrarySnapshot? = null,
    val browseQuery: BrowseQuery = BrowseQuery("movie"),
    val browseControls: DiscoveryControls? = null,
    val browseCards: List<Card> = emptyList(),
    val browseNext: String? = null,
    val livePage: LiveCatalogPage? = null,
    val liveQuery: LiveQuery = LiveQuery(),
    val liveGuides: Map<String, LiveGuideSnapshot> = emptyMap(),
    val liveGuideChannel: String? = null,
    val liveGuideLoading: Boolean = false,
    val liveFavorites: Set<String> = emptySet(),
    val liveFavoritesAvailable: Boolean = false,
    val liveFavoritesCanRetry: Boolean = false,
    val liveFavoriteError: String? = null,
    val liveFavoritePending: Set<String> = emptySet(),
    val liveFavoritesOnly: Boolean = false,
    val calendarType: String = "series",
    val calendarWeek: Int = 0,
    val calendarAvailable: Boolean = true,
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
    val torrentOnlySources: Int = 0,
    val findingSources: Boolean = false,
    val playingSource: SourceCandidate? = null,
    /** Measured startup stage of a source this device acquires itself ("Finding peers…"), else null. */
    val openingStage: String? = null,
    val playback: PlaybackInput? = null,
    val nextEpisode: Card? = null,
    val nextEpisodeStatus: org.viptv.engine.wire.ContinuationStatus? = null,
    val nextEpisodeLoading: Boolean = false,
    val paused: Boolean = false,
    val presentation: PlayerFact.Presentation? = null,
    val history: List<HistoryDisplay> = emptyList(),
)

internal fun ApplicationState.isSaved(card: Card?): Boolean {
    card ?: return false
    libraryMembership?.let { return it.containsCard(card) }
    // Compatibility with old hosts/fakes: origin-bearing cards may only match
    // the exact saved lookup when no authoritative membership exists.
    if (card.metadata_origin != null) return savedCardsContain(library, card)
    val identity = card.parent_identity ?: card.identity
    return favoriteKeys?.contains(identity.kind to identity.id) ?: savedCardsContain(library, card)
}
