package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.viptv.app.features.player.PlaybackFailure
import org.viptv.app.features.player.PlayerFallbackNotice
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.features.player.TvPlaybackExtras
import org.viptv.app.features.player.TvPlayerScene
import org.viptv.app.features.player.TvPlayerScreen
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlayerFailurePageTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val title = Card(MediaIdentity("private-movie", "movie", emptyList()), provider_id = "addon:7", title = "A Movie",
        subtitle = "2026 · 1h 52m", badges = emptyList())
    private val failure = "This source could not play. Choose another source."
    private val source = "LordStreams 1080p"
    private val named = "$source didn’t respond. Choose another source."
    private val decode = "This device can’t decode this stream. Choose another source."

    private class Clicks { var retry = 0; var choose = 0; var close = 0 }

    @Test fun phoneDrawsTheSharedPage() {
        val clicks = Clicks()
        scene(844, 390) { PlayerScreen(null, true, failure, {}, {}, { clicks.close++ }, title, phone = true,
            tvExtras = TvPlaybackExtras(fallbackLabel = "Fixture 1080p"), retry = { clicks.retry++ }, chooseSources = { clicks.choose++ },
            failure = PlaybackFailure.Failed, failedSource = source) }
            .use { scene ->
                assertPage(scene, named, listOf("Try Fixture 1080p", "Choose another source"), back = false)
                click(scene, "Try Fixture 1080p"); click(scene, "Choose another source")
                assertEquals(1, clicks.retry); assertEquals(1, clicks.choose)
            }
    }

    @Test fun desktopDrawsTheSharedPageInsteadOfAnErrorLineOverTheFrame() {
        val clicks = Clicks()
        scene(1440, 900) { PlayerScreen(null, true, failure, {}, {}, { clicks.close++ }, title,
            tvExtras = TvPlaybackExtras(fallbackLabel = "Fixture 1080p"), retry = { clicks.retry++ }, chooseSources = { clicks.choose++ },
            failure = PlaybackFailure.Failed, failedSource = source) }
            .use { scene ->
                assertPage(scene, named, listOf("Try Fixture 1080p", "Choose another source"))
                assertFalse(nodes(scene).any { label(it) in listOf("Play", "Pause", "Close playback", "Audio and subtitles") },
                    "the failure page replaces the controls")
                click(scene, "Try Fixture 1080p"); click(scene, "Choose another source"); click(scene, "Back")
                assertEquals(1, clicks.retry); assertEquals(1, clicks.choose); assertEquals(1, clicks.close)
            }
    }

    @Test fun webDrawsTheSharedPageAtBothWidthsWithoutOverflowing() {
        for ((width, height) in listOf(1440 to 900, 390 to 844)) {
            scene(width, height) { PlayerScreen(null, true, failure, {}, {}, {}, title, canvasColor = androidx.compose.ui.graphics.Color.Transparent,
                tvExtras = TvPlaybackExtras(fallbackLabel = "Fixture 1080p"), retry = {}, chooseSources = {},
                failure = PlaybackFailure.Failed, failedSource = source) }
                .use { scene ->
                    assertPage(scene, named, listOf("Try Fixture 1080p", "Choose another source"))
                    nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }.forEach {
                        assertTrue(it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= width,
                            "$width: \"${label(it)}\" leaves the viewport at ${it.boundsInRoot}")
                    }
                }
        }
    }

    @Test fun desktopDrawsThePageForARendererFailureAndForAnUndecodableStream() {
        for ((fact, description) in listOf(
                PlayerFact.Failed to "$source didn’t respond. Choose another source to continue from 14:02.",
                PlayerFact.Undecodable to "This device can’t decode this stream. Choose another source to continue from 14:02.")) {
            val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 4)
            scene(1440, 900, Dispatchers.Unconfined) { PlayerScreen(null, true, null, {}, {}, {}, title,
                preview = PlayerFact.Progress(842_000, 3_132_000), externalFacts = facts, retry = {}, chooseSources = {},
                failedSource = source) }
                .use { scene ->
                    frames(scene)
                    assertFalse(nodes(scene).any { text(it) == "This source stopped working" }, "$fact: nothing failed yet")
                    assertTrue(facts.tryEmit(fact))
                    frames(scene)
                    assertPage(scene, description, listOf("Retry", "Choose another source"))
                }
        }
    }

    @Test fun televisionDrawsTheSamePage() {
        val clicks = Clicks()
        scene(1920, 1080, television = true) { TvPlayerScreen(null, false, null, {}, {}, { clicks.close++ }, title,
            TvPlaybackExtras(fallbackLabel = "Fixture 4K"), { clicks.choose++ }, null, { clicks.retry++ }, {}, scene = TvPlayerScene.Error,
            failure = PlaybackFailure.Failed, failedSource = source) }
            .use { scene ->
                assertPage(scene, named, listOf("Try Fixture 4K", "Choose another source"))
                click(scene, "Try Fixture 4K"); click(scene, "Choose another source"); click(scene, "Back")
                assertEquals(1, clicks.retry); assertEquals(1, clicks.choose); assertEquals(1, clicks.close)
            }
    }

    @Test fun everyPlatformWithdrawsTheActionsTheControllerCannotOffer() {
        for (television in listOf(false, true)) {
            scene(if (television) 1920 else 1440, if (television) 1080 else 900, television = television) {
                if (television) TvPlayerScreen(null, false, null, {}, {}, {}, title, TvPlaybackExtras(), null, null, null, {}, scene = TvPlayerScene.Error,
                    failure = PlaybackFailure.Timeout, failedSource = source)
                else PlayerScreen(null, true, failure, {}, {}, {}, title, failure = PlaybackFailure.Timeout, failedSource = source)
            }.use { scene ->
                assertPage(scene, named, emptyList())
            }
        }
    }

    @Test fun everyPlatformSaysTheSameThingAboutTheSameFailure() {
        for ((cause, position, description) in listOf(
                Triple(PlaybackFailure.Failed, 0L, "$source didn’t respond. Choose another source."),
                Triple(PlaybackFailure.Timeout, 0L, "$source didn’t respond. Choose another source."),
                Triple(PlaybackFailure.Failed, 842_000L, "$source didn’t respond. Choose another source to continue from 14:02."))) {
            val expected = listOf("Back", "Choose another source", description, "This source stopped working", "Try Fixture 1080p")
            for ((name, labels) in failurePages(cause, position)) assertEquals(expected, labels, "$name with $cause at $position")
        }
    }

    @Test fun aDecodeFailureKeepsItsDeviceSentenceOnEveryPlatform() {
        for ((name, labels) in failurePages(PlaybackFailure.Undecodable, 0L)) {
            assertTrue("This source stopped working" in labels, "$name: the page headline")
            assertTrue(labels.any { it.startsWith("This ") && it.contains("can’t decode this stream") && it.contains("Choose another source") },
                "$name keeps a device-decode sentence; saw $labels")
            assertFalse(labels.any { it.contains("didn’t respond") }, "$name must not blame the source for the device; saw $labels")
        }
        assertTrue(failurePages(PlaybackFailure.Undecodable, 0L).getValue("television")
            .any { it.contains("4K Dolby Vision releases are not supported") }, "TV keeps its decode guidance")
    }

    private fun failurePages(cause: PlaybackFailure, position: Long): Map<String, List<String>> {
        fun pointer(phone: Boolean, web: Boolean = false) = pageLabels(if (phone) 844 else 1440, if (phone) 390 else 900) {
            PlayerScreen(null, true, failure, {}, {}, {}, null, phone = phone,
                canvasColor = if (web) androidx.compose.ui.graphics.Color.Transparent else androidx.compose.ui.graphics.Color.Black,
                preview = PlayerFact.Progress(position, 3_132_000), tvExtras = TvPlaybackExtras(fallbackLabel = "Fixture 1080p"),
                retry = {}, chooseSources = {}, failure = cause, failedSource = source)
        }
        return mapOf(
            "phone" to pointer(phone = true),
            "desktop" to pointer(phone = false),
            "web" to pointer(phone = false, web = true),
            "television" to pageLabels(1920, 1080, television = true) { TvPlayerScreen(null, false, failure, {}, {}, {}, null,
                TvPlaybackExtras(fallbackLabel = "Fixture 1080p"), {}, null, {}, {}, initialFacts = listOf(PlayerFact.Progress(position, 3_132_000)),
                failure = cause, failedSource = source) },
        )
    }

    private fun pageLabels(width: Int, height: Int, television: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit): List<String> =
        scene(width, height, television = television, content = content).use { scene ->
            frames(scene)
            nodes(scene).map(::label).filter { it.isNotEmpty() }.distinct().sorted()
        }

    @Test fun theBestMatchFallbackDrawsTheSamePageWithStop() {
        for (television in listOf(false, true)) {
            for (phone in if (television) listOf(false) else listOf(true, false)) {
                var stopped = 0
                val notice = PlayerFallbackNotice("This source could not play", "Source b", "1080p · WEB-DL · addon:b")
                scene(if (television) 1920 else 1440, if (television) 1080 else 900, television = television) {
                    if (television) TvPlayerScreen(null, false, failure, {}, {}, {}, title, TvPlaybackExtras(), null, null, null, {},
                        fallback = notice, stopFallback = { stopped++ }, scene = TvPlayerScene.Error)
                    else PlayerScreen(null, true, failure, {}, {}, {}, title, phone = phone, fallback = notice,
                        stopFallback = { stopped++ })
                }.use { scene ->
                    frames(scene)
                    val texts = nodes(scene).map(::text)
                    assertTrue("This source could not play. Trying the next best source…" in texts, "the notice is the page's headline; saw $texts")
                    assertTrue(texts.any { it.contains("Source b") }, "the next source's facts follow it; saw $texts")
                    assertFalse("This source stopped working" in texts, "the page waits for Stop before the failure copy")
                    assertFalse(nodes(scene).any { text(it) == "Back" }, "the fallback notice has no Back")
                    click(scene, "Stop")
                    assertEquals(1, stopped)
                }
            }
        }
    }

    private fun assertPage(scene: ImageComposeScene, description: String, actions: List<String>, back: Boolean = true) {
        frames(scene)
        val texts = nodes(scene).map(::text)
        assertTrue("This source stopped working" in texts, "the page headline is drawn; saw $texts")
        assertTrue(description in texts, "the page explains the failure; saw $texts")
        for (action in actions) assertNotNull(nodes(scene).firstOrNull {
            text(it) == action && it.config.contains(SemanticsActions.OnClick) }, "no \"$action\" action; saw $texts")
        assertEquals(back, nodes(scene).any { text(it) == "Back" && it.config.contains(SemanticsActions.OnClick) }, "the Back action")
    }

    private fun scene(width: Int, height: Int, dispatcher: kotlinx.coroutines.CoroutineDispatcher? = null, television: Boolean = false,
        content: @androidx.compose.runtime.Composable () -> Unit): ImageComposeScene {
        val body: @androidx.compose.runtime.Composable () -> Unit = { ViptvTheme(television = television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
        } }
        return if (dispatcher == null) ImageComposeScene(width, height, Density(1f), content = body)
        else ImageComposeScene(width, height, Density(1f), coroutineContext = dispatcher, content = body)
    }

    private fun frames(scene: ImageComposeScene, count: Int = 20) {
        var time = 0L
        repeat(count) { scene.render(time).close(); time += 16_666_667L }
    }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun label(node: SemanticsNode) = text(node).ifEmpty {
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty() }
    private fun click(scene: ImageComposeScene, label: String) {
        val action = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
        assertTrue(action.config[SemanticsActions.OnClick].action!!.invoke())
    }
    private inline fun <T> ImageComposeScene.use(block: (ImageComposeScene) -> T): T = try { block(this) } finally { close() }
}
