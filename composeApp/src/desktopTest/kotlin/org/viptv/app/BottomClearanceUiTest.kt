package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.components.BottomBarHeight
import org.viptv.app.components.BottomNavigation
import org.viptv.app.components.LocalBottomBarFades
import org.viptv.app.components.LocalBottomClearance
import org.viptv.app.components.ScrollEdgeFadeHeight
import org.viptv.app.components.bottomBarClearance
import org.viptv.app.connection.AccountDevice
import org.viptv.app.connection.AccountDevices
import org.viptv.app.connection.AccountIdentity
import org.viptv.app.connection.HistoryDisplay
import org.viptv.app.connection.Profile
import org.viptv.app.features.browse.BrowseScreen
import org.viptv.app.features.calendar.CalendarDay
import org.viptv.app.features.calendar.CalendarEntry
import org.viptv.app.features.calendar.CalendarScreen
import org.viptv.app.features.continuewatching.ContinueWatchingPage
import org.viptv.app.features.continuewatching.ContinueWatchingScreen
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.history.HistoryScreen
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.features.live.LiveChannel
import org.viptv.app.features.live.LiveScreen
import org.viptv.app.features.search.SearchScreen
import org.viptv.app.features.settings.AccountDevicesScreen
import org.viptv.app.features.settings.AccountIdentityScreen
import org.viptv.app.features.settings.AddDeviceScreen
import org.viptv.app.features.settings.AddonSettings
import org.viptv.app.features.settings.ParentControlsSettings
import org.viptv.app.features.settings.PhoneProfilePreferences
import org.viptv.app.features.settings.PhoneSettings
import org.viptv.app.features.settings.ProfilePageActions
import org.viptv.app.features.settings.ProfilePageValues
import org.viptv.app.features.settings.ProfileSettingsPage
import org.viptv.app.features.settings.SettingsInformation
import org.viptv.app.features.settings.SettingsLayout
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.EpisodeSeason
import org.viptv.engine.wire.HistoryEntry
import org.viptv.engine.wire.HomeRow
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.RowState
import org.viptv.engine.wire.Scope
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

// Bottom bar at phone size: the shared clearance must let every page's last content clear the bar's fade.
@OptIn(ExperimentalComposeUiApi::class)
class BottomClearanceUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private class Case(val name: String, val content: @Composable () -> Unit)

    private fun movie(i: Int) = Card(MediaIdentity("clearance-movie-$i", "movie", emptyList()),
        provider_id = "addon:1", title = "Fixture $i", badges = emptyList())

    private fun cases() = buildList<Case> {
        val movies = (1..42).map(::movie)

        add(Case("Home") {
            HomeScreen(HomeSnapshot(Scope("viptv:remote", "account", "1", 1), 1,
                rows = listOf(HomeRow(id = "movies", title = "Movies", provider_id = "addon:1",
                    state = RowState.READY, cards = (1..24).map(::movie))), complete = true),
                false, open = {}, retry = {})
        })
        add(Case("Browse") { BrowseScreen(Screen.Movies, null, false, {}, movies) })
        add(Case("Library") { LibraryScreen(movies, true, false, {}, {}, {}) })
        add(Case("History") {
            HistoryScreen((1..30).map { i -> HistoryDisplay(HistoryEntry(movie(i), i.toLong()), "Today") }, false, null, {}, {})
        })
        add(Case("Search") { SearchScreen("", movies, emptyList(), false, null, false, {}, {}, editQuery = {}) })
        add(Case("Calendar") {
            val day = CalendarDay("today", "Thu", "15", "Today", "Oct 15")
            val entries = (1..30).map { i ->
                val card = Card(MediaIdentity("cal-$i", "series", emptyList()), provider_id = "simkl",
                    title = "Calendar series $i", badges = emptyList())
                CalendarEntry("cal-$i", "today", "4 PM", card, aired = true, season = 1, episode = i.toLong())
            }
            CalendarScreen(listOf(day), entries, false, {}, {})
        })
        add(Case("Live") {
            val channels = (1..30).map { i ->
                LiveChannel(Card(MediaIdentity("live-$i", "live", emptyList()), provider_id = "m3u:1",
                    title = "Channel $i", badges = emptyList()))
            }
            LiveScreen(channels, false, null, null, {}, {})
        })
        add(Case("Continue watching") {
            ContinueWatchingScreen(ContinueWatchingPage(movies, loaded = true), {}, {}, null, {}, {}, {})
        })
        add(Case("Details") {
            val show = Card(MediaIdentity("tt1111111", "series", emptyList()), provider_id = "addon:1",
                title = "Fixture series", badges = emptyList())
            fun episode(season: Long, number: Long) = show.copy(
                identity = MediaIdentity("tt1111111:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
                parent_identity = show.identity, title = "Episode $season-$number")
            DetailsScreen(DetailSnapshot(show, episodes = (1L..28L).map { episode(1, it) },
                next_cursor = "28", seasons = listOf(EpisodeSeason(1, 28, 0))), false, null, {}, {})
        })
        add(Case("Settings") {
            PhoneSettings(null, 0, false, null, profiles = {}, addons = {}, leave = {}, back = {},
                account = {}, devices = {}, stremioImport = {}, preferences = { _ -> }, parental = {},
                information = { _ -> }, addDevice = {}, householdPin = {}, sourceIntegrations = {}, password = {},
                deleteAccount = {}, profilePage = {}, linkedServices = {})
        })
        add(Case("Settings › Account") { AccountIdentityScreen(AccountIdentity("1", "viewer", "Viewer", "owner"), false, null, {}) })
        add(Case("Settings › Profile") {
            ProfileSettingsPage(ProfilePageValues(profile = Profile("1", "Viewer", null), local = false),
                false, null, 0L, ProfilePageActions(), SettingsLayout.Phone)
        })
        add(Case("Settings › Playback") { PhoneProfilePreferences("Playback", null, false, null, {}, {}) })
        add(Case("Settings › Subtitles & audio") { PhoneProfilePreferences("Subtitles & audio", null, false, null, {}, {}) })
        add(Case("Settings › Add-ons") { AddonSettings(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}) })
        add(Case("Settings › Parental controls") {
            ParentControlsSettings(null, emptyMap(), false, null, null, 0L, {}, {}, { _, _ -> }, { _, _, _ -> }, { _ -> })
        })
        add(Case("Settings › Devices") {
            AccountDevicesScreen(AccountDevices(AccountIdentity("1", "viewer", "Viewer", "owner"),
                (1..20).map { AccountDevice("device-$it", "Phone $it", "phone", 1_000_000L + it, it == 1, true) }, true),
                false, null, {}, {}, {}, compact = true)
        })
        add(Case("Settings › Add a device") { AddDeviceScreen(null, true) })
        add(Case("Settings › Help") { SettingsInformation("Help", false, null) })
        add(Case("Settings › About") { SettingsInformation("About", false, null) })
    }

    @Test fun everyPageScrollsItsLastContentClearOfTheBottomBarsFade() {
        val background = Palette.background.toArgb()
        val fadeHeight = (BottomBarHeight.value + ScrollEdgeFadeHeight.value).roundToInt()
        val barTop = HEIGHT - BottomBarHeight.value.roundToInt()
        for (case in cases()) {
            frame = 0
            val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) {
                ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    CompositionLocalProvider(LocalBottomBarFades provides true, LocalBottomClearance provides bottomBarClearance()) {
                        Box(Modifier.fillMaxSize()) {
                            case.content()
                            Box(Modifier.align(Alignment.BottomCenter)) { BottomNavigation(Screen.Home, {}) }
                        }
                    }
                } }
            }
            try {
                frames(scene)
                scrollToEnd(scene)
                frames(scene, 6)
                val page = scroller(nodes(scene))
                val fadeTop = page.boundsInRoot.bottom.roundToInt() - fadeHeight
                scene.render((frame++) * 16_666_667L).use { image ->
                    Bitmap.makeFromImage(image).use { pixels ->
                        for (y in fadeTop until barTop step 2) {
                            for (x in 0 until WIDTH step 2) {
                                val colour = pixels.getColor(x, y)
                                assertTrue(distance(colour, background) <= 2,
                                    "${case.name}: content ${hex(colour)} at ($x,$y) sits above the fade at $fadeTop")
                            }
                        }
                    }
                }
            } finally { scene.close() }
        }
    }

    private fun scroller(all: List<SemanticsNode>) = all.filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
        .maxByOrNull { it.boundsInRoot.height } ?: fail("no vertical scroller")

    private fun scrollToEnd(scene: ImageComposeScene) {
        var before = -1f
        repeat(60) {
            val scroll = scroller(nodes(scene))
            val value = scroll.config[SemanticsProperties.VerticalScrollAxisRange].value()
            if (value <= before + 0.5f) return
            before = value
            scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 4000f)
            frames(scene, 6)
        }
        fail("the page never reached its end")
    }

    private fun distance(a: Int, b: Int) = (0..2).maxOf { abs(((a shr (it * 8)) and 0xFF) - ((b shr (it * 8)) and 0xFF)) }
    private fun hex(color: Int) = "#" + color.toUInt().toString(16).padStart(8, '0')

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)

    private companion object {
        const val WIDTH = 393
        const val HEIGHT = 852
    }
}