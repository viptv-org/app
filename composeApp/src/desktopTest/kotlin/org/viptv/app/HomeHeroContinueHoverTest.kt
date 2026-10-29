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
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class HomeHeroContinueHoverTest {
    @get:Rule val uiThread = UiThreadRule()

    private val featured = PreviewData.hero
    private val watched = PreviewData.watching.first()

    private class Shell(val scene: ImageComposeScene, val played: MutableList<Card>) : AutoCloseable {
        private var frame = 0L
        fun settle() = repeat(6) { scene.render(frame++ * 16_666_667L).close() }
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
        override fun close() = scene.close()
    }

    private fun shell(browser: Boolean = false): Shell {
        val played = mutableListOf<Card>()
        val scene = ImageComposeScene(1440, 1000, Density(1f)) {
            ViptvTheme(pointer = true) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    HomeScreen(PreviewData.home, television = false, open = {}, retry = {}, busy = false,
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
