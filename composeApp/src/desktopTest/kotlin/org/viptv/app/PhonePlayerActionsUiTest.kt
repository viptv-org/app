package org.viptv.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.player.*
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PhonePlayerActionsUiTest {
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(number: Long) = Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        metadata = CardMetadata(runtime_minutes = 52), provider_id = "addon:7", title = "Episode $number", badges = emptyList(),
        parent_identity = parent, metadata_origin = origin)

    @Test fun authoritativeNextBeyondLoadedEpisodesRequiresExplicitConfirmationAndCreditsNeverDispatches() {
        var played: Card? = null
        var back: (() -> Unit)? = null
        val current = episode(24); val next = episode(25)
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = current, phone = true,
                preview = PlayerFact.Progress(842_000, 3_132_000), tvExtras = TvPlaybackExtras(episodes = listOf(current), nextEpisode = next),
                playEpisode = { played = it }, backChanged = { back = it }, playbackKey = "current")
        } }
        try {
            frames(scene); capture(scene, "phone-player-controls")
            assertNull(played)
            click(scene, "Next episode"); frames(scene, 20)
            assertNull(played); assertTrue(nodes(scene).any { text(it) == "25. Episode 25" })
            capture(scene, "phone-player-next")
            click(scene, "Watch credits"); frames(scene, 40); assertNull(played)
            assertTrue(action(scene, "Next episode").config.getOrNull(SemanticsProperties.Focused) == true)
            click(scene, "Next episode"); frames(scene, 60); back!!(); frames(scene, 80); assertNull(played)
            click(scene, "Next episode"); frames(scene, 100); click(scene, "Next episode")
            assertEquals(next, played)
        } finally { scene.close() }
    }

    @Test fun loadedEpisodePickerStartsAtCurrentEpisodeRestoresFocusAndDispatchesExactCard() {
        var played: Card? = null
        var back: (() -> Unit)? = null
        var closed = 0
        val current = episode(80)
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, { closed++ }, title = current, phone = true,
                tvExtras = TvPlaybackExtras(episodes = (1L..100L).map(::episode)), playEpisode = { played = it }, backChanged = { back = it })
        } }
        try {
            frames(scene); click(scene, "Episodes"); frames(scene, 20)
            val selected = action(scene, "80. Episode 80 Now playing")
            assertEquals(true, selected.config.getOrNull(SemanticsProperties.Selected))
            assertEquals(true, selected.config.getOrNull(SemanticsProperties.Focused))
            assertTrue(selected.boundsInRoot.top >= 0 && selected.boundsInRoot.bottom <= 390)
            capture(scene, "phone-player-episodes-current")
            back!!(); frames(scene, 40); assertEquals(0, closed); assertNull(played)
            assertEquals(true, action(scene, "Episodes").config.getOrNull(SemanticsProperties.Focused))
            click(scene, "Episodes"); frames(scene, 60); click(scene, "81. Episode 81 Play episode")
            assertEquals(episode(81), played)
        } finally { scene.close() }
    }

    @Test fun unavailableEpisodeActionsAreDisabledAndMovieHasNoEpisodeControls() {
        var loading by mutableStateOf(true)
        var current by mutableStateOf(episode(1))
        var played = 0
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = current, phone = true, playEpisode = { played++ }, nextEpisodeLoading = loading)
        } }
        try {
            frames(scene)
            assertTrue(action(scene, "Episodes").config.contains(SemanticsProperties.Disabled))
            assertTrue(action(scene, "Next episode").config.contains(SemanticsProperties.Disabled))
            assertTrue(nodes(scene).any { text(it) == "Finding next episode…" })
            loading = false; frames(scene, 20)
            assertTrue(nodes(scene).any { text(it) == "Next episode unavailable" })
            nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }.forEach {
                assertTrue(it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= 390, "Phone action overflows")
            }
            capture(scene, "phone-player-unavailable-portrait")
            current = current.copy(identity = MediaIdentity("film", "movie", emptyList()), parent_identity = null); frames(scene, 40)
            assertFalse(nodes(scene).any { text(it) == "Next episode" || text(it) == "Episodes" }); assertEquals(0, played)
        } finally { scene.close() }
    }

    @Test fun failureExposesOnlyAvailableRecoveryActionsAndDispatchesEachExplicitly() {
        var retry = 0; var choose = 0; var close = 0
        var available by mutableStateOf(true)
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, "The selected source did not respond.", {}, {}, { close++ }, title = episode(2), phone = true,
                tvExtras = TvPlaybackExtras(fallbackLabel = "Fixture 1080p"), retry = if (available) ({ retry++ }) else null,
                chooseSources = if (available) ({ choose++ }) else null)
        } }
        try {
            frames(scene); capture(scene, "phone-player-source-failure")
            assertEquals(0, retry); assertEquals(0, choose)
            click(scene, "Try Fixture 1080p"); assertEquals(1, retry)
            click(scene, "Choose a source"); assertEquals(1, choose)
            available = false; frames(scene, 20)
            assertFalse(nodes(scene).any { text(it) == "Choose a source" || text(it).startsWith("Try ") })
            nodes(scene).single { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Back") == true && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, close)
        } finally { scene.close() }
    }

    @Test fun metadataOrPlaybackReplacementDismissesNextAndRemovedEpisodesCannotBeChosen() {
        var key by mutableStateOf("old")
        var current by mutableStateOf(episode(2))
        var next by mutableStateOf(episode(3))
        var episodes by mutableStateOf(listOf(episode(2), episode(3)))
        var played = 0
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = current, phone = true, playbackKey = key,
                tvExtras = TvPlaybackExtras(episodes = episodes, nextEpisode = next), playEpisode = { played++ })
        } }
        try {
            frames(scene); click(scene, "Next episode"); frames(scene, 20)
            assertTrue(nodes(scene).any { text(it) == "3. Episode 3" })
            next = episode(4); frames(scene, 40); assertEquals(0, played)
            assertFalse(nodes(scene).any { text(it) == "Up next" })
            click(scene, "Next episode"); frames(scene, 50)
            assertTrue(nodes(scene).any { text(it) == "4. Episode 4" })
            // Replacing the playback owner closes its pending choice without dispatch.
            key = "new"; current = episode(40); next = episode(41); episodes = listOf(episode(40), episode(41)); frames(scene, 60)
            assertEquals(0, played); assertFalse(nodes(scene).any { text(it) == "Up next" })
            click(scene, "Episodes"); frames(scene, 80)
            assertTrue(nodes(scene).any { text(it) == "41. Episode 41 Play episode" })
            episodes = listOf(episode(40)); frames(scene, 100); assertEquals(0, played)
            assertFalse(nodes(scene).any { text(it) == "41. Episode 41 Play episode" })
            click(scene, "40. Episode 40 Now playing"); assertEquals(1, played)
        } finally { scene.close() }
    }

    @Test fun episodesFromAnotherParentProviderOrIncarnationNeverBecomePlayerActions() {
        val here = episode(2); val good = episode(3)
        val foreign = listOf(good.copy(provider_id = "addon:8"), good.copy(parent_identity = parent.copy(id = "other")),
            good.copy(metadata_origin = origin.copy(incarnation = "b".repeat(32))), good.copy(identity = good.identity.copy(episode = null)))
        assertEquals(listOf(here, good), phonePlaybackEpisodes(here, listOf(here, good, good) + foreign))
        val assertions = listOf(ExternalId("imdb", "tt123"), ExternalId("tmdb", "42"))
        val reordered = good.copy(parent_identity = parent.copy(ids = assertions.reversed()))
        assertEquals(listOf(reordered), phonePlaybackEpisodes(here.copy(parent_identity = parent.copy(ids = assertions)), listOf(reordered)))
    }

    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun action(scene: ImageComposeScene, label: String) = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
    private fun click(scene: ImageComposeScene, label: String) { assertTrue(action(scene, label).config[SemanticsActions.OnClick].action!!.invoke()) }
    private fun capture(scene: ImageComposeScene, name: String) {
        System.getenv("VIPTV_PHONE_PLAYER_CAPTURE_DIR")?.let { path ->
            File(path).mkdirs(); scene.render(3_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        }
    }
}
