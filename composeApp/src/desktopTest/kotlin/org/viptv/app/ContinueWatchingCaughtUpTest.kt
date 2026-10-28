package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import org.viptv.app.components.caughtUpCaption
import org.viptv.app.components.isCaughtUp
import org.viptv.app.features.continuewatching.ContinueWatchingPage
import org.viptv.app.features.continuewatching.TvContinueWatchingScreen
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.home.MediaCard
import org.viptv.app.features.home.TvHomeShelf
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.junit.Rule
import kotlin.test.*

/**
 * A Continue Watching entry for a show the viewer is caught up on: the last episode they finished,
 * which the queue keeps and marks watched while no later episode is released (app#132, backend#61).
 * The card is labelled with that episode and never opens the source picker for an episode that is
 * already watched: it opens the show's Details, whose episode list lands on the finished one.
 *
 * The next episode's line ("S3 E5 coming soon", or its air date) needs the server's next-episode
 * facts, which it does not send yet (backend#78), so a caught-up card carries one label here.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ContinueWatchingCaughtUpTest {
    @get:Rule val uiThread = UiThreadRule()
    private val origin = MetadataOrigin("stremio", "addon:4", "c".repeat(32))
    private val series = Card(MediaIdentity("tt38262097", "series", listOf(ExternalId("imdb", "tt38262097"))),
        provider_id = "addon:4", title = "Level 999 no Murabito", badges = emptyList(), metadata_origin = origin)
    private fun episode(number: Long) = series.copy(
        identity = MediaIdentity("tt38262097:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        parent_identity = series.identity, title = "Episode $number")
    /** The finale the owner finished last: no later episode is released, so the queue keeps it. */
    private val finale = episode(12).copy(title = series.title, badges = listOf("Watched"))
    /** Still in the middle of S1 E3: a normal resume card. */
    private val resume = episode(3).copy(title = series.title, subtitle = "S1 E3 · 9m left", progress = 0.6581)
    private val home = HomeSnapshot(
        scope = Scope("viptv:remote", "account", "1", 1), revision = 1,
        rows = listOf(HomeRow(id = "continue", title = "Continue Watching", provider_id = "library",
            state = RowState.READY, cards = listOf(finale, resume))),
        complete = true)

    /** Home with one Continue Watching row holding just [cards], so every card composes at any width. */
    private fun rowOf(vararg cards: Card) = home.copy(rows = listOf(home.rows.single().copy(cards = cards.toList())))

    @Test fun theFinishedEpisodeOfACaughtUpShowIsCaughtUpAndLabelled() {
        assertTrue(finale.isCaughtUp(), "a watched finale with no caption and no progress is caught up")
        assertEquals("S1 E12 · Watched", finale.caughtUpCaption())
        // The up-next case the server already advances (backend#61) names the next episode.
        assertFalse(finale.copy(subtitle = "S1 E12 · Chapter 12").isCaughtUp())
        assertNull(finale.copy(subtitle = "S1 E12 · Chapter 12").caughtUpCaption())
        // A resume card keeps its progress and its caption.
        assertFalse(resume.isCaughtUp())
        assertNull(resume.caughtUpCaption())
        // Only a series episode can be waiting for the next one: a finished movie is not.
        val movie = Card(MediaIdentity("tt0111161", "movie", emptyList()), provider_id = "addon:4",
            title = "A Movie", badges = listOf("Watched"))
        assertFalse(movie.isCaughtUp())
        assertNull(movie.caughtUpCaption())
        // An episode with no season still names the episode.
        val unnumbered = finale.copy(identity = finale.identity.copy(episode = EpisodeCoordinates("aired", null, 7)))
        assertTrue(unnumbered.isCaughtUp())
        assertEquals("E7 · Watched", unnumbered.caughtUpCaption())
    }

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun label(node: SemanticsNode) = (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")

    /** The card's own node: the boards merge a card's art, chip, check and captions into one. */
    private fun tap(scene: ImageComposeScene, card: Card) {
        val node = nodes(scene).first { it.config.contains(SemanticsActions.OnClick) && label(it).contains(card.title) }
        assertTrue(node.config[SemanticsActions.OnClick].action?.invoke() == true, "${card.title} is not activatable")
    }

    @Test fun phoneDesktopAndWebCatchUpCardsAreLabelledAndOpenDetails() = onEdt {
        for ((width, height) in listOf(390 to 900, 1280 to 900)) {
            // A caught-up card: labelled with the episode it finished, and it opens the show's Details.
            val opened = mutableListOf<Card>(); val played = mutableListOf<Card>()
            val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    HomeScreen(rowOf(finale), television = false, retry = {}, open = { opened += it },
                        play = { played += it }, busy = false)
                }
            } }
            try {
                repeat(6) { scene.render(it * 16_666_667L).close() }
                assertTrue(nodes(scene).any { label(it).contains("S1 E12 · Watched") },
                    "${width}dp: the caught-up card is labelled with the episode it finished")
                tap(scene, finale)
                assertEquals(listOf(finale), opened, "${width}dp: a caught-up card opens the show's Details")
                assertTrue(played.isEmpty(), "${width}dp: nothing plays the watched finale: $played")
            } finally { scene.close() }
            // The resume case is unchanged: the tap starts the episode the card names.
            val resumed = mutableListOf<Card>(); val resumedOpen = mutableListOf<Card>()
            val resumeScene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    HomeScreen(rowOf(resume), television = false, retry = {}, open = { resumedOpen += it },
                        play = { resumed += it }, busy = false)
                }
            } }
            try {
                repeat(6) { resumeScene.render(it * 16_666_667L).close() }
                tap(resumeScene, resume)
                assertEquals(listOf(resume), resumed, "${width}dp: a resume card still starts its episode")
                assertTrue(resumedOpen.isEmpty(), "${width}dp: $resumedOpen")
            } finally { resumeScene.close() }
        }
    }

    @Test fun tvCatchUpOkOpensDetailsAndAResumeCardStillPlays() = onEdt {
        for (card in listOf(finale, resume)) {
            val opened = mutableListOf<Card>(); val played = mutableListOf<Card>()
            val first = FocusRequester()
            val scene = ImageComposeScene(1920, 900, Density(1f)) { ViptvTheme(true) {
                Column(Modifier.fillMaxSize().background(Palette.background)) {
                    TvHomeShelf(rowOf(card).rows.single(), 1f, first, up = null, focus = {}, open = { opened += it },
                        play = { played += it })
                }
            } }
            var frame = 0L
            fun render() = repeat(8) { scene.render(frame++ * 16_666_667L).close() }
            fun ok() {
                scene.sendKeyEvent(KeyEvent(Key.DirectionCenter, KeyEventType.KeyDown))
                scene.sendKeyEvent(KeyEvent(Key.DirectionCenter, KeyEventType.KeyUp))
            }
            try {
                render(); delay(30)
                first.requestFocus(); render(); ok(); render()
                if (card === finale) {
                    assertEquals(listOf(finale), opened, "D-pad OK on the caught-up card opens the show's Details")
                    assertTrue(played.isEmpty(), "the watched finale is not fetched again: $played")
                } else {
                    assertEquals(listOf(resume), played, "a resume card keeps opening its sources")
                    assertTrue(opened.isEmpty(), "$opened")
                }
            } finally { scene.close() }
        }
    }

    @Test fun tvContinueWatchingPageRoutesACaughtUpCardToDetails() = onEdt {
        val opened = mutableListOf<Card>(); val played = mutableListOf<Card>()
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            TvContinueWatchingScreen(ContinueWatchingPage(listOf(finale, resume), loaded = true),
                play = { played += it }, open = { opened += it }, more = null, retry = {}, home = {})
        } }
        fun render() = repeat(10) { scene.render(it * 16_666_667L).close() }
        try {
            render(); delay(30); render()
            scene.sendKeyEvent(KeyEvent(Key.DirectionCenter, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(Key.DirectionCenter, KeyEventType.KeyUp))
            render()
            assertEquals(listOf(finale), opened, "the full TV list routes a caught-up card to Details too")
            assertTrue(played.isEmpty(), "$played")
        } finally { scene.close() }
    }

    /** The card drawn on its own: the caught-up label is the line under its title. */
    @Test fun theCardDrawsTheCaughtUpLabelUnderItsTitle() = onEdt {
        val scene = ImageComposeScene(300, 260, Density(1f)) { ViptvTheme {
            MediaCard(finale, 240, open = {}, select = {})
        } }
        try {
            repeat(6) { scene.render(it * 16_666_667L).close() }
            assertTrue(nodes(scene).any { label(it).contains("S1 E12 · Watched") },
                "the label is drawn: ${nodes(scene).map(::label)}")
        } finally { scene.close() }
    }
}