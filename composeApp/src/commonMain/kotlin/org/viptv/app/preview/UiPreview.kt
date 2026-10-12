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
import org.viptv.app.features.entry.ProfilePinScreen
import org.viptv.app.features.history.HistoryScreen
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.home.TvHomeScreen
import org.viptv.app.features.browse.TvBrowseScreen
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.player.PlayerFact
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.features.live.LiveScreen
import org.viptv.app.features.calendar.CalendarScreen
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
    val current = screen ?: selected
    val go: (Screen) -> Unit = navigate ?: { selected = it }
    val query: (String) -> Unit = search ?: { term = it }
    ViptvTheme(television) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            val phone = maxWidth < 640.dp && !television && !desktop && !browser
            val tablet = !phone && !television && !desktop && !browser
            Column(Modifier.fillMaxSize()) {
                if (current !in listOf(Screen.Connect, Screen.Profiles, Screen.ProfilePin, Screen.Player) && (browser || (television && current !in listOf(Screen.Home, Screen.Details, Screen.Sources, Screen.Settings)))) TopNavigation(current, television, go, PreviewData.profile)
                else if (phone && current !in listOf(Screen.Connect, Screen.Search, Screen.Details, Screen.Sources, Screen.Settings, Screen.History, Screen.Profiles, Screen.ProfilePin, Screen.Player))
                    PhoneHeader(current, PreviewData.profile, go)
                Row(Modifier.weight(1f)) {
                    if (current !in listOf(Screen.Connect, Screen.Profiles, Screen.ProfilePin, Screen.Player)) {
                        if (tablet) TabletNavigation(current, PreviewData.profile, go) else if (desktop) SideNavigation(current, go)
                    }
                    Box(Modifier.weight(1f)) {
                        when (current) {
                            Screen.Connect -> ConnectScreen(browser, "https://viptv.syek.tech", false, null, { go(Screen.Profiles) }, { _, _, _ -> }, desktop, phone, television, entryBackChanged)
                            Screen.Player -> if (television && liveTitle == null) PreviewTvPlayer(tvDetailVariant, go, entryBackChanged)
                                else PlayerScreen(null, true, null, {}, {}, { go(if (liveTitle == null) Screen.Home else Screen.Live) }, liveTitle ?: PreviewData.playerTitle, if (liveTitle == null) PlayerFact.Progress(842000, 3132000) else PlayerFact.Progress(0, null), phone = handheld, previewFrame = liveTitle?.landscape ?: PreviewData.playerFrame, television = television, backChanged = entryBackChanged)
                            Screen.Details -> if (television) TvDetailsScreen(
                                if (tvDetailVariant == "Series") PreviewTvDetails.series else PreviewTvDetails.movie,
                                false, null, { go(Screen.Sources) },
                                if (tvDetailVariant == "Series") PreviewTvDetails.seriesExtras else PreviewTvDetails.movieExtras)
                                else DetailsScreen(PreviewData.detail, false, null, { go(Screen.Home) }, { go(Screen.Sources) })
                            Screen.Sources -> if (television) TvSourcesScreen(PreviewTvDetails.movie.item,
                                if (tvDetailVariant == "Loading") PreviewTvSources.sources.take(2) else PreviewTvSources.sources,
                                false, null, { go(Screen.Player) }, { go(Screen.Details) }, PreviewTvDetails.movie, PreviewTvSources.info,
                                PreviewTvDetails.movieExtras, loadingMore = tvDetailVariant == "Loading")
                                else SourcesScreen(PreviewData.hero, PreviewData.sources, false, null, {}, { go(Screen.Details) }, PreviewData.detail)
                            Screen.Calendar -> CalendarScreen(PreviewCalendar.days, PreviewCalendar.entries, television, { go(Screen.Details) }, { go(Screen.Settings) })
                            Screen.Live -> LiveScreen(PreviewLive.channels, television, PreviewLive.start, PreviewLive.now, { channel -> liveTitle = channel.card.copy(title = channel.current(PreviewLive.now)?.title ?: channel.card.title, subtitle = channel.card.title); go(Screen.Player) }, { go(Screen.Settings) })
                            Screen.Library -> LibraryScreen(PreviewData.posters, true, television, { go(Screen.Details) }, { go(Screen.History) }, { go(Screen.Home) })
                            Screen.History -> HistoryScreen(PreviewData.history, false, null, { go(Screen.Details) }, {}, { go(Screen.Home) })
                            Screen.Search -> if (television) TvSearchScreen(searchTerm ?: term, PreviewData.posters + PreviewLive.channels.take(3).map { it.card }, emptyList(), false, null, query, { go(Screen.Details) }, listOf("the whisper man", "the wild robot", "the white lotus"))
                                else SearchScreen(searchTerm ?: term, PreviewData.posters, emptyList(), false, null, desktop, query, { go(Screen.Details) })
                            Screen.Settings -> if (television) PreviewTvSettings(tvDetailVariant, go) else SettingsScreen(true, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {},
                                { go(Screen.Profiles) }, {}, PreviewData.profile, { go(Screen.Home) })
                            Screen.ProfilePin -> ProfilePinScreen(PreviewData.profiles[2], phone, false, null, {}, { go(Screen.Profiles) }, television)
                            Screen.Profiles -> ProfilesScreen(PreviewData.profiles, false, null, true, false, phone, desktop,
                                { go(Screen.Home) }, {}, {}, {}, {}, television)
                            Screen.Movies, Screen.Series, Screen.Discover -> if (television) TvBrowseScreen(PreviewTvHome.movies, false, { go(Screen.Details) }, PreviewTvHome.genres, PreviewTvHome.membership, 4)
                                else BrowseScreen(current, PreviewData.home, television) { go(Screen.Details) }
                            else -> if (television) TvHomeScreen(if (tvDetailVariant == "Loading") null else PreviewTvHome.home, { go(Screen.Details) }, { go(Screen.Sources) }, {}, go,
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
