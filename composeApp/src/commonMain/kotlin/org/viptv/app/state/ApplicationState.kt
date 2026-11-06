package org.viptv.app.state

import org.viptv.app.components.NextEpisodes
import org.viptv.app.connection.*
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.AddonFailure
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.engine.wire.LiveGuide
import org.viptv.engine.wire.LivePage
import org.viptv.engine.wire.LiveQuery
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.connection.HistoryDisplay
import org.viptv.app.crash.CrashReport

enum class Screen { Connect, RecoveryCodes, Profiles, ProfilePin, ProfileEdit, Home, Discover, Movies, Series, Anime, Calendar, Search, Details, Sources, Player, Live, Library, History, Settings,
    ContinueWatching }
data class ApplicationState(
    val starting: Boolean = false,
    val startupRetryable: Boolean = false,
    val recoveryKit: RecoveryKit? = null,
    val screen: Screen = Screen.Connect,
    val navBack: List<BackEntry> = emptyList(),
    val navForward: List<BackEntry> = emptyList(),
    val settingsEntrySection: String? = null,
    val settingsPage: SettingsPage? = null,
    val settingsReturnsToPlayer: Boolean = false,
    val busy: Boolean = false,
    val mutations: PendingMutations = PendingMutations(),
    val savedOverrides: Map<String, Boolean> = emptyMap(),
    val error: String? = null,
    val errorCode: String? = null,
    val updateRequired: Boolean = false,
    val backendUnreachableSince: Long? = null,
    val backendRetrySeconds: Int = 0,
    val backendBannerDismissed: Boolean = false,
    val profiles: List<Profile> = emptyList(),
    val profileToOpen: String? = null,
    val profileToEdit: String? = null,
    val simkl: SimklLinkStatus? = null,
    val accountDevices: AccountDevices? = null,
    val devicesRetryable: Boolean = true,
    val accountIdentity: AccountIdentity? = null,
    val passwordChangeNotice: String? = null,
    val manageAccount: Boolean = false,
    val accountOwner: Boolean = false,
    val accountRole: String = "member",
    val accountAccess: AccountAccess? = null,
    val profileManagement: ProfileManagement? = null,
    val parentSettings: ProfileManagement? = null,
    val parentControls: Map<String, ParentProfileControls> = emptyMap(),
    val parentTitles: ParentTitlesState? = null,
    val vodMatches: VodMatchesState? = null,
    val parentSettingsNotice: String? = null,
    val parentSettingsSaved: Long = 0,
    val profileApprovals: Int? = null,
    val profilePageSaved: Long = 0,
    val linkedServicesProfile: String? = null,
    val policyRefreshNeeded: Boolean = false,
    val managingProfiles: Boolean = false,
    val profilesFromHome: Boolean = false,
    val selectedProfile: String? = null,
    val leavePinFrom: String? = null,
    val home: HomeSnapshot? = null,
    val summaryCards: Map<String, Card> = emptyMap(),
    val homeRowBusy: Set<String> = emptySet(),
    val homeRowErrors: Map<String, String> = emptyMap(),
    val continueWatching: ContinueWatchingList? = null,
    val continueWatchingPageError: String? = null,
    val continueUndo: ContinueUndo? = null,
    val continueNext: NextEpisodes = NextEpisodes.Empty,
    val results: List<Card> = emptyList(),
    val searchRows: List<SearchResultRow> = emptyList(),
    val library: List<Card> = emptyList(),
    val favoriteKeys: Set<Pair<String, String>>? = null,
    val libraryMembership: org.viptv.engine.wire.LibraryMembershipSnapshot? = null,
    val libraryQuery: org.viptv.engine.wire.LibraryQuery = defaultLibraryQuery(),
    val libraryPage: org.viptv.engine.wire.LibrarySnapshot? = null,
    val libraryPageError: String? = null,
    val browseQuery: BrowseQuery = BrowseQuery("movie"),
    val browseControls: DiscoveryControls? = null,
    val browseCards: List<Card> = emptyList(),
    val browseNext: String? = null,
    val browseFailures: List<AddonFailure> = emptyList(),
    val browsePageError: String? = null,
    val homeGenres: HomeGenres? = null,
    val livePage: LivePage? = null,
    val livePageError: String? = null,
    val liveQuery: LiveQuery = LiveQuery(offset = 0),
    val liveGuides: Map<String, LiveGuide> = emptyMap(),
    val liveGuideChannel: String? = null,
    val liveGuideLoading: Boolean = false,
    val liveFavorites: Set<String> = emptySet(),
    val liveFavoritesAvailable: Boolean = false,
    val liveFavoritesCanRetry: Boolean = false,
    val liveFavoriteError: String? = null,
    val liveFavoritesOnly: Boolean = false,
    val liveSetupReturnFocus: Boolean = false,
    val calendarType: String = org.viptv.app.features.calendar.CALENDAR_DEFAULT_TYPE,
    val calendarWeek: Int = 0,
    val calendarAvailable: Boolean = true,
    val calendarDays: List<org.viptv.app.features.calendar.CalendarDay> = emptyList(),
    val calendarEntries: List<org.viptv.app.features.calendar.CalendarEntry> = emptyList(),
    val calendarSources: Set<org.viptv.app.features.calendar.CalendarSource>? = null,
    val calendarEpisode: Card? = null,
    val searchTerm: String = "",
    val searchFilters: Set<String> = emptySet(),
    val searchFocusResults: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val trendingSearches: List<String> = emptyList(),
    val pendingDetailSeason: Long? = null,
    val detailReturnScreen: Screen = Screen.Home,
    val playTapTitle: Card? = null,
    val detailFailure: String? = null,
    val detailRetryable: Boolean = true,
    val detail: DetailSnapshot? = null,
    val detailFailures: List<AddonFailure> = emptyList(),
    val detailShell: Boolean = false,
    val detailFocus: DetailFocus? = null,
    val detailSourcePreview: DetailSourcePreview? = null,
    val detailLoadingOffset: Long? = null,
    val detailPageError: String? = null,
    val detailPageRetryOffset: Long? = null,
    val detailHistory: List<DetailSnapshot> = emptyList(),
    val localMode: Boolean = false,
    val sessionNotice: String? = null,
    val devicePairing: DevicePairingDisplay? = null,
    val plugins: List<org.viptv.engine.wire.PluginDescriptor> = emptyList(),
    val browsingConfiguration: BrowsingConfiguration? = null,
    val addons: List<IntegrationConnection> = emptyList(),
    val addonLogos: Map<String, String> = emptyMap(),
    val preferences: PreferencesSnapshot? = null,
    val preferencesError: String? = null,
    val sourceIntegrations: SourceIntegrationSnapshot = SourceIntegrationSnapshot(),
    val sourceIntegrationError: String? = null,
    val xtreamConnections: List<XtreamConnection>? = null,
    val xtreamDefaultLive: Long? = null,
    val xtreamNotice: String? = null,
    val gateways: GatewaySnapshot? = null,
    val gatewaysError: String? = null,
    val gatewayChecks: Map<String, GatewayCheck> = emptyMap(),
    val gatewayGrants: Map<String, List<Long>> = emptyMap(),
    val enabledAddonIds: Set<String> = emptySet(),
    val sourceTitle: Card? = null,
    val sourceReturnScreen: Screen = Screen.Home,
    val sources: List<SourceCandidate> = emptyList(),
    val sourceProducers: List<org.viptv.app.connection.SourceProducerOutcome> = emptyList(),
    val sourceGroups: org.viptv.app.connection.SourceGroups = org.viptv.app.connection.SourceGroups(),
    val torrentOnlySources: Int = 0,
    val findingSources: Boolean = false,
    val playingSource: SourceCandidate? = null,
    val openingSource: SourceCandidate? = null,
    val pendingAction: String? = null,
    val bestMatch: BestMatchSession? = null,
    val fallbackNotice: BestMatchFallback? = null,
    val audioNotice: String? = null,
    val openingStage: String? = null,
    val playback: PlaybackInput? = null,
    val playbackSegments: List<org.viptv.app.features.player.TvSkipRange> = emptyList(),
    val nextEpisode: Card? = null,
    val nextEpisodeStatus: org.viptv.engine.wire.ContinuationStatus? = null,
    val nextEpisodeLoading: Boolean = false,
    val nextEpisodePreparation: NextEpisodePreparation? = null,
    val playbackSeason: List<Card> = emptyList(),
    val paused: Boolean = false,
    val presentation: PlayerFact.Presentation? = null,
    val history: List<HistoryDisplay> = emptyList(),
    val pendingCrashReport: CrashReport? = null,
)

internal val ApplicationState.animeAvailable: Boolean
    get() = browsingConfiguration?.profile?.browsing_plugin?.let { it == "simkl" } ?: true

// A device grant cannot manage the account, so the account-only pages stay closed to it.
internal val ApplicationState.accountSession: Boolean
    get() = accountRole != "device"

internal fun ApplicationState.detailsPending(): Boolean =
    detail == null && error == null && detailFailure == null && playTapTitle != null

internal fun ApplicationState.isSaved(card: Card?): Boolean {
    card ?: return false
    savedOverrides[cardLookupKey(card)]?.let { return it }
    libraryMembership?.let { return it.containsCard(card) }
    // Only an exact saved lookup may match a card with a metadata origin; older hosts send no membership.
    if (card.metadata_origin != null) return savedCardsContain(library, card)
    val identity = card.parent_identity ?: card.identity
    return favoriteKeys?.contains(identity.kind to identity.id) ?: savedCardsContain(library, card)
}
