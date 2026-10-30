package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import org.junit.Rule
import org.viptv.app.features.home.HERO_SUMMARY_TAG
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.preview.PreviewData
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class HomeHeroContinueHoverTest {
    @get:Rule val uiThread = UiThreadRule()

    private val featured = PreviewData.hero
    private val watched = PreviewData.watching.first()
    private val slides = listOf(featured,
        featured.copy(identity = MediaIdentity("hero-2", "movie", emptyList()), title = "Second Feature"),
        featured.copy(identity = MediaIdentity("hero-3", "movie", emptyList()), title = "Third Feature"))

    private class Shell(val scene: ImageComposeScene, val played: MutableList<Card>) : AutoCloseable {
        private var frame = 0L
        fun frames(count: Int = 1) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        fun settle() = frames(20)
        fun nodes(merged: Boolean = true): List<SemanticsNode> =
            scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(merged) }
        fun label(node: SemanticsNode) = (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
        fun heroSummary(): String {
            val bounds = nodes(merged = false)
                .firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == HERO_SUMMARY_TAG }?.boundsInRoot
                ?: return ""
            return nodes().filter { bounds.contains(it.boundsInRoot.center) }.joinToString(" ") { label(it) }
        }
        fun card(title: String): SemanticsNode = nodes().first {
            it.config.contains(SemanticsActions.OnClick) && label(it).contains(title)
        }
        fun tagged(tag: String): SemanticsNode = nodes(merged = false).first {
            it.config.getOrNull(SemanticsProperties.TestTag) == tag
        }
        override fun close() = scene.close()
    }

    private fun shell(browser: Boolean = false): Shell {
        val played = mutableListOf<Card>()
        val home = PreviewData.home.copy(hero = featured, featured = slides)
        val scene = ImageComposeScene(1440, 1000, Density(1f)) {
            ViptvTheme(pointer = true) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    HomeScreen(home, television = false, open = {}, retry = {}, busy = false,
                        desktopChrome = !browser, browser = browser, play = { played += it })
                }
            }
        }
        return Shell(scene, played)
    }

    @Test fun hoveringAContinueWatchingCardShowsThatTitleAndEpisodeInTheHero() {
        for (browser in listOf(false, true)) {
            val where = if (browser) "web" else "desktop"
            val shell = shell(browser)
            try {
                shell.settle()
                val start = shell.heroSummary()
                assertTrue(start.contains(featured.title), "$where: the hero starts on the featured title: $start")
                assertTrue(start.contains("Featured"), "$where: the hero is labelled as the featured pick: $start")

                val bounds = shell.card(watched.title).boundsInRoot
                shell.scene.sendPointerEvent(PointerEventType.Move, bounds.center)
                shell.settle()

                val hero = shell.heroSummary()
                assertTrue(hero.contains(watched.title), "$where: the hero shows the hovered Continue Watching title: $hero")
                assertTrue(hero.contains("S1 E2"), "$where: the hero shows the hovered episode: $hero")
                assertTrue(hero.contains("Continue Watching"), "$where: the hero names the row it previews: $hero")
                assertFalse(hero.contains("Featured"), "$where: the preview is not labelled as the featured pick: $hero")
                assertFalse(hero.contains(featured.title), "$where: the featured title gives way to the hovered one: $hero")

                shell.scene.sendPointerEvent(PointerEventType.Move, Offset(1436f, 8f))
                shell.settle()
                assertTrue(shell.heroSummary().contains(featured.title),
                    "$where: leaving the card restores the featured hero: ${shell.heroSummary()}")
            } finally { shell.close() }
        }
    }

    @Test fun hoveringAContinueWatchingCardCrossfadesTheHeroAndHoldsTheRows() {
        val shell = shell()
        try {
            shell.settle()
            val row = shell.card(watched.title).boundsInRoot
            val summary = shell.tagged(HERO_SUMMARY_TAG).boundsInRoot
            shell.scene.sendPointerEvent(PointerEventType.Move, row.center)
            var bothOnScreen = false
            for (step in 1..14) {
                shell.frames(1)
                val hero = shell.heroSummary()
                if (hero.contains(featured.title) && hero.contains(watched.title)) bothOnScreen = true
            }
            assertTrue(bothOnScreen, "the hero crossfades from the featured slide to the preview")
            shell.settle()
            val preview = shell.heroSummary()
            assertTrue(preview.contains(watched.title) && !preview.contains(featured.title), "the preview owns the hero: $preview")
            assertFalse(preview.contains("Featured"), "the carousel dots are not drawn for the preview: $preview")
            assertEquals(row, shell.card(watched.title).boundsInRoot, "the rows below the hero never move under the pointer")
            assertEquals(summary, shell.tagged(HERO_SUMMARY_TAG).boundsInRoot, "the preview keeps the hero's footprint")

            shell.scene.sendPointerEvent(PointerEventType.Move, Offset(1436f, 8f))
            var backTogether = false
            for (step in 1..14) {
                shell.frames(1)
                val hero = shell.heroSummary()
                if (hero.contains(featured.title) && hero.contains(watched.title)) backTogether = true
            }
            assertTrue(backTogether, "leaving the card crossfades the preview back to the featured slide")
            shell.settle()
            assertEquals(row, shell.card(watched.title).boundsInRoot, "the rows below the hero never move on the way back")
            assertEquals(summary, shell.tagged(HERO_SUMMARY_TAG).boundsInRoot, "the featured hero keeps its footprint")
            assertTrue(shell.heroSummary().contains("Featured"), "the dots come back with the featured slide")
        } finally { shell.close() }
    }

    @Test fun clickingTheHoveredContinueWatchingCardStillResumesTheEpisode() {
        val shell = shell()
        try {
            shell.settle()
            val bounds = shell.card(watched.title).boundsInRoot
            shell.scene.sendPointerEvent(PointerEventType.Move, bounds.center)
            shell.settle()
            shell.scene.sendPointerEvent(PointerEventType.Press, bounds.center)
            shell.scene.sendPointerEvent(PointerEventType.Release, bounds.center)
            shell.settle()
            assertEquals(listOf(watched), shell.played, "the click still resumes the hovered episode")
        } finally { shell.close() }
    }
}
