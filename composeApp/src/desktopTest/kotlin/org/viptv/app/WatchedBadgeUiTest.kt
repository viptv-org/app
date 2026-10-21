package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.features.home.MediaCard
import org.viptv.app.features.home.TvCatalogCard
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import java.io.File
import kotlin.test.*

/**
 * Canvas dictionary §A1 / Tv3CardStates / D3CardStates / M3Browse: a watched
 * title shows a check in the art's top-right corner, the art is not dimmed,
 * and the card announces "Watched" without another focus stop.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class WatchedBadgeUiTest {
    private fun card(id: String, badges: List<String>, progress: Double? = null) = Card(
        MediaIdentity(id, "movie", emptyList()), provider_id = "addon:1", title = "Title $id",
        badges = badges, progress = progress)

    @Test fun phoneAndDesktopCardsDrawTheCornerCheckOnlyForWatchedTitles() {
        val watched = card("tt1", listOf("Watched", "4K"))
        val unseen = card("tt2", listOf("4K"))
        val resumed = card("tt3", listOf("Watched"), progress = 0.4)
        val scene = ImageComposeScene(720, 520, Density(1f)) { ViptvTheme {
            Row(Modifier.fillMaxSize().background(Palette.background).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MediaCard(watched, width = 112, portrait = true, open = {})   // phone grid
                MediaCard(unseen, width = 112, portrait = true, open = {})
                MediaCard(resumed, width = 176, portrait = true, open = {})   // desktop My List
            }
        } }
        try {
            frames(scene)
            capture(scene, "watched-badge-phone-desktop.png")
            val badges = unmerged(scene).filter { description(it) == "Watched" }
            assertEquals(2, badges.size, "Exactly the two watched cards carry the check")
            assertFalse(unmerged(scene).any { text(it) == "Watched" }, "Watched is a check, never a text label on the art")
            assertTrue(unmerged(scene).any { text(it) == "4K" }, "Other art labels keep their slot")
            // Phone 112 dp poster: the M3Browse 22/13/6 check, top-right.
            val phone = badges.minBy { it.boundsInRoot.left }.boundsInRoot
            assertBadge(phone, art = Rect(24f, 24f, 136f, 192f), diameter = 21f..22.5f)
            // Desktop-sized poster: the D3CardStates 26 dp check.
            val desktop = badges.maxBy { it.boundsInRoot.left }.boundsInRoot
            val left = 24f + 112f + 16f + 112f + 16f
            assertBadge(desktop, art = Rect(left, 24f, left + 176f, 24f + 264f), diameter = 25.5f..26.5f)
            // The card is one announcement: title and Watched together.
            assertTrue(merged(scene).any { description(it).contains("Title tt1") && description(it).contains("Watched") })
            assertFalse(merged(scene).any { description(it).contains("Title tt2") && description(it).contains("Watched") })
        } finally { scene.close() }
    }

    @Test fun tvCardAnnouncesWatchedAndKeepsTheCheckClearOfRingAndProgress() {
        val watched = card("tt1", listOf("Watched"), progress = 0.5)
        val scene = ImageComposeScene(600, 560, Density(1f)) { ViptvTheme(true) {
            Row(Modifier.fillMaxSize().background(Palette.background).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TvCatalogCard(watched, 1f, true, focused = {}, open = {}, focusedCaptions = true)
                TvCatalogCard(card("tt2", emptyList()), 1f, true, focused = {}, open = {}, focusedCaptions = true)
            }
        } }
        try {
            frames(scene)
            capture(scene, "watched-badge-tv.png")
            val nodes = merged(scene)
            assertTrue(nodes.any { description(it).startsWith("Title tt1") && description(it).contains("Watched") },
                "The TV card's single accessibility node must say Watched")
            assertFalse(nodes.any { description(it).startsWith("Title tt2") && description(it).contains("Watched") })
            // One node per TV card: the check adds no node and no focus stop of its own.
            assertFalse(unmerged(scene).any { description(it) == "Watched" })
        } finally { scene.close() }
    }

    private fun assertBadge(badge: Rect, art: Rect, diameter: ClosedFloatingPointRange<Float>) {
        assertTrue(badge.width in diameter && badge.height in diameter, "Badge size $badge")
        val inset = badge.width * 10f / 34f
        assertEquals(art.right - inset, badge.right, 1.5f, "Badge right inset in $art")
        assertEquals(art.top + inset, badge.top, 1.5f, "Badge top inset in $art")
        assertTrue(badge.right <= art.right - 4f && badge.top >= art.top + 4f, "Clear of the focus ring")
        assertTrue(badge.bottom < art.top + art.height / 2, "Far from the bottom progress bar")
    }
    private fun frames(scene: ImageComposeScene) = repeat(12) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
    private fun capture(scene: ImageComposeScene, name: String) = System.getenv("VIPTV_CARD_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs()
        scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, name).writeBytes(it.bytes) } }
    }
    private fun merged(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun unmerged(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ").orEmpty()
}
