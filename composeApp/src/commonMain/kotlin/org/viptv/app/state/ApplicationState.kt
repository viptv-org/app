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
    val simkl: SimklLinkStatus? = null,
    val profileManagement: ProfileManagement? = null,
    val managingProfiles: Boolean = false,
    val selectedProfile: String? = null,
    val home: HomeSnapshot? = null,
    val results: List<Card> = emptyList(),
    val library: List<Card> = emptyList(),
    val browseQuery: BrowseQuery = BrowseQuery("movie"),
    val browseControls: DiscoveryControls? = null,
    val browseCards: List<Card> = emptyList(),
    val browseNext: String? = null,
    val calendarDays: List<org.viptv.app.features.calendar.CalendarDay> = emptyList(),
    val calendarEntries: List<org.viptv.app.features.calendar.CalendarEntry> = emptyList(),
    val searchTerm: String = "",
    val detail: DetailSnapshot? = null,
    val localMode: Boolean = false,
    val sessionNotice: String? = null,
    val addons: List<IntegrationConnection> = emptyList(),
    val sourceIntegrations: SourceIntegrationSnapshot = SourceIntegrationSnapshot(),
    val sourceIntegrationError: String? = null,
    val enabledAddonIds: Set<String> = emptySet(),
    val sourceTitle: Card? = null,
    val sources: List<SourceCandidate> = emptyList(),
    val findingSources: Boolean = false,
    val playingSource: SourceCandidate? = null,
    val playback: PlaybackInput? = null,
    val paused: Boolean = false,
    val presentation: PlayerFact.Presentation? = null,
    val history: List<HistoryDisplay> = emptyList(),
)
