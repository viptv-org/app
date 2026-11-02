package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.tvHold
import org.viptv.app.features.details.TvDetailExtras
import org.viptv.app.features.details.TvDetailHero
import org.viptv.app.features.home.CardActions
import org.viptv.app.features.home.LocalCardActions
import org.viptv.app.features.home.TvCatalogCard
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvHoldTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Scene(content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        private var focusManager: FocusManager? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(television = true) {
                focusManager = LocalFocusManager.current
                Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
            }
        }.also { owner -> repeat(16) { owner.render(frame++ * 16_666_667L).close() } }

        fun frames(n: Int = 6) = repeat(n) { scene.render(frame++ * 16_666_667L).close() }
        private fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        private fun label(node: SemanticsNode) = (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ") +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }.joinToString(" ")).trim()
        fun has(text: String) = nodes().any { label(it) == text }
        fun focusedLabel() = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::label)
        fun keyDown(key: Key) = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        fun keyUp(key: Key) = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        fun ok() { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter); frames() }
        suspend fun waitFor(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!condition()) {
                check(System.nanoTime() < deadline) { "timed out waiting for $what" }
                frames(); delay(15)
            }
        }
        override fun close() = scene.close()
    }

    private fun movieCard() = Card(MediaIdentity("tt1", "movie", emptyList()), provider_id = "simkl",
        title = "Cached title", subtitle = "1h 52m left", badges = listOf("Simkl", "4K"), poster = null)

    private fun cardActions(played: AtomicInteger) = CardActions(
        play = { played.incrementAndGet() }, toggleSave = {}, isSaved = { false },
        chooseSource = {}, playFromStart = {}, markWatched = {})

    @Test fun shortSelectOnACardOpensTheTitle() = onEdt {
        val opened = AtomicInteger()
        val requester = FocusRequester()
        Scene {
            CompositionLocalProvider(LocalCardActions provides cardActions(AtomicInteger())) {
                TvCatalogCard(movieCard(), 1f, true, Modifier.focusRequester(requester), focused = {}, open = { opened.incrementAndGet() })
            }
        }.use { s ->
            s.frames()
            assertTrue(requester.requestFocus(), "the card must be focusable")
            s.frames()
            s.ok()
            assertEquals(1, opened.get(), "a short OK press opens the title")
            assertFalse(s.has("More info"), "a short press shows no title menu")
        }
    }

    @Test fun holdOnACardOpensTheTitleMenuWithoutOpeningTheTitle() = onEdt {
        val opened = AtomicInteger()
        val played = AtomicInteger()
        val requester = FocusRequester()
        Scene {
            CompositionLocalProvider(LocalCardActions provides cardActions(played)) {
                TvCatalogCard(movieCard(), 1f, true, Modifier.focusRequester(requester), focused = {}, open = { opened.incrementAndGet() })
            }
        }.use { s ->
            s.frames()
            assertTrue(requester.requestFocus(), "the card must be focusable")
            s.frames()
            s.keyDown(Key.DirectionCenter)
            s.waitFor("the title menu to open on hold") { s.has("More info") }
            assertTrue(s.has("Choose source"), "the shared title menu offers the local actions")
            assertEquals(0, opened.get(), "holding the card must not open the title")
            assertEquals(0, played.get(), "holding the card must not play the title")
        }
    }

    @Test fun dismissingTheTitleMenuReturnsFocusToTheCard() = onEdt {
        val opened = AtomicInteger()
        val played = AtomicInteger()
        val requester = FocusRequester()
        Scene {
            CompositionLocalProvider(LocalCardActions provides cardActions(played)) {
                TvCatalogCard(movieCard(), 1f, true, Modifier.focusRequester(requester), focused = {}, open = { opened.incrementAndGet() })
            }
        }.use { s ->
            s.frames()
            assertTrue(requester.requestFocus(), "the card must be focusable")
            s.frames()
            s.keyDown(Key.DirectionCenter)
            s.waitFor("the title menu to open on hold") { s.has("More info") }
            s.keyDown(Key.Back); s.keyUp(Key.Back)
            s.waitFor("the title menu to close") { !s.has("More info") }
            assertEquals("Cached title", s.focusedLabel()?.takeWhile { it != ',' }?.trim(), "Back from the menu returns focus to the card")
            assertEquals(0, opened.get(), "closing the menu changes nothing")
            assertEquals(0, played.get(), "closing the menu changes nothing")
        }
    }

    @Test fun tvHoldSuppressesTheReleaseAndTheNextShortPressClicks() = onEdt {
        val opened = AtomicInteger()
        val held = AtomicInteger()
        val requester = FocusRequester()
        Scene {
            val interaction = remember { MutableInteractionSource() }
            Box(Modifier.size(120.dp).focusRequester(requester)
                .tvHold(holdMillis = 150, onHold = { held.incrementAndGet() })
                .pointerClickable(interaction, indication = null) { opened.incrementAndGet() })
        }.use { s ->
            s.frames()
            assertTrue(requester.requestFocus(), "the control must be focusable")
            s.frames()
            s.keyDown(Key.DirectionCenter)
            s.waitFor("the hold to fire") { held.get() == 1 }
            s.keyUp(Key.DirectionCenter)
            s.frames()
            assertEquals(0, opened.get(), "releasing after a hold must not click")
            s.ok()
            assertEquals(1, opened.get(), "a later short press still clicks")
        }
    }

    @Test fun holdOnPlayResumeOpensManualSourcesWithoutPlaying() = onEdt {
        val played = AtomicInteger()
        val chose = AtomicInteger()
        val playFocus = FocusRequester()
        Scene {
            TvDetailHero(movieCard(), TvDetailExtras(), 1f, play = { played.incrementAndGet() }, save = {}, trailer = {},
                restart = {}, first = playFocus, down = null, requestInitialFocus = false, hold = { chose.incrementAndGet() })
        }.use { s ->
            s.frames()
            assertTrue(playFocus.requestFocus(), "Play/Resume must be focusable")
            s.frames()
            s.keyDown(Key.DirectionCenter)
            s.waitFor("the manual source selection to open on hold") { chose.get() == 1 }
            assertEquals(0, played.get(), "holding Play/Resume must not play")
            s.keyUp(Key.DirectionCenter)
            s.frames()
            assertEquals(0, played.get(), "releasing after the hold must not play")
            s.ok()
            assertEquals(1, played.get(), "a short press keeps playing")
        }
    }
}