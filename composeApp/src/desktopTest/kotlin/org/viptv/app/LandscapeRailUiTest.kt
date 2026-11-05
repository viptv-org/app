package org.viptv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.App
import org.viptv.app.components.BottomNavigation
import org.viptv.app.components.HomeGenrePicker
import org.viptv.app.components.LocalHeaderPills
import org.viptv.app.components.LocalHomeGenres
import org.viptv.app.components.PhoneHeader
import org.viptv.app.components.TabletMinSide
import org.viptv.app.components.TabletNavigation
import org.viptv.app.components.phoneDestinations
import org.viptv.app.components.phoneShell
import org.viptv.app.components.phoneTabDestinations
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.GenreChoice
import org.viptv.app.connection.Profile
import org.viptv.app.features.home.PhoneHero
import org.viptv.app.features.home.TabletHero
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import java.io.File
import kotlin.test.*
import org.junit.Rule

@OptIn(ExperimentalComposeUiApi::class, DelicateCoilApi::class)
class LandscapeRailUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val profile = Profile("qa", "QA profile", null, avatarId = "initial-blue")
    private val genres = HomeGenrePicker(listOf("Action", "Comedy").map { GenreChoice(it, BrowseQuery("movie", genre = it)) }) {}

    @Test fun aPhoneInEitherOrientationIsStillAPhone() {
        assertTrue(phoneShell(390.dp, 844.dp, television = false, touch = true), "portrait phone")
        assertTrue(phoneShell(844.dp, 390.dp, television = false, touch = true), "landscape phone")
        assertFalse(phoneShell(TabletMinSide, 960.dp, television = false, touch = true), "portrait tablet")
        assertFalse(phoneShell(1280.dp, 800.dp, television = false, touch = true), "landscape tablet")
        assertFalse(phoneShell(844.dp, 390.dp, television = true, touch = true), "TV")
        assertFalse(phoneShell(844.dp, 390.dp, television = false, touch = false), "pointer window")
    }

    @Test fun theRailCarriesEveryPhoneDestinationAndItsBottomEntriesInLandscape() {
        val scene = landscapeShell()
        try {
            frames(scene)
            assertEquals(phoneDestinations.map { it.label }.toSet(), names(scene), "every phone destination has a rail entry")
            for (name in phoneDestinations.map { it.label } + listOf("Settings", "Switch profile")) {
                assertTrue(onScreen(scene, 390) { named(it, name) }.isNotEmpty(), "$name is drawn inside the window")
            }
            assertEquals(emptyList(), offScreen(scene, 390) { named(it, "Settings") || named(it, "Switch profile") }, "the bottom entries never clip")
        } finally { scene.close() }
    }

    @Test fun theRailStillReachesEveryEntryWhenItIsTooShortToFitThem() {
        val scene = scene(844, 260) {
            CompositionLocalProvider(LocalHomeGenres provides genres) {
                Column { PhoneHeader(Screen.Home, profile) {}; Row(Modifier.weight(1f)) { TabletNavigation(Screen.Home, profile) {} } }
            }
        }
        try {
            frames(scene)
            scrollRail(scene)
            assertEquals(phoneDestinations.map { it.label }.toSet(), names(scene), "every destination stays reachable")
            assertTrue(onScreen(scene, 260) { text(it) == "Search" || description(it) == "Search" }.isNotEmpty(), "Search scrolls into view")
        } finally { scene.close() }
    }

    @Test fun theBottomBarAndTheRailReadTheSameDestinations() {
        val bar = scene(390, 844) { BottomNavigation(Screen.Home, {}) }
        val rail = scene(844, 390) { TabletNavigation(Screen.Home, profile) {} }
        try {
            frames(bar); frames(rail)
            val fromBar = labels(bar) + nodes(bar).map(::description).filter { it == "Search" }
            assertEquals(phoneDestinations.map { it.label }.toSet(), fromBar.toSet(), "the bar offers every destination")
            assertEquals(fromBar.toSet(), names(rail), "the rail offers the same destinations")
            assertEquals(phoneTabDestinations.map { it.label }, labels(bar).filter { it != "Search" }, "the search button is not a bar tab")
        } finally { bar.close(); rail.close() }
    }

    @Test fun landscapeKeepsTheBrowsePillsAndTheProfileEntryOnce() {
        val card = Card(MediaIdentity("tt1", "movie", emptyList()), provider_id = "simctl", title = "Fixture film", badges = emptyList())
        val scene = scene(844, 390) {
            CompositionLocalProvider(LocalHomeGenres provides genres, LocalHeaderPills provides true) {
                Column {
                    PhoneHeader(Screen.Home, profile) {}
                    Row(Modifier.weight(1f)) { TabletNavigation(Screen.Home, profile) {}; TabletHero(card, {}, {}, {}, {}, false) }
                }
            }
        }
        try {
            frames(scene)
            for (pill in listOf("All", "Movies", "Series", "Anime", "Genres")) assertEquals(1, nodes(scene).count { text(it) == pill }, "$pill is drawn once")
            assertEquals(1, nodes(scene).count { description(it) == "Profile menu" }, "the header keeps the profile entry")
        } finally { scene.close() }
    }

    @Test fun capturesTheLandscapeShellAndTheTabletRailForThePr() {
        val card = Card(MediaIdentity("tt1", "movie", emptyList()), provider_id = "simctl", title = "Fixture film", badges = emptyList())
        val landscape = landscapeShell(card)
        try { frames(landscape); capture(landscape, "landscape-844x390") } finally { landscape.close() }
        val portrait = scene(390, 844) {
            CompositionLocalProvider(LocalHomeGenres provides genres) {
                Column {
                    PhoneHeader(Screen.Home, profile) {}
                    PhoneHero(card, {}, {}, false)
                    Spacer(Modifier.weight(1f))
                    BottomNavigation(Screen.Home, {})
                }
            }
        }
        try { frames(portrait); capture(portrait, "portrait-390x844") } finally { portrait.close() }
        val tablet = scene(1280, 900) {
            CompositionLocalProvider(LocalHomeGenres provides genres) {
                Row { TabletNavigation(Screen.Home, profile) {}; TabletHero(card, {}, {}, {}, {}, false) }
            }
        }
        try { frames(tablet); capture(tablet, "tablet-1280x900") } finally { tablet.close() }
    }

    @Test fun theAppInLandscapeKeepsThePillsAndEveryDestinationAndSwapsTheBarForTheRail() {
        val state = ApplicationState(screen = Screen.Home, selectedProfile = profile.id, profiles = listOf(profile),
            localMode = true, home = PreviewData.home)
        AppShell(844, 390, state).use { shell ->
            shell.frames()
            for (pill in listOf("All", "Movies", "Series", "Anime")) {
                assertTrue(shell.nodes().any { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab && text(it) == pill && it.boundsInRoot.top < 90f },
                    "$pill pill stays in landscape: ${shell.nodes().filter { node -> node.config.getOrNull(SemanticsProperties.Role) == Role.Tab }.map(::text)}")
            }
            assertEquals(phoneDestinations.map { it.label }.toSet(), shell.entryNames(), "the rail carries every destination")
            assertTrue(shell.nodes().any { description(it) == "Settings" }, "Settings is reachable")
            assertTrue(shell.nodes().any { description(it) == "Switch profile" }, "the profile entry is reachable")
            assertFalse(shell.nodes().any { it.config.getOrNull(SemanticsProperties.Role) == Role.Button && description(it) == "Search" },
                "a landscape phone draws the rail and its icon Search entry, not the bottom bar's round button")
            capture(shell.scene, "app-landscape-844x390")
        }
    }

    @Test fun theAppInPortraitStillDrawsTheBar() {
        val state = ApplicationState(screen = Screen.Home, selectedProfile = profile.id, profiles = listOf(profile),
            localMode = true, home = PreviewData.home)
        AppShell(390, 844, state).use { shell ->
            shell.frames()
            val search = shell.nodes().filter { description(it) == "Search" && it.config.contains(SemanticsActions.OnClick) }.maxByOrNull { it.boundsInRoot.top }
            assertNotNull(search, "the portrait bar keeps the search button")
            assertTrue(search.boundsInRoot.top > 700f, "the search button sits in the bottom bar: ${search.boundsInRoot}")
            assertTrue(shell.nodes().none { description(it) == "Switch profile" }, "a portrait phone draws the bar, not the rail")
            capture(shell.scene, "app-portrait-390x844")
        }
    }

    private fun landscapeShell(card: Card? = null) = scene(844, 390) {
        CompositionLocalProvider(LocalHomeGenres provides genres, LocalHeaderPills provides true) {
            Column {
                PhoneHeader(Screen.Home, profile) {}
                Row(Modifier.weight(1f)) {
                    TabletNavigation(Screen.Home, profile) {}
                    if (card != null) TabletHero(card, {}, {}, {}, {}, false)
                }
            }
        }
    }

    private inner class AppShell(width: Int, height: Int, private val state: ApplicationState) : AutoCloseable {
        private val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        private val controller = ApplicationController(scope, store)
        private var previousLoader: ImageLoader? = null
        private var appLoader: ImageLoader? = null
        private var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) {
            val context = LocalPlatformContext.current
            remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
            App(providedController = controller, manageControllerLifecycle = false)
            appLoader = SingletonImageLoader.get(context)
        }

        fun frames(count: Int = 20) {
            controller.mutable.value = state
            repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        }

        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun entryNames() = nodes().filter { it.boundsInRoot.left < 92f }.map { text(it).ifEmpty { description(it) } }
            .filter { it in phoneDestinations.map { destination -> destination.label } }.toSet()

        override fun close() {
            scene.close(); scope.cancel()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    private var frame = 0L
    private fun scene(width: Int, height: Int, content: @Composable () -> Unit): ImageComposeScene {
        frame = 0
        return ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }
    }
    private fun capture(scene: ImageComposeScene, name: String) = System.getenv("VIPTV_LANDSCAPE_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs()
        scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    }
    private fun frames(scene: ImageComposeScene, count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun scrollRail(scene: ImageComposeScene) {
        val rail = nodes(scene).filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) && it.boundsInRoot.left < 92f }
            .maxByOrNull { it.boundsInRoot.height } ?: return
        rail.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 1_000f)
        frames(scene, 40)
    }
    private fun onScreen(scene: ImageComposeScene, height: Int, match: (SemanticsNode) -> Boolean) =
        nodes(scene).filter(match).filter { it.boundsInRoot.top >= 0f && it.boundsInRoot.bottom <= height }
    private fun offScreen(scene: ImageComposeScene, height: Int, match: (SemanticsNode) -> Boolean) =
        nodes(scene).filter(match).filter { it.boundsInRoot.top < 0f || it.boundsInRoot.bottom > height }
    private fun labels(scene: ImageComposeScene) = nodes(scene).map(::text).filter { it.isNotEmpty() }
    private fun names(scene: ImageComposeScene) = nodes(scene).map { text(it).ifEmpty { description(it) } }
        .filter { it in phoneDestinations.map { destination -> destination.label } }.toSet()
    private fun named(node: SemanticsNode, name: String) = text(node) == name || description(node) == name
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().firstOrNull().orEmpty()
}
