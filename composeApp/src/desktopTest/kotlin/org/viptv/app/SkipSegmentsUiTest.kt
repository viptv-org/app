package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableSharedFlow
import org.viptv.app.features.player.*
import org.viptv.app.player.PlayerCommand
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SkipSegmentsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(number: Long) = Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        metadata = CardMetadata(runtime_minutes = 52), provider_id = "addon:7", title = "Episode $number", badges = emptyList(),
        parent_identity = parent, metadata_origin = origin, description = "The crew regroups after the storm.")
    private val intro = TvSkipRange(30_000, 60_000, TvSkipKind.Intro)
    private val recap = TvSkipRange(0, 30_000, TvSkipKind.Recap)
    private val credits = TvSkipRange(1_290_000, 1_380_000, TvSkipKind.Credits)

    @Test fun phoneShowsTheSkipActionInsideItsSegmentAndHidesItAtTheEnd() {
        val player = Player(phone = true, skips = listOf(intro, credits), position = 20_000)
        try {
            player.frames()
            assertFalse(player.hasText("Skip intro"), "Before the segment there is nothing to skip")
            player.facts(PlayerFact.Progress(40_000, 1_800_000))
            assertTrue(player.hasText("Skip intro"))
            player.click("Skip intro"); player.frames()
            assertFalse(player.hasText("Skip intro"), "Skipping leaves the segment")
            player.facts(PlayerFact.Progress(60_000, 1_800_000))
            player.facts(PlayerFact.Progress(1_300_000, 1_800_000))
            assertTrue(player.hasText("Skip credits"))
            player.click("Skip credits"); player.frames()
            assertFalse(player.hasText("Skip credits"))
        } finally { player.close() }
    }

    @Test fun theCopyFollowsTheSegmentKind() {
        val player = Player(phone = true, skips = listOf(recap, intro), position = 10_000)
        try {
            player.frames()
            assertTrue(player.hasText("Skip recap"))
            assertFalse(player.hasText("Skip intro"))
            player.facts(PlayerFact.Progress(40_000, 1_800_000))
            assertTrue(player.hasText("Skip intro"))
            assertFalse(player.hasText("Skip recap"))
        } finally { player.close() }
    }

    @Test fun noSegmentsShowNothingAndLeavePlaybackAlone() {
        val player = Player(phone = true, skips = emptyList(), position = 40_000)
        try {
            player.frames()
            assertFalse(player.nodes().any { player.label(it).startsWith("Skip ") })
            assertFalse(player.hasText("The stream could not seek there."))
            assertTrue(player.nodes().any { player.label(it) == "Pause" || player.label(it) == "Play" })
        } finally { player.close() }
    }

    @Test fun tvHeldOkSeeksOnceAndKeepsThePlayPauseState() {
        val player = Player(television = true, skips = listOf(intro), position = 40_000)
        try {
            player.frames()
            assertTrue(player.hasText("Skip intro"))
            player.focus("Skip intro")
            player.keyDown(Key.Enter); player.keyDown(Key.Enter); player.keyDown(Key.Enter)
            player.keyUp(Key.Enter); player.frames()
            assertEquals(listOf<PlayerCommand>(PlayerCommand.Seek(60_000)), player.seeks, "Held OK seeks once per activation")
            assertFalse((player.seeks.single() as PlayerCommand.Seek).automatic, "A pressed Skip answers to the viewer")
            assertEquals(0, player.toggles, "Skipping never toggles playback")
        } finally { player.close() }
    }

    @Test fun tvAutoSkipFiresOnceAndNeverAgainForThatPlayback() {
        val player = Player(television = true, skips = listOf(intro), position = 20_000, skipIntros = true, paused = false)
        try {
            player.frames()
            player.facts(PlayerFact.Progress(31_000, 1_800_000))
            assertEquals(listOf<PlayerCommand>(PlayerCommand.Seek(60_000, automatic = true)), player.seeks, "Crossing the start skips once, on nobody's command")
            player.facts(PlayerFact.Progress(60_000, 1_800_000))
            player.facts(PlayerFact.Progress(31_500, 1_800_000))
            player.frames()
            assertEquals(1, player.seeks.size, "Seeking back into a skipped segment does not fire it again")
        } finally { player.close() }
    }

    @Test fun phoneSkipsAnIntroItRunsIntoAndUndoPlaysIt() {
        val player = Player(phone = true, skips = listOf(intro), position = 20_000, skipIntros = true, paused = false)
        try {
            player.facts(PlayerFact.Progress(30_500, 1_800_000))
            assertTrue(player.hasText("Skipped intro"), "The skip says what it did")
            assertFalse(player.hasText("Skip intro"))
            player.click("Undo")
            assertFalse(player.hasText("Skipped intro"))
            player.facts(PlayerFact.Progress(31_000, 1_800_000))
            assertTrue(player.hasText("Skip intro"), "Undo returns to the intro's start, which plays")
            assertFalse(player.hasText("Skipped intro"), "An undone intro does not skip again")
        } finally { player.close() }
    }

    @Test fun phoneSeekingIntoAnIntroPlaysIt() {
        val player = Player(phone = true, skips = listOf(intro), position = 20_000, skipIntros = true, paused = false)
        try {
            val forward = player.nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Forward 10 seconds") == true &&
                it.config.contains(SemanticsActions.OnClick) }
            assertTrue(forward.config[SemanticsActions.OnClick].action!!.invoke()); player.frames()
            player.facts(PlayerFact.Progress(31_000, 1_800_000))
            assertFalse(player.hasText("Skipped intro"))
            assertTrue(player.hasText("Skip intro"), "A seek into the intro plays it")
        } finally { player.close() }
    }

    @Test fun tvSkipsAnIntroItRunsIntoAndUndoSeeksBackToItsStart() {
        val player = Player(television = true, skips = listOf(intro), position = 20_000, skipIntros = true, paused = false)
        try {
            player.facts(PlayerFact.Progress(30_500, 1_800_000))
            assertTrue(player.hasText("Skipped intro"))
            player.facts(PlayerFact.Progress(60_000, 1_800_000))
            player.keyDown(Key.Enter); player.keyUp(Key.Enter); player.frames()
            assertEquals(listOf<PlayerCommand>(PlayerCommand.Seek(60_000, automatic = true), PlayerCommand.Seek(30_000)), player.seeks,
                "Undo has focus and seeks to the intro's start on the viewer's command")
            player.facts(PlayerFact.Progress(31_000, 1_800_000))
            assertEquals(2, player.seeks.size, "An undone intro plays")
            assertTrue(player.hasText("Skip intro"))
        } finally { player.close() }
    }

    @Test fun tvSeekingIntoAnIntroPlaysIt() {
        val player = Player(television = true, skips = listOf(intro), position = 20_000, skipIntros = true, paused = false)
        try {
            player.keyDown(Key.MediaFastForward); player.keyUp(Key.MediaFastForward); player.frames()
            player.facts(PlayerFact.Progress(31_000, 1_800_000))
            assertEquals(listOf<PlayerCommand>(PlayerCommand.Seek(30_000)), player.seeks, "A seek into the intro is never followed by a skip")
        } finally { player.close() }
    }

    @Test fun withThePreferenceOffNothingSkipsOnItsOwn() {
        val player = Player(television = true, skips = listOf(intro), position = 20_000, skipIntros = false, paused = false)
        try {
            player.frames()
            player.facts(PlayerFact.Progress(31_000, 1_800_000))
            assertTrue(player.seeks.isEmpty())
            assertTrue(player.hasText("Skip intro"), "The action stays available")
        } finally { player.close() }
    }

    @Test fun creditsAreNeverSkippedAutomatically() {
        val player = Player(television = true, skips = listOf(credits), position = 1_280_000, skipIntros = true, paused = false)
        try {
            player.frames()
            player.facts(PlayerFact.Progress(1_295_000, 1_800_000))
            assertTrue(player.seeks.isEmpty())
            assertTrue(player.hasText("Skip credits"))
        } finally { player.close() }
    }

    @Test fun phoneOpensTheNextEpisodePanelAtTheCreditsStart() {
        val played = mutableListOf<Card>()
        val player = Player(phone = true, skips = listOf(credits), position = 1_289_000, next = episode(25), play = { played += it })
        try {
            player.frames()
            assertFalse(player.hasText("Up next"), "Before the credits there is no panel")
            player.facts(PlayerFact.Progress(1_290_000, 1_800_000))
            player.frames()
            assertTrue(player.hasText("Up next"), "The panel opens during the credits")
            assertTrue(played.isEmpty(), "Opening the panel plays nothing")
        } finally { player.close() }
    }

    @Test fun tvOpensTheNextEpisodePanelAtTheCreditsStart() {
        val played = mutableListOf<Card>()
        val player = Player(television = true, skips = listOf(credits), position = 1_289_000, next = episode(25), play = { played += it })
        try {
            player.frames()
            assertFalse(player.hasText("Play next episode"))
            player.facts(PlayerFact.Progress(1_290_000, 1_800_000))
            player.frames()
            assertTrue(player.hasText("Play next episode"))
            assertTrue(played.isEmpty())
        } finally { player.close() }
    }

    private inner class Player(phone: Boolean = false, television: Boolean = false, val skips: List<TvSkipRange>, position: Long,
        next: Card? = null, skipIntros: Boolean = false, paused: Boolean = true, play: (Card) -> Unit = {}) {
        val seeks = mutableListOf<PlayerCommand>()
        var toggles = 0
        private val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 16)
        private var back: (() -> Unit)? = null
        private var time = 0L
        val scene = ImageComposeScene(if (television) 1920 else 844, if (television) 1080 else 390, Density(1f)) { ViptvTheme(television = television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                if (television) TvPlayerScreen(null, paused, null, { toggles++ }, {}, {}, episode(24),
                    TvPlaybackExtras(skips = skips, skipIntros = skipIntros, episodes = listOf(episode(24)), nextEpisode = next),
                    null, play, null, { back = it }, scene = TvPlayerScene.Skip, externalFacts = facts, previewCommand = { seeks += it },
                    playbackKey = "current")
                else PlayerScreen(null, paused, null, { toggles++ }, {}, {}, episode(24), phone = phone, canvasColor = Color.Transparent,
                    externalFacts = facts, tvExtras = TvPlaybackExtras(skips = skips, skipIntros = skipIntros, episodes = listOf(episode(24)),
                        nextEpisode = next), playEpisode = play, backChanged = { back = it }, playbackKey = "current",
                    controlInsets = WindowInsets(0, 0, 0, 0))
            }
        } }
        init {
            frames()
            facts.tryEmit(PlayerFact.Progress(position, 1_800_000))
            facts.tryEmit(PlayerFact.Status("Playing"))
            frames()
        }
        fun frames() { repeat(16) { time += 16_666_667L; scene.render(time).close() } }
        fun facts(vararg values: PlayerFact) { values.forEach { assertTrue(facts.tryEmit(it)) }; frames() }
        fun focus(value: String) { action(value).config[SemanticsActions.RequestFocus].action!!.invoke(); frames() }
        fun keyDown(key: Key) { scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)) }
        fun keyUp(key: Key) { scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp)) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        fun label(node: SemanticsNode) = text(node).ifEmpty { node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty() }
        fun hasText(value: String) = nodes().any { text(it) == value }
        fun action(value: String) = nodes().single { label(it) == value && it.config.contains(SemanticsActions.OnClick) }
        fun click(value: String) { assertTrue(action(value).config[SemanticsActions.OnClick].action!!.invoke()); frames() }
        fun close() = scene.close()
    }
}
