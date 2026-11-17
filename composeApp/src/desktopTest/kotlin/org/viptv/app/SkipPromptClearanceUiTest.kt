package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.viptv.app.features.player.*
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SkipPromptClearanceUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val intro = TvSkipRange(30_000, 60_000, TvSkipKind.Intro)
    private val card = Card(MediaIdentity("private-24", "series", emptyList(), EpisodeCoordinates("aired", 1, 24)),
        metadata = CardMetadata(runtime_minutes = 24), provider_id = "addon:7", title = "Episode 24", badges = emptyList(),
        parent_identity = MediaIdentity("private-show", "series", emptyList()),
        metadata_origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32)))
    private val layouts = listOf(Layout("phone portrait", 390, 844, true), Layout("phone landscape", 844, 390, true),
        Layout("small phone portrait", 320, 640, true), Layout("tablet", 1024, 768, false),
        Layout("desktop 1280", 1280, 800, false), Layout("desktop 1440", 1440, 900, false))
    private val timeLabel = Regex("^-?\\d+:\\d\\d(:\\d\\d)?$")

    @Test fun theSkipPromptAndTheSkippedNoticeNeverCoverTheTimeline() {
        for (layout in layouts) for (crossing in listOf(false, true)) {
            val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 16)
            var time = 0L
            val scene = ImageComposeScene(layout.width, layout.height, Density(1f)) { ViptvTheme {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    PlayerScreen(null, false, null, {}, {}, {}, card, phone = layout.phone, canvasColor = Color.Transparent, externalFacts = facts,
                        tvExtras = TvPlaybackExtras(skips = listOf(intro), skipIntros = crossing, episodes = listOf(card)),
                        playbackKey = "k", controlInsets = WindowInsets(0, 0, 0, 0))
                }
            } }
            try {
                fun frames() = repeat(16) { time += 16_666_667L; scene.render(time).close() }
                fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
                frames()
                facts.tryEmit(PlayerFact.Progress(if (crossing) 20_000 else 40_000, 1_440_000)); facts.tryEmit(PlayerFact.Status("Playing")); frames()
                if (crossing) { facts.tryEmit(PlayerFact.Progress(30_500, 1_440_000)); frames(); facts.tryEmit(PlayerFact.Progress(60_000, 1_440_000)); frames() }
                val what = "${layout.name}, ${if (crossing) "skipped notice" else "skip prompt"}"
                val prompt = nodes().filter { node -> text(node).let { it == "Skip intro" || it == "Skipped intro" || it == "Undo" } }
                assertTrue(prompt.isNotEmpty(), "$what is shown")
                val timeline = nodes().filter { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith("Playback position") } == true ||
                        timeLabel.matches(text(node)) }
                assertTrue(timeline.size >= 2, "$what: the timeline and its time labels are shown")
                for (p in prompt) for (t in timeline) {
                    assertFalse(p.boundsInRoot.overlaps(t.boundsInRoot),
                        "$what: '${text(p)}' ${p.boundsInRoot} covers '${text(t).ifEmpty { "timeline" }}' ${t.boundsInRoot}")
                }
            } finally { scene.close() }
        }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun Rect.overlaps(other: Rect) = left < other.right && other.left < right && top < other.bottom && other.top < bottom
    private data class Layout(val name: String, val width: Int, val height: Int, val phone: Boolean)
}
