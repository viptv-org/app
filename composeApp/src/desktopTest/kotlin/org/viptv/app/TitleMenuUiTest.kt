package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.viptv.app.components.ListRemoval
import org.viptv.app.components.LocalListRemoval
import org.viptv.app.components.WATCHED_BADGE
import org.viptv.app.features.continuewatching.REMOVE_CONTINUE_LABEL
import org.viptv.app.features.home.CANCEL_LABEL
import org.viptv.app.features.home.CHOOSE_SOURCE_LABEL
import org.viptv.app.features.home.CardActions
import org.viptv.app.features.home.CardMenu
import org.viptv.app.features.home.LocalCardActions
import org.viptv.app.features.home.MARK_UNWATCHED_LABEL
import org.viptv.app.features.home.MARK_WATCHED_LABEL
import org.viptv.app.features.home.MediaCard
import org.viptv.app.features.home.RESUME_PREVIOUS_LABEL
import org.viptv.app.features.home.WATCH_FROM_START_LABEL
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TitleMenuUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val episode = Card(MediaIdentity("show:1:2", "series", emptyList(), EpisodeCoordinates("aired", 1, 2)),
        provider_id = "addon:4", title = "Show", subtitle = "S1 E2 · 10m left", progress = 0.4, badges = emptyList(),
        parent_identity = MediaIdentity("show", "series", emptyList()))
    private val movie = Card(MediaIdentity("tt1", "movie", emptyList()), provider_id = "addon:4",
        title = "Film", badges = emptyList())
    private val show = Card(MediaIdentity("tt2", "series", emptyList()), provider_id = "addon:4",
        title = "Series", badges = emptyList())
    private val channel = Card(MediaIdentity("chan1", "channel", emptyList()), provider_id = "addon:4",
        title = "News", badges = emptyList())

    private class Calls {
        val made = mutableListOf<String>()
        var actions: CardActions? = null
    }

    private fun calls() = Calls().also { calls ->
        calls.actions = CardActions(play = { calls.made += "play" }, toggleSave = { calls.made += "save" },
            isSaved = { false }, chooseSource = { calls.made += "source" },
            playFromStart = { calls.made += "restart" }, markWatched = { calls.made += "watched" })
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun described(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
    private fun clickable(scene: ImageComposeScene, name: String) = nodes(scene).firstOrNull {
        it.config.contains(SemanticsActions.OnClick) && (text(it) == name || described(it) == name)
    }
    private fun tap(scene: ImageComposeScene, name: String) =
        assertTrue(requireNotNull(clickable(scene, name)) { "$name is not on screen" }
            .config[SemanticsActions.OnClick].action?.invoke() == true)
    private fun frames(scene: ImageComposeScene, count: Int = 8) = repeat(count) {
        scene.render(it * 16_666_667L).close(); Thread.sleep(4)
    }
    private fun withMenu(card: Card = movie, removal: ListRemoval? = null, actions: CardActions?,
        test: (ImageComposeScene) -> Unit) {
        val scene = ImageComposeScene(400, 760, Density(1f)) { ViptvTheme(pointer = true) {
            Box(Modifier.fillMaxSize()) {
                CardMenu(card, saved = false, removal = removal, actions = actions, onDismiss = {},
                    onPlay = { actions?.play?.invoke(card) },
                    onInfo = {}, onToggleSave = actions?.let { { it.toggleSave(card) } })
            }
        } }
        try { frames(scene); test(scene) } finally { scene.close() }
    }

    @Test fun aContinueWatchingCardOffersEveryListedAction() {
        val calls = calls()
        var removed = 0
        withMenu(episode, ListRemoval(REMOVE_CONTINUE_LABEL, continueWatching = true) { removed++ }, calls.actions) { scene ->
            for (action in listOf(RESUME_PREVIOUS_LABEL, CHOOSE_SOURCE_LABEL, MARK_WATCHED_LABEL,
                WATCH_FROM_START_LABEL, REMOVE_CONTINUE_LABEL, "Add to My List", "More info", CANCEL_LABEL))
                assertNotNull(clickable(scene, action), "$action is missing from the title menu")
            tap(scene, RESUME_PREVIOUS_LABEL)
            tap(scene, CHOOSE_SOURCE_LABEL)
            tap(scene, MARK_WATCHED_LABEL)
            tap(scene, WATCH_FROM_START_LABEL)
            tap(scene, REMOVE_CONTINUE_LABEL)
            assertEquals(listOf("play", "source", "watched", "restart"), calls.made)
            assertEquals(1, removed, "the removal asks the page, which owns the confirmation")
        }
    }

    @Test fun aCatalogCardLeavesOutTheContinueOnlyActions() {
        val calls = calls()
        withMenu(movie, actions = calls.actions) { scene ->
            assertNotNull(clickable(scene, "Play"), "a card without progress plays from the start")
            assertNotNull(clickable(scene, CHOOSE_SOURCE_LABEL))
            assertNotNull(clickable(scene, MARK_WATCHED_LABEL))
            assertNotNull(clickable(scene, WATCH_FROM_START_LABEL))
            assertNull(clickable(scene, RESUME_PREVIOUS_LABEL), "nothing is being continued here")
            assertNull(clickable(scene, REMOVE_CONTINUE_LABEL))
            tap(scene, CANCEL_LABEL)
            assertTrue(calls.made.isEmpty(), "Cancel has no side effect")
        }
    }

    @Test fun aTitleLevelSeriesOffersNoSourceChoice() {
        withMenu(show, actions = calls().actions) { scene ->
            assertNull(clickable(scene, CHOOSE_SOURCE_LABEL), "a series without an episode has no source to pick")
            assertNull(clickable(scene, MARK_WATCHED_LABEL), "nor one watch state to mark")
            assertNotNull(clickable(scene, "More info"))
        }
    }

    @Test fun aLiveChannelOffersNoTitleActions() {
        withMenu(channel, actions = calls().actions) { scene ->
            assertNull(clickable(scene, CHOOSE_SOURCE_LABEL), "a channel has no source picker here")
            assertNull(clickable(scene, MARK_WATCHED_LABEL), "nor a watch state to mark")
            assertNull(clickable(scene, WATCH_FROM_START_LABEL))
            assertNotNull(clickable(scene, "More info"))
        }
    }

    @Test fun aWatchedTitleOffersToBeMarkedUnwatched() {
        withMenu(movie.copy(badges = listOf(WATCHED_BADGE)), actions = calls().actions) { scene ->
            assertNotNull(clickable(scene, MARK_UNWATCHED_LABEL))
            assertNull(clickable(scene, MARK_WATCHED_LABEL))
        }
    }

    @Test fun aTitleWithoutMenuActionsKeepsOnlyWhatItHas() {
        withMenu(movie, actions = null) { scene ->
            assertNotNull(clickable(scene, "Play"))
            assertNotNull(clickable(scene, "More info"))
            assertNull(clickable(scene, CHOOSE_SOURCE_LABEL), "no provider means no source picker")
            assertNull(clickable(scene, MARK_WATCHED_LABEL), "local mode cannot mark anything")
        }
    }

    private fun cardScene(pointer: Boolean, actions: CardActions?, test: suspend (ImageComposeScene, () -> Offset) -> Unit) {
        val card = movie.copy(progress = 0.4)
        val scene = ImageComposeScene(600, 700, Density(1f)) { ViptvTheme(pointer = pointer) {
            Box(Modifier.fillMaxSize().padding(40.dp)) {
                CompositionLocalProvider(LocalCardActions provides actions) {
                    MediaCard(card, width = 240, open = {})
                }
            }
        } }
        val at = { nodes(scene).first { text(it) == "Film" }.boundsInRoot.center }
        try { frames(scene); runBlocking { test(scene, at) } } finally { scene.close() }
    }

    @Test fun theTouchOverflowOpensTheTitleMenu() {
        cardScene(pointer = false, actions = calls().actions) { scene, _ ->
            assertNotNull(clickable(scene, "More actions"), "a touch card carries its own menu target")
            tap(scene, "More actions")
            frames(scene, 40)
            assertNotNull(clickable(scene, CHOOSE_SOURCE_LABEL), "the menu opens on the card")
        }
    }

    @Test fun aLongPressOpensTheTitleMenu() {
        cardScene(pointer = false, actions = calls().actions) { scene, at ->
            var clock = 1_000L
            scene.sendPointerEvent(PointerEventType.Press, at(), type = PointerType.Touch, timeMillis = clock)
            repeat(40) { delay(20); clock += 20; scene.render(clock * 1_000_000L).close() }
            frames(scene, 40)
            assertNotNull(clickable(scene, CHOOSE_SOURCE_LABEL), "holding the card opens its menu")
            scene.sendPointerEvent(PointerEventType.Release, at(), type = PointerType.Touch, timeMillis = clock)
            frames(scene, 40)
            assertNotNull(clickable(scene, CHOOSE_SOURCE_LABEL), "the release that ends a hold does not also open the title")
        }
    }

    @Test fun aShortTouchOpensTheTitleInstead() {
        var opened = 0
        val card = movie.copy(progress = 0.4)
        val scene = ImageComposeScene(600, 700, Density(1f)) { ViptvTheme(pointer = false) {
            Box(Modifier.fillMaxSize().padding(40.dp)) {
                CompositionLocalProvider(LocalCardActions provides calls().actions) {
                    MediaCard(card, width = 240, open = { opened++ })
                }
            }
        } }
        try {
            frames(scene)
            val at = nodes(scene).first { text(it) == "Film" }.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, at, type = PointerType.Touch, timeMillis = 1_000)
            frames(scene, 2)
            scene.sendPointerEvent(PointerEventType.Release, at, type = PointerType.Touch, timeMillis = 1_120)
            frames(scene)
            assertNull(clickable(scene, CHOOSE_SOURCE_LABEL), "a tap does not open the menu")
            assertEquals(1, opened, "a tap still opens the title")
        } finally { scene.close() }
    }

    @Test fun aSecondaryClickOpensTheTitleMenu() {
        cardScene(pointer = true, actions = calls().actions) { scene, at ->
            assertNull(clickable(scene, "More actions"), "the pointer right-clicks instead of a button")
            scene.sendPointerEvent(PointerEventType.Press, at(), type = PointerType.Mouse,
                buttons = PointerButtons(isSecondaryPressed = true))
            scene.sendPointerEvent(PointerEventType.Release, at(), type = PointerType.Mouse, buttons = PointerButtons())
            frames(scene, 40)
            assertNotNull(clickable(scene, CHOOSE_SOURCE_LABEL), "right-click opens the title menu")
        }
    }

    @Test fun aCardMenuFromTheContinueListResumesItsOwnEpisode() {
        val calls = calls()
        withMenu(episode, ListRemoval(REMOVE_CONTINUE_LABEL, continueWatching = true) { }, calls.actions) { scene ->
            assertNull(clickable(scene, "Resume"), "the continue card names the episode it resumes")
            tap(scene, RESUME_PREVIOUS_LABEL)
            assertEquals(listOf("play"), calls.made)
        }
    }
}
