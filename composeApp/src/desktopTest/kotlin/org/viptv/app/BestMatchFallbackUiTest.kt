package org.viptv.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.SourceGroups
import org.viptv.app.connection.interimSourceGroups
import org.viptv.app.features.player.PlaybackFailure
import org.viptv.app.features.player.PlayerFallbackNotice
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class BestMatchFallbackUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val title = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "fixture", title = "Movie", badges = emptyList())
    private fun row(id: String) = SourceCandidate(id, "addon:$id", "Source $id", quality_height = 1080, delivery = "direct")

    @Test fun thePickerShowsTheExhaustedBannerAndTheDidNotStartLabel() {
        val rows = listOf(row("a"), row("b"), row("c"))
        val groups = interimSourceGroups(rows)
        val scene = ImageComposeScene(844, 800, Density(1f)) { ViptvTheme {
            SourcesScreen(title, rows, false, null, {}, {}, detail = null, television = false,
                groups = groups, banner = "3 sources couldn’t start on this device. Choose a source.", failed = listOf("a", "b", "c"))
        } }
        try {
            frames(scene)
            assertTrue(nodes(scene).any { text(it) == "3 sources couldn’t start on this device. Choose a source." })
            assertEquals(3, nodes(scene).count { text(it).contains("Didn’t start") }, "Each failed row is labelled")
            assertTrue(nodes(scene).any { text(it).contains("Best match") }, "The marker stays on the row that failed")
        } finally { scene.close() }
    }

    @Test fun theFallbackNoticeNamesTheNextSourceAndStops() {
        var stopped = 0
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, {}, {}, {}, title = title, phone = true, failure = PlaybackFailure.Failed,
                fallback = PlayerFallbackNotice("This source could not play", "Source b", "1080p · WEB-DL · addon:b"),
                stopFallback = { stopped++ })
        } }
        try {
            frames(scene)
            assertTrue(nodes(scene).any { text(it) == "This source could not play. Trying the next best source…" })
            assertTrue(nodes(scene).any { text(it).contains("Source b") })
            assertFalse(nodes(scene).any { text(it) == "This source stopped working" }, "The failure screen waits for Stop")
            click(scene, "Stop")
            assertEquals(1, stopped)
        } finally { scene.close() }
    }

    @Test fun theNoAudioNoticeOffersNextSourceOnlyInABestMatchSession() {
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, {}, {}, {}, title = title, phone = true, audioNotice = "This source has no French audio.")
        } }
        try {
            frames(scene)
            assertTrue(nodes(scene).any { text(it) == "This source has no French audio." })
            assertFalse(nodes(scene).any { text(it) == "Next source" }, "No action outside a Best match session")
        } finally { scene.close() }
    }

    @Test fun theNoAudioNoticeInASessionOffersNextSource() {
        var next = 0
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, {}, {}, {}, title = title, phone = true, audioNotice = "This source has no French audio.",
                nextSource = { next++ })
        } }
        try {
            frames(scene)
            click(scene, "Next source")
            assertEquals(1, next)
        } finally { scene.close() }
    }

    @Test fun theNoAudioNoticeHidesByItselfAfterSixSecondsOnPhoneAndTelevision() {
        for (television in listOf(false, true)) {
            var notice by mutableStateOf<String?>("This source has no English audio.")
            var dismissed = 0
            val scene = ImageComposeScene(if (television) 1920 else 390, if (television) 1080 else 844, Density(1f)) {
                ViptvTheme(television = television) {
                    PlayerScreen(null, false, {}, {}, {}, title = title, phone = !television, television = television,
                        audioNotice = notice, dismissAudioNotice = { dismissed++; notice = null }, nextSource = {})
                }
            }
            try {
                var frame = 0L
                fun advance(millis: Long) = repeat((millis / 100).toInt()) { scene.render(frame).close(); frame += 100_000_000L }
                advance(1_000)
                assertTrue(nodes(scene).any { text(it) == "This source has no English audio." }, "shown first (tv=$television)")
                advance(4_000)
                assertTrue(nodes(scene).any { text(it) == "This source has no English audio." }, "still up after 5 s (tv=$television)")
                advance(2_000)
                assertFalse(nodes(scene).any { text(it) == "This source has no English audio." }, "gone after 6 s (tv=$television)")
                assertFalse(nodes(scene).any { text(it) == "Next source" }, "its action leaves with it (tv=$television)")
                assertEquals(1, dismissed, "the controller hears once (tv=$television)")
                advance(10_000)
                assertFalse(nodes(scene).any { text(it) == "This source has no English audio." }, "stays gone (tv=$television)")
            } finally { scene.close() }
        }
    }

    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun action(scene: ImageComposeScene, label: String) = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
    private fun click(scene: ImageComposeScene, label: String) { assertTrue(action(scene, label).config[SemanticsActions.OnClick].action!!.invoke()) }
}
