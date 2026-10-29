package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.components.*
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.GenreChoice
import org.viptv.app.connection.Profile
import org.viptv.app.features.home.TabletHero
import org.viptv.app.features.settings.PhoneSettings
import org.viptv.app.state.Screen
import org.viptv.app.theme.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class HomeHeaderControlsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val profile = Profile("qa", "Stremio import QA", null, avatarId = "initial-blue")
    private val genres = listOf("Action", "Comedy", "Science Fiction").map { GenreChoice(it, BrowseQuery("movie", "top", "addon:1", genre = it)) }
    private val removed = listOf("Cast", "Remote control", "Calendar filters")

    @Test fun phoneHeaderHasNoCastRemoteOrCalendarFilterButtons() {
        for (screen in listOf(Screen.Home, Screen.Movies, Screen.Library, Screen.Live, Screen.Calendar)) {
            val scene = scene(390, 844, HomeGenrePicker(genres) {}) { PhoneHeader(screen, profile) {} }
            try {
                frames(scene)
                assertTrue(nodes(scene).none { description(it).any(removed::contains) }, "$screen header: ${nodes(scene).flatMap(::description)}")
                assertTrue(nodes(scene).any { description(it) == listOf("Profile menu") }, "$screen keeps the profile menu")
                assertEquals(screen == Screen.Home, nodes(scene).any { text(it) == "Genres" }, "$screen Genres chip")
            } finally { scene.close() }
        }
    }

    @Test fun genresChipOpensThePickerAndChoosingAGenreHandsBackItsBrowseQuery() {
        val chosen = mutableListOf<GenreChoice>()
        val scene = scene(390, 844, HomeGenrePicker(genres) { chosen += it }) { PhoneHeader(Screen.Home, profile) {} }
        try {
            frames(scene)
            assertFalse(nodes(scene).any { text(it) == "Comedy" })
            click(scene, "Genres")
            assertEquals(genres.map { it.label }, nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) && text(it) in genres.map(GenreChoice::label) }
                .sortedBy { it.boundsInRoot.top }.map(::text))
            click(scene, "Comedy")
            assertEquals(listOf(genres[1]), chosen)
            assertFalse(nodes(scene).any { text(it) == "Science Fiction" }, "choosing a genre closes the picker")
            assertTrue(nodes(scene).none { text(it).contains("not available") })
        } finally { scene.close() }
    }

    @Test fun withoutGenresTheChipIsHidden() {
        for (picker in listOf(null, HomeGenrePicker(emptyList()) {})) {
            val scene = scene(390, 844, picker) { PhoneHeader(Screen.Home, profile) {} }
            try {
                frames(scene)
                assertTrue(nodes(scene).any { text(it) == "All" })
                assertFalse(nodes(scene).any { text(it) == "Genres" })
            } finally { scene.close() }
        }
    }

    @Test fun tabletHeroOffersTheSameGenresAndTheRailHasNoCast() {
        val chosen = mutableListOf<GenreChoice>()
        val card = Card(MediaIdentity("tt1", "movie", emptyList()), provider_id = "stremio", title = "Fixture film", badges = emptyList())
        val scene = scene(1280, 800, HomeGenrePicker(genres) { chosen += it }) {
            Row { TabletNavigation(Screen.Home, profile) {}; TabletHero(card, {}, {}, {}, {}, false) }
        }
        try {
            frames(scene)
            assertTrue(nodes(scene).none { description(it).contains("Cast") })
            click(scene, "Genres"); click(scene, "Action")
            assertEquals(listOf(genres[0]), chosen)
        } finally { scene.close() }
    }

    @Test fun phoneSettingsHidesRowsWithoutAHandlerInsteadOfOpeningADialog() {
        val bare = scene(390, 844, null) { PhoneSettings(profile, 2, false, null, {}, {}, {}, {}) }
        try {
            frames(bare)
            val labels = nodes(bare).map(::text)
            assertTrue(labels.any { it.startsWith("Add-ons") } && "Sign out" in labels, "$labels")
            for (hidden in listOf("Playback", "Subtitles & audio", "Watch on TV", "Account", "Help", "About VIPTV")) assertFalse(labels.any { it.startsWith(hidden) }, hidden)
        } finally { bare.close() }
        val opened = mutableListOf<String>()
        val wired = scene(390, 844, null) {
            PhoneSettings(profile, 2, true, null, {}, {}, {}, {}, account = { opened += "Account" }, preferences = { opened += it }, information = { opened += it })
        }
        try {
            frames(wired)
            for (label in listOf("Playback", "Subtitles & audio", "Watch on TV", "Account", "Help", "About VIPTV")) click(wired, label, prefix = true)
            assertEquals(listOf("Playback", "Subtitles & audio", "Watch on TV", "Account", "Help", "About"), opened)
        } finally { wired.close() }
    }

    private var frame = 0L
    private fun scene(width: Int, height: Int, picker: HomeGenrePicker?, content: @Composable () -> Unit): ImageComposeScene {
        frame = 0
        return ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(false) {
                CompositionLocalProvider(LocalHomeGenres provides picker) {
                    Surface(Modifier.fillMaxSize(), color = Palette.background) { Column { content() } }
                }
            }
        }
    }
    private fun frames(scene: ImageComposeScene, count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun click(scene: ImageComposeScene, label: String, prefix: Boolean = false) {
        val node = nodes(scene).first { (if (prefix) text(it).startsWith(label) else text(it) == label) && it.config.contains(SemanticsActions.OnClick) }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
        frames(scene, 40)
    }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
}
