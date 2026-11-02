package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.junit.Rule
import org.viptv.app.features.player.*
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class TimelineSegmentsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(number: Long) = Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        metadata = CardMetadata(runtime_minutes = 52), provider_id = "addon:7", title = "Episode $number", badges = emptyList(),
        parent_identity = parent, metadata_origin = origin, description = "The crew regroups after the storm.")
    private val intro = TvSkipRange(30_000, 60_000, TvSkipKind.Intro)
    private val recap = TvSkipRange(0, 30_000, TvSkipKind.Recap)
    private val credits = TvSkipRange(1_290_000, 1_380_000, TvSkipKind.Credits)

    @Test fun mobileTimelineMarksEachSegmentWithItsKindAndTimes() {
        val scene = ImageComposeScene(844, 64, Density(1f)) { ViptvTheme(false) {
            Surface(Modifier.fillMaxSize(), color = Palette.background) {
                PlaybackTimeline(40_000, 1_800_000, {}, skips = listOf(intro, credits))
            }
        } }
        try {
            frames(scene)
            val descriptions = descriptions(scene)
            assertTrue(descriptions.any { it.contains("Intro 0:30–1:00") }, "intro times are announced")
            assertTrue(descriptions.any { it.contains("Credits 21:30–23:00") }, "credits times are announced")
            assertTrue(pixels(scene).contains(Palette.segmentIntro.toArgb()), "intro is drawn in its own colour")
            assertTrue(pixels(scene).contains(Palette.segmentCredits.toArgb()), "credits are drawn in their own colour")
        } finally { scene.close() }
    }

    @Test fun noSegmentsDrawNoMarkersOnMobile() {
        val scene = ImageComposeScene(844, 64, Density(1f)) { ViptvTheme(false) {
            Surface(Modifier.fillMaxSize(), color = Palette.background) {
                PlaybackTimeline(40_000, 1_800_000, {}, skips = emptyList())
            }
        } }
        try {
            frames(scene)
            assertTrue(descriptions(scene).none { it.contains("Intro") || it.contains("Recap") || it.contains("Credits") })
            val pixels = pixels(scene)
            assertFalse(pixels.contains(Palette.segmentIntro.toArgb()))
            assertFalse(pixels.contains(Palette.segmentRecap.toArgb()))
            assertFalse(pixels.contains(Palette.segmentCredits.toArgb()))
        } finally { scene.close() }
    }

    @Test fun tvTimelineMarksEachSegmentWithItsKindAndTimes() {
        val player = TvPlayer(skips = listOf(recap, credits))
        try {
            player.frames()
            val descriptions = player.descriptions()
            assertTrue(descriptions.any { it.contains("Recap 0:00–0:30") }, "recap times are announced")
            assertTrue(descriptions.any { it.contains("Credits 21:30–23:00") }, "credits times are announced")
            val pixels = player.pixels()
            assertTrue(pixels.contains(Palette.segmentRecap.toArgb()), "recap is drawn in its own colour")
            assertTrue(pixels.contains(Palette.segmentCredits.toArgb()), "credits are drawn in their own colour")
        } finally { player.close() }
    }

    private var frame = 0L
    private fun frames(scene: ImageComposeScene) = repeat(16) { scene.render(frame++ * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun descriptions(scene: ImageComposeScene) = nodes(scene)
        .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() }
    private fun pixels(scene: ImageComposeScene): Set<Int> {
        val rendered = scene.render(frame++ * 16_666_667L)
        return collectedColors(rendered)
    }
    private fun collectedColors(image: Image): Set<Int> = image.use { rendered -> Bitmap.makeFromImage(rendered).use { bitmap ->
        val colors = HashSet<Int>()
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) colors.add(bitmap.getColor(x, y))
        colors
    } }

    private inner class TvPlayer(val skips: List<TvSkipRange>) {
        private val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 16)
        private var time = 0L
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(television = true) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                TvPlayerScreen(null, true, null, {}, {}, {}, episode(24),
                    TvPlaybackExtras(skips = skips, episodes = listOf(episode(24))),
                    null, null, null, { }, scene = TvPlayerScene.Controls, externalFacts = facts, playbackKey = "current")
            }
        } }
        init { frames(); facts.tryEmit(PlayerFact.Progress(0, 1_800_000)); facts.tryEmit(PlayerFact.Status("Playing")); frames() }
        fun frames() = repeat(16) { time += 16_666_667L; scene.render(time).close(); Thread.sleep(4) }
        fun descriptions() = nodes(scene).mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() }
        fun pixels(): Set<Int> { time += 16_666_667L; return collectedColors(scene.render(time)) }
        fun close() = scene.close()
    }
}