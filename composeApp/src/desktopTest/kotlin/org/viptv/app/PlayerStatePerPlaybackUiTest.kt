package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableSharedFlow
import org.viptv.app.features.player.*
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class PlayerStatePerPlaybackUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(number: Long) = Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        metadata = CardMetadata(runtime_minutes = 30), provider_id = "addon:7", title = "Episode $number", badges = emptyList(),
        parent_identity = parent, metadata_origin = origin)
    private val credits = listOf(TvSkipRange(1_700_000, 1_790_000, TvSkipKind.Credits))

    @Test fun phoneNextFromTheCreditsOpensTheNextEpisodeWithoutAnUpNextPanel() = nextFromCredits(phone = true, panel = "Up next")

    @Test fun tvNextFromTheCreditsOpensTheNextEpisodeWithoutAnUpNextPanel() = nextFromCredits(television = true, panel = "Play next episode")

    private fun nextFromCredits(phone: Boolean = false, television: Boolean = false, panel: String) {
        val player = Player(phone = phone, television = television)
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Progress(1_750_000, 1_800_000)); player.frames(40)
            assertTrue(player.hasText(panel)); assertTrue(player.hasText("2. Episode 2"))
            player.capture("1-e1-credits")
            player.click(if (phone) "Next episode" else "Play next episode")
            assertEquals(listOf(2L), player.played)
            player.frames(40); player.capture("2-e2-opening")
            assertFalse(player.hasText(panel), "Episode 2 opens clean")
            assertFalse(player.hasText("3. Episode 3"))
            player.segments = credits
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Progress(5_000, 1_800_000)); player.frames(40)
            player.capture("3-e2-first-frames")
            assertFalse(player.hasText(panel), "Nothing appears before episode 2's own credits")
            player.facts(PlayerFact.Progress(1_750_000, 1_800_000)); player.frames(40)
            assertTrue(player.hasText(panel)); assertTrue(player.hasText("3. Episode 3"))
        } finally { player.close() }
    }

    @Test fun phoneAutoAdvanceAtTheEndOpensTheNextEpisodeClean() {
        val player = Player(phone = true, autoplay = true)
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended"))
            assertTrue(player.hasText("Up next"))
            player.seconds(10.5)
            assertEquals(listOf(2L), player.played)
            player.seconds(2.0)
            assertFalse(player.hasText("Up next"))
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Progress(1_000, 1_800_000)); player.frames(40)
            assertFalse(player.hasText("Up next"))
        } finally { player.close() }
    }

    @Test fun tvAutoAdvanceAtTheEndOpensTheNextEpisodeClean() {
        val player = Player(television = true, autoplay = true)
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.frames(60)
            assertTrue(player.nodes().any { player.text(it).startsWith("Next episode in ") })
            player.seconds(10.0)
            assertEquals(listOf(2L), player.played)
            player.seconds(2.0); player.frames(80)
            assertFalse(player.hasText("Play next episode")); assertFalse(player.hasText("3. Episode 3"))
        } finally { player.close() }
    }

    @Test fun choosingAnEpisodeFromTheEpisodesPanelOpensItWithoutAnUpNextPanel() {
        val player = Player(phone = true)
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Progress(1_750_000, 1_800_000)); player.frames(40)
            player.click("Watch credits"); player.frames(40)
            player.click("Episodes"); player.frames(60); player.click("3. Episode 3")
            assertEquals(listOf(3L), player.played)
            player.frames(40)
            assertFalse(player.hasText("Up next"))
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Progress(2_000, 1_800_000)); player.frames(40)
            assertFalse(player.hasText("Up next"))
        } finally { player.close() }
    }

    @Test fun phoneSeekReopenOfTheSameEpisodeKeepsTheDismissedPanelAndTimeline() = seekReopen(phone = true, panel = "Up next")

    @Test fun tvSeekReopenOfTheSameEpisodeKeepsTheDismissedPanelAndTimeline() = seekReopen(television = true, panel = "Play next episode")

    private fun seekReopen(phone: Boolean = false, television: Boolean = false, panel: String) {
        val player = Player(phone = phone, television = television)
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Progress(1_750_000, 1_800_000)); player.frames(40)
            assertTrue(player.hasText(panel))
            if (phone) player.click("Watch credits") else player.back()
            player.frames(40); assertFalse(player.hasText(panel))
            player.segments = emptyList(); player.frames(20)
            player.segments = credits
            player.facts(PlayerFact.Progress(1_760_000, 1_800_000)); player.frames(60)
            assertFalse(player.hasText(panel), "A seek-reopen of the same episode keeps the dismissed panel")
            assertTrue(player.played.isEmpty())
        } finally { player.close() }
    }

    @Test fun phoneCreditsAtTheSeededPositionWaitForAProgressFact() = progressGate(phone = true, panel = "Up next")

    @Test fun tvCreditsAtTheSeededPositionWaitForAProgressFact() = progressGate(television = true, panel = "Play next episode")

    private fun progressGate(phone: Boolean = false, television: Boolean = false, panel: String) {
        val player = Player(phone = phone, television = television, initialSegments = listOf(TvSkipRange(0, 1_790_000, TvSkipKind.Credits)))
        try {
            player.frames(60)
            assertFalse(player.hasText(panel), "No progress from this playback yet")
            player.facts(PlayerFact.Progress(1_000, null)); player.frames(40)
            assertTrue(player.hasText(panel))
        } finally { player.close() }
    }

    @Test fun phoneDismissedUpNextStaysClosedForTheRestOfTheCredits() = dismissalSticks(phone = true, panel = "Up next")

    @Test fun tvDismissedUpNextStaysClosedForTheRestOfTheCredits() = dismissalSticks(television = true, panel = "Play next episode")

    private fun dismissalSticks(phone: Boolean = false, television: Boolean = false, panel: String) {
        val player = Player(phone = phone, television = television)
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Progress(1_750_000, 1_800_000)); player.frames(40)
            assertTrue(player.hasText(panel)); player.capture("dismiss-1-credits-panel")
            player.dismiss(); player.frames(40)
            assertFalse(player.hasText(panel)); player.capture("dismiss-2-watching-credits")
            player.facts(PlayerFact.Status("Paused"), PlayerFact.Status("Playing"), PlayerFact.Progress(1_755_000, 1_800_000)); player.frames(40)
            assertFalse(player.hasText(panel), "Pause and resume keep it dismissed")
            player.facts(PlayerFact.Status("Buffering"), PlayerFact.Status("Loading"), PlayerFact.Status("Playing"), PlayerFact.Progress(1_760_000, 1_800_000)); player.frames(40)
            assertFalse(player.hasText(panel), "Buffering and a seek-reopen keep it dismissed")
            player.seek(0.98f); player.facts(PlayerFact.Status("Buffering"), PlayerFact.Status("Playing"), PlayerFact.Progress(1_764_000, 1_800_000)); player.frames(40)
            assertFalse(player.hasText(panel), "A seek within the credits keeps it dismissed")
            player.capture("dismiss-3-after-pause-buffer-seek")
            assertTrue(player.played.isEmpty())
            player.facts(PlayerFact.Status("Ended")); player.frames(60)
            assertTrue(player.hasText(panel), "The real end offers the next episode again")
            player.capture("dismiss-4-ended")
        } finally { player.close() }
    }

    @Test fun phoneSeekingBackBeforeTheCreditsOffersUpNextAgainAtTheCredits() = seekBackRearms(phone = true, panel = "Up next")

    @Test fun tvSeekingBackBeforeTheCreditsOffersUpNextAgainAtTheCredits() = seekBackRearms(television = true, panel = "Play next episode")

    private fun seekBackRearms(phone: Boolean = false, television: Boolean = false, panel: String) {
        val player = Player(phone = phone, television = television)
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Progress(1_750_000, 1_800_000)); player.frames(40)
            player.dismiss(); player.frames(40)
            player.seek(0.5f); player.facts(PlayerFact.Status("Buffering"), PlayerFact.Status("Playing"), PlayerFact.Progress(900_000, 1_800_000)); player.frames(40)
            assertFalse(player.hasText(panel))
            player.facts(PlayerFact.Progress(1_701_000, 1_800_000)); player.frames(40)
            assertTrue(player.hasText(panel), "Reaching the credits again offers the next episode")
        } finally { player.close() }
    }

    @Test fun phoneDismissingAtTheEndKeepsItClosedUntilPlaybackResumes() = dismissAtEnd(phone = true, panel = "Up next")

    @Test fun tvDismissingAtTheEndKeepsItClosedUntilPlaybackResumes() = dismissAtEnd(television = true, panel = "Play next episode")

    private fun dismissAtEnd(phone: Boolean = false, television: Boolean = false, panel: String) {
        val player = Player(phone = phone, television = television, initialSegments = emptyList())
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Progress(1_799_000, 1_800_000), PlayerFact.Status("Ended")); player.frames(40)
            assertTrue(player.hasText(panel))
            player.dismiss(); player.frames(40)
            player.facts(PlayerFact.Status("Ended"), PlayerFact.Progress(1_800_000, 1_800_000)); player.frames(40)
            assertFalse(player.hasText(panel), "A repeated end status keeps it dismissed")
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Progress(1_790_000, 1_800_000), PlayerFact.Status("Ended")); player.frames(40)
            assertTrue(player.hasText(panel), "A new end after playback resumes offers it again")
            assertTrue(player.played.isEmpty())
        } finally { player.close() }
    }

    private inner class Player(phone: Boolean = false, val television: Boolean = false, autoplay: Boolean = false,
        initialSegments: List<TvSkipRange> = credits) {
        val width = if (television) 1920 else 844
        val height = if (television) 1080 else 390
        private var number by mutableStateOf(1L)
        var segments by mutableStateOf(initialSegments)
        val played = mutableListOf<Long>()
        private val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 16)
        private var back: (() -> Unit)? = null
        private var time = 0L
        private val name = buildString { append(if (television) "tv" else if (phone) "phone" else "desktop") }
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television = television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                val title = episode(number)
                PlayerScreen(null, true, {}, {}, {}, title = title, phone = phone, television = television,
                    canvasColor = Color.Transparent, externalFacts = facts,
                    tvExtras = TvPlaybackExtras(episodes = (1L..4L).map(::episode), nextEpisode = episode(number + 1), skips = segments),
                    playEpisode = { card ->
                        played += card.identity.episode!!.episode
                        number = card.identity.episode!!.episode
                        segments = emptyList()
                    }, backChanged = { back = it }, playbackKey = "episode-$number", autoplayNext = autoplay)
            }
        } }
        fun frames(count: Int = 16) = repeat(count) { time += 16_666_667L; scene.render(time).close() }
        fun seconds(value: Double) {
            time += (value * 1_000_000_000).toLong()
            repeat(4) { time += 1_000_000; scene.render(time).close() }
        }
        fun facts(vararg values: PlayerFact) { values.forEach { assertTrue(facts.tryEmit(it)); frames() }; frames() }
        fun back() { back!!.invoke() }
        fun dismiss() { if (television) back() else click("Watch credits") }
        fun seek(fraction: Float) {
            if (television) { assertTrue(scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyDown))); scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyUp)); frames(40) }
            val slider = nodes().first { label(it).startsWith(if (television) "Position" else "Playback position") && it.config.contains(SemanticsActions.SetProgress) }
            assertTrue(slider.config[SemanticsActions.SetProgress].action!!.invoke(fraction)); frames()
        }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        fun label(node: SemanticsNode) = text(node).ifEmpty { node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty() }
        fun hasText(value: String) = nodes().any { text(it) == value }
        fun click(value: String) {
            val action = nodes().filter { label(it) == value && it.config.contains(SemanticsActions.OnClick) }.maxBy { it.boundsInWindow.top }
            assertTrue(action.config[SemanticsActions.OnClick].action!!.invoke()); frames()
        }
        fun capture(step: String) {
            val path = System.getenv("VIPTV_PLAYBACK_SEQUENCE_DIR") ?: return
            File(path).mkdirs()
            scene.render(time).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { File(path, "$name-$step.png").writeBytes(it.bytes) } }
        }
        fun close() = scene.close()
    }
}
