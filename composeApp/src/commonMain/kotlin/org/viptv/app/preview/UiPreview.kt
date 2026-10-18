package org.viptv.app.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.components.*
import org.viptv.app.features.browse.BrowseScreen
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.details.TvDetailsScreen
import org.viptv.app.features.entry.ProfilesScreen
import org.viptv.app.features.entry.ConnectScreen
import org.viptv.app.features.entry.TvSignIn
import org.viptv.app.features.entry.TvSignInStep
import org.viptv.app.features.entry.TvEmailCodeChallenge
import org.viptv.app.features.entry.TvSplash
import org.viptv.app.features.entry.TvPairingSession
import org.viptv.app.features.entry.TvSignedIn
import org.viptv.app.features.entry.TvSignedInIdentity
import org.viptv.app.features.entry.ProfilePinScreen
import org.viptv.app.features.history.HistoryScreen
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.home.TvHomeScreen
import org.viptv.app.features.browse.TvBrowseScreen
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.player.PlayerFact
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.features.library.TvLibraryScreen
import org.viptv.app.features.live.LiveScreen
import org.viptv.app.features.live.TvLiveScreen
import org.viptv.app.features.calendar.CalendarScreen
import org.viptv.app.features.calendar.TvCalendarScreen
import org.viptv.app.features.search.SearchScreen
import org.viptv.app.features.search.TvSearchScreen
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.features.sources.TvSourcesScreen
import org.viptv.app.state.Screen
import org.viptv.app.theme.*

/** Explicit visual-review entry point. It never connects to an account or modifies a database. */
@Composable
fun UiPreview(initialScreen: Screen = Screen.Home, television: Boolean = false, browser: Boolean = false,
    desktop: Boolean = false, screen: Screen? = null, navigate: ((Screen) -> Unit)? = null, searchTerm: String? = null,
    search: ((String) -> Unit)? = null, handheld: Boolean = false, tvDetailVariant: String = "Movie", entryBackChanged: ((() -> Unit)?) -> Unit = {}) {
    org.viptv.app.images.ConfigureImageLoading()
    var selected by remember { mutableStateOf(initialScreen) }
    var liveTitle by remember { mutableStateOf<org.viptv.engine.wire.Card?>(null) }
    var term by remember { mutableStateOf("the w") }
    var managingProfiles by remember { mutableStateOf(false) }
    var profileToEdit by remember { mutableStateOf(PreviewData.profile.id) }
    val current = screen ?: selected
    val go: (Screen) -> Unit = navigate ?: { selected = it }
    val query: (String) -> Unit = search ?: { term = it }
    val editQuery: ((String) -> String) -> Unit = { edit -> term = edit(term); search?.invoke(term) }
    val watchLive: (org.viptv.app.features.live.LiveChannel) -> Unit = { channel ->
        liveTitle = channel.card.copy(title = channel.current(PreviewLive.now)?.title ?: channel.card.title, subtitle = channel.card.title)
        go(Screen.Player)
    }
    var homeArrived by remember(tvDetailVariant) { mutableStateOf(tvDetailVariant != "LoadingThenReady") }
    LaunchedEffect(tvDetailVariant) {
        if (tvDetailVariant == "LoadingThenReady") { kotlinx.coroutines.delay(3000); homeArrived = true }
    }
    ViptvTheme(television) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            val phone = maxWidth < 640.dp && !television && !desktop && !browser
            val tablet = !phone && !television && !desktop && !browser
            Column(Modifier.fillMaxSize()) {
                if (current !in listOf(Screen.Connect, Screen.Profiles, Screen.ProfilePin, Screen.ProfileEdit, Screen.Player) && ((browser && !(current == Screen.Details && tvDetailVariant == "SharedTitle")) || (television && current !in listOf(Screen.Home, Screen.Details, Screen.Sources, Screen.Settings)))) TopNavigation(current, television, go, PreviewData.profile)
                else if (phone && current !in listOf(Screen.Connect, Screen.Search, Screen.Details, Screen.Sources, Screen.Settings, Screen.History, Screen.Profiles, Screen.ProfilePin, Screen.Player))
                    PhoneHeader(current, PreviewData.profile, go)
                Row(Modifier.weight(1f)) {
                    if (current !in listOf(Screen.Connect, Screen.Profiles, Screen.ProfilePin, Screen.ProfileEdit, Screen.Player)) {
                        if (tablet && current !in listOf(Screen.Details, Screen.Sources)) TabletNavigation(current, PreviewData.profile, go) else if (desktop) SideNavigation(current, go)
                    }
                    Box(Modifier.weight(1f)) {
                        when (current) {
                            Screen.Connect -> if (television && tvDetailVariant == "Splash") TvSplash()
                                else if (television && tvDetailVariant in listOf("SignedIn", "SignedInHold")) TvSignedIn(
                                    TvSignedInIdentity("isolated-preview", "vynxc", "[name@email.com]", PreviewData.profile),
                                    if (tvDetailVariant == "SignedInHold") null else ({ go(Screen.Profiles) }))
                                else if (television) TvSignIn("https://viptv.syek.tech", false, null, { go(Screen.Profiles) },
                                { _, _, _ -> }, entryBackChanged, when (tvDetailVariant) { "Pairing", "PairingUnavailable" -> TvSignInStep.Phone; "Email" -> TvSignInStep.Email; "EmailCode" -> TvSignInStep.EmailCode; else -> TvSignInStep.Welcome },
                                if (tvDetailVariant == "Email") "vynxc" else "",
                                if (tvDetailVariant == "EmailCode") TvEmailCodeChallenge("Sent to [name@email.com] · expires in 15 minutes", "481", "isolated-preview") else null,
                                if (tvDetailVariant == "Pairing") TvPairingSession("https://example.invalid/device?code=AB12CD34EF", "example.invalid/device", "AB12CD34EF", "14:52") else null)
                                else if (!browser && tvDetailVariant in listOf("PairingDevice", "PairingBrowser", "PairingDeviceLoading", "PairingBrowserLoading"))
                                    PreviewNativePairing(desktop, tvDetailVariant) { go(Screen.Profiles) }
                                else ConnectScreen(browser, "https://viptv.syek.tech", false, null, { go(Screen.Profiles) }, { _, _, _ -> }, desktop, phone, false, entryBackChanged)
                            Screen.Player -> if (television && liveTitle == null) PreviewTvPlayer(tvDetailVariant, go, entryBackChanged)
                                else PlayerScreen(null, true, null, {}, {}, { go(if (liveTitle == null) Screen.Home else Screen.Live) }, liveTitle ?: PreviewData.playerTitle, if (liveTitle == null) PlayerFact.Progress(842000, 3132000) else PlayerFact.Progress(0, null), phone = handheld, previewFrame = liveTitle?.landscape ?: PreviewData.playerFrame, television = television, backChanged = entryBackChanged)
                            Screen.Details -> if (television && tvDetailVariant in listOf("SeriesSeasons", "SeriesDelayed")) PreviewTvSeasons(tvDetailVariant, go)
                                else if (television) TvDetailsScreen(
                                if (tvDetailVariant == "Series") PreviewTvDetails.series else PreviewTvDetails.movie,
                                false, null, { go(Screen.Sources) },
                                if (tvDetailVariant == "Series") PreviewTvDetails.seriesExtras else PreviewTvDetails.movieExtras)
                                else PreviewDetails(tvDetailVariant, go, tablet, browser, desktop)
                            Screen.Sources -> if (television) TvSourcesScreen(PreviewTvDetails.movie.item,
                                when (tvDetailVariant) { "Loading" -> PreviewTvSources.sources.take(2); "LoadingEmpty", "Empty" -> emptyList(); else -> PreviewTvSources.sources },
                                tvDetailVariant == "LoadingEmpty", null, { go(Screen.Player) }, { go(Screen.Details) }, PreviewTvDetails.movie, PreviewTvSources.info,
                                PreviewTvDetails.movieExtras, loadingMore = tvDetailVariant in listOf("Loading", "LoadingEmpty"))
                                else SourcesScreen(PreviewData.hero, PreviewData.sources, false, null, {}, { go(Screen.Details) }, PreviewData.detail, tablet = tablet)
                            Screen.Calendar -> if (television) TvCalendarScreen(PreviewTvCalendar.days, PreviewTvCalendar.entries,
                                { go(Screen.Details) }, { go(Screen.Settings) }, PreviewTvCalendar.actions, { go(Screen.Sources) })
                                else CalendarScreen(PreviewCalendar.days, PreviewCalendar.entries, false, { go(Screen.Details) }, { go(Screen.Settings) })
                            Screen.Live -> if (television) PreviewTvGuide(tvDetailVariant, watchLive, { go(Screen.Settings) })
                                else LiveScreen(PreviewLive.channels, false, PreviewLive.start, PreviewLive.now, watchLive, { go(Screen.Settings) })
                            Screen.Library -> if (television) TvLibraryScreen(if (tvDetailVariant in listOf("Empty", "Unavailable")) emptyList() else PreviewTvLibrary.cards,
                                tvDetailVariant != "Unavailable", { go(Screen.Details) }, { go(Screen.Home) }, PreviewTvLibrary.watched)
                                else LibraryScreen(PreviewData.posters, true, television, { go(Screen.Details) }, { go(Screen.History) }, { go(Screen.Home) })
                            Screen.History -> HistoryScreen(PreviewData.history, false, null, { go(Screen.Details) }, {}, { go(Screen.Home) })
                            Screen.Search -> if (television) TvSearchScreen(searchTerm ?: term,
                                if (tvDetailVariant == "Loading") emptyList() else PreviewData.posters + PreviewLive.channels.take(3).map { it.card },
                                emptyList(), tvDetailVariant == "Loading", null, query, { go(Screen.Details) }, editQuery, listOf("the whisper man", "the wild robot", "the white lotus"))
                                else SearchScreen(searchTerm ?: term, PreviewData.posters, emptyList(), false, null, desktop, query, { go(Screen.Details) }, editQuery = editQuery)
                            Screen.Settings -> if (television) PreviewTvSettings(tvDetailVariant, go) else PreviewNonTvSettings(tvDetailVariant, go)
                            Screen.ProfilePin -> if (television) PreviewTvPin(tvDetailVariant, go, entryBackChanged)
                                else ProfilePinScreen(PreviewData.profiles[2], phone, false, null, {}, { go(Screen.Profiles) }, false)
                            Screen.ProfileEdit -> if (television) PreviewTvProfileEdit(go, entryBackChanged, profileToEdit)
                            Screen.Profiles -> ProfilesScreen(if (television && tvDetailVariant == "Empty") emptyList()
                                else if (television && tvDetailVariant != "Locked") PreviewData.profiles.map { it.copy(locked = false) } else PreviewData.profiles,
                                false, null, true, managingProfiles, phone, desktop,
                                { go(Screen.Home) }, { if (television) { profileToEdit = it; go(Screen.ProfileEdit) } },
                                { managingProfiles = true }, { managingProfiles = false }, {}, television)
                            Screen.Movies, Screen.Series, Screen.Anime, Screen.Discover -> if (television) TvBrowseScreen(
                                if (tvDetailVariant in listOf("Loading", "Empty")) emptyList() else PreviewTvHome.movies,
                                tvDetailVariant == "Loading", { go(Screen.Details) }, PreviewTvHome.genres, PreviewTvHome.membership, 4)
                                else BrowseScreen(current, PreviewData.home, television, open = { go(Screen.Details) })
                            else -> if (television) TvHomeScreen(if (tvDetailVariant == "Loading" || !homeArrived) null else PreviewTvHome.home, { go(Screen.Details) }, { go(Screen.Sources) }, {}, go,
                                PreviewData.profile, PreviewTvHome.summaries, listOf(PreviewData.hero) + PreviewData.posters.take(4))
                                else HomeScreen(PreviewData.home, television, { go(Screen.Details) }, {}, desktop, { go(Screen.Sources) }, tablet, go, browser)
                        }
                    }
                }
            }
            if (phone && current !in listOf(Screen.Connect, Screen.Search, Screen.Details, Screen.Sources, Screen.Settings, Screen.Profiles, Screen.ProfilePin, Screen.Player))
                Box(Modifier.align(Alignment.BottomCenter)) { BottomNavigation(current, go) }
        }
    }
}
