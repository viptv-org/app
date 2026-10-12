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
import org.viptv.app.features.entry.ProfilesScreen
import org.viptv.app.features.entry.ProfilePinScreen
import org.viptv.app.features.history.HistoryScreen
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.player.PlayerFact
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.features.search.SearchScreen
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.state.Screen
import org.viptv.app.theme.*

/** Explicit visual-review entry point. It never connects to an account or modifies a database. */
@Composable
fun UiPreview(initialScreen: Screen = Screen.Home, television: Boolean = false, browser: Boolean = false,
    desktop: Boolean = false, screen: Screen? = null, navigate: ((Screen) -> Unit)? = null, searchTerm: String? = null,
    search: ((String) -> Unit)? = null, handheld: Boolean = false) {
    org.viptv.app.images.ConfigureImageLoading()
    var selected by remember { mutableStateOf(initialScreen) }
    var term by remember { mutableStateOf("the w") }
    val current = screen ?: selected
    val go: (Screen) -> Unit = navigate ?: { selected = it }
    val query: (String) -> Unit = search ?: { term = it }
    ViptvTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            val phone = maxWidth < 640.dp && !television && !desktop && !browser
            val tablet = !phone && !television && !desktop && !browser
            Column(Modifier.fillMaxSize()) {
                if (current !in listOf(Screen.Profiles, Screen.ProfilePin, Screen.Player) && (browser || (television && current != Screen.Home))) TopNavigation(current, television, go, PreviewData.profile)
                else if (phone && current !in listOf(Screen.Search, Screen.Details, Screen.Sources, Screen.Settings, Screen.History, Screen.Profiles, Screen.ProfilePin, Screen.Player))
                    PhoneHeader(current, PreviewData.profile, go)
                Row(Modifier.weight(1f)) {
                    if (current !in listOf(Screen.Profiles, Screen.ProfilePin, Screen.Player)) {
                        if (tablet) TabletNavigation(current, PreviewData.profile, go) else if (desktop) SideNavigation(current, go)
                    }
                    Box(Modifier.weight(1f)) {
                        when (current) {
                            Screen.Player -> PlayerScreen(null, true, null, {}, {}, { go(Screen.Home) }, PreviewData.playerTitle, PlayerFact.Progress(842000, 3132000), phone = handheld, previewFrame = PreviewData.playerFrame)
                            Screen.Details -> DetailsScreen(PreviewData.detail, false, null, { go(Screen.Home) }, { go(Screen.Sources) })
                            Screen.Sources -> SourcesScreen(PreviewData.hero, PreviewData.sources, false, null, {},
                                { go(Screen.Details) }, PreviewData.detail, television)
                            Screen.Library -> LibraryScreen(PreviewData.posters, true, television, { go(Screen.Details) }, { go(Screen.History) }, { go(Screen.Home) })
                            Screen.History -> HistoryScreen(PreviewData.history, false, null, { go(Screen.Details) }, {}, { go(Screen.Home) })
                            Screen.Search -> SearchScreen(searchTerm ?: term, PreviewData.posters, emptyList(), false, null, desktop, query, { go(Screen.Details) })
                            Screen.Settings -> SettingsScreen(true, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {},
                                { go(Screen.Profiles) }, {}, PreviewData.profile, { go(Screen.Home) })
                            Screen.ProfilePin -> ProfilePinScreen(PreviewData.profiles[2], phone, false, null, {}, { go(Screen.Profiles) }, television)
                            Screen.Profiles -> ProfilesScreen(PreviewData.profiles, false, null, true, false, phone, desktop,
                                { go(Screen.Home) }, {}, {}, {}, {}, television)
                            Screen.Movies, Screen.Series, Screen.Discover -> BrowseScreen(current, PreviewData.home, television) { go(Screen.Details) }
                            else -> HomeScreen(PreviewData.home, television, { go(Screen.Details) }, {}, desktop, { go(Screen.Sources) }, tablet, go, browser)
                        }
                    }
                }
            }
            if (television && current == Screen.Home) TopNavigation(current, true, go, PreviewData.profile)
            if (phone && current !in listOf(Screen.Search, Screen.Details, Screen.Sources, Screen.Settings, Screen.Profiles, Screen.ProfilePin, Screen.Player))
                Box(Modifier.align(Alignment.BottomCenter)) { BottomNavigation(current, go) }
        }
    }
}
