package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.home.MEDIA_CARD_HOVER_TAG
import org.viptv.app.state.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.awt.EventQueue
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

/**
 * app#56: on desktop and web a hovered media card dims its art to 0.38, shows
 * the accent play disc and draws the inset 2 px ring (components.md §6 "Hover
 * (D)"). Continue Watching and My List are the two rows the owner hovered.
 * Touch keeps no hover visuals.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class PointerHoverUiTest {
    private val origin = MetadataOrigin("stremio", "addon:4", "c".repeat(32))
    private val resume = Card(MediaIdentity("tt0111161", "movie", emptyList()), provider_id = "addon:4",
        title = "Resume Movie", badges = emptyList(), progress = 0.4, metadata_origin = origin)
    private val saved = Card(MediaIdentity("tt0000001", "series", emptyList()), provider_id = "addon:4",
        title = "Saved Show", badges = emptyList(), metadata_origin = origin)
    private val home = HomeSnapshot(
        scope = Scope("viptv:remote", "account", "1", 1), revision = 1,
        rows = listOf(
            HomeRow(id = "continue", title = "Continue Watching", provider_id = "library", state = RowState.READY, cards = listOf(resume)),
            HomeRow(id = "favorites", title = "My List", provider_id = "library", state = RowState.READY, cards = listOf(saved))),
        complete = true)

    private val edt = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = !EventQueue.isDispatchThread()
        override fun dispatch(context: CoroutineContext, block: Runnable) = EventQueue.invokeLater(block)
    }
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(edt, block)
    private fun nodes(scene: ImageComposeScene, merged: Boolean = true): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(merged) }
    private fun label(node: SemanticsNode) = (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
    private fun card(scene: ImageComposeScene, title: String) = nodes(scene).first {
        it.config.contains(SemanticsActions.OnClick) && label(it).contains(title)
    }
    private fun hoverTags(scene: ImageComposeScene) = nodes(scene, merged = false)
        .filter { it.config.getOrNull(SemanticsProperties.TestTag) == MEDIA_CARD_HOVER_TAG }

    private fun scene(pointer: Boolean, browser: Boolean) = ImageComposeScene(1280, 900, Density(1f)) {
        ViptvTheme(pointer = pointer) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                HomeScreen(home, television = false, retry = {}, open = {}, busy = false,
                    desktopChrome = !browser, browser = browser)
            }
        }
    }

    @Test fun desktopAndWebContinueWatchingAndMyListCardsShowTheHoverStateAndClearItOnExit() = onEdt {
        for (browser in listOf(false, true)) {
            val where = if (browser) "web" else "desktop"
            val scene = scene(pointer = true, browser = browser)
            var frame = 0L
            fun render() = repeat(4) { scene.render(frame++ * 16_666_667L).close() }
            try {
                render()
                assertTrue(hoverTags(scene).isEmpty(), "$where: no card is hovered before the mouse arrives")
                for (title in listOf("Resume Movie", "Saved Show")) {
                    val bounds = card(scene, title).boundsInRoot
                    scene.sendPointerEvent(PointerEventType.Move, bounds.center); render()
                    val shown = hoverTags(scene)
                    assertEquals(1, shown.size, "$where: hovering $title shows exactly one hover state")
                    assertTrue(bounds.contains(shown.single().boundsInRoot.center), "$where: the hover state is on $title")
                    // Leaving the card clears the dim, disc and ring.
                    scene.sendPointerEvent(PointerEventType.Move, Offset(1270f, 890f)); render()
                    assertTrue(hoverTags(scene).isEmpty(), "$where: the hover state clears when the mouse leaves $title")
                }
            } finally { scene.close() }
        }
    }

    @Test fun touchLayoutsKeepNoHoverVisuals() = onEdt {
        val scene = scene(pointer = false, browser = false)
        var frame = 0L
        fun render() = repeat(4) { scene.render(frame++ * 16_666_667L).close() }
        try {
            render()
            scene.sendPointerEvent(PointerEventType.Move, card(scene, "Resume Movie").boundsInRoot.center); render()
            assertTrue(hoverTags(scene).isEmpty(), "no hover visuals without a pointer platform")
        } finally { scene.close() }
    }
}
