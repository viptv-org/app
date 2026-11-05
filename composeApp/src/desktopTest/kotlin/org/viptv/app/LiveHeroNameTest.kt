package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.live.LiveChannel
import org.viptv.app.features.live.LivePreview
import org.viptv.app.features.live.LiveProgram
import org.viptv.app.features.live.TvLivePreview
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class LiveHeroNameTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun desktopHeroPrintsTheChannelNameOnceWhenNoProgrammeIsScheduled() {
        val scene = ImageComposeScene(1200, 800, Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                LivePreview(channel(), null, null, phone = false, television = false, scale = 1f, watch = {}, favourite = null)
            } }
        }
        try {
            assertEquals(1, occurrences(texts(scene), "LAT | Animal Planet HD"), "the hero names the channel in its eyebrow only: ${texts(scene)}")
        } finally { scene.close() }
    }

    @Test fun desktopHeroShowsTheProgrammeAsTheHeadline() {
        val scene = ImageComposeScene(1200, 800, Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                LivePreview(channel(), LiveProgram("p1", "Morning news", 0, 180), 30, phone = false, television = false, scale = 1f, watch = {}, favourite = null)
            } }
        }
        try {
            val texts = texts(scene)
            assertTrue(texts.any { it == "Morning news" }, "the programme title is the headline: $texts")
            assertEquals(1, occurrences(texts, "LAT | Animal Planet HD"), "the channel name stays in the eyebrow: $texts")
        } finally { scene.close() }
    }

    @Test fun tvHeroPrintsTheChannelNameOnceWhenNoProgrammeIsScheduled() {
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                TvLivePreview(channel(), null, null, 1f, favorite = {}, watch = {})
            } }
        }
        try {
            assertEquals(1, occurrences(texts(scene), "LAT | Animal Planet HD"), "the hero names the channel in its eyebrow only: ${texts(scene)}")
        } finally { scene.close() }
    }

    @Test fun tvHeroKeepsTheFavoriteAndArtworkInPlaceWhenTheHeadlineIsAbsent() {
        val noProgramme = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                TvLivePreview(channel(), null, null, 1f, favorite = {}, watch = {})
            } }
        }
        val withProgramme = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                TvLivePreview(channel(), LiveProgram("p1", "Morning news", 0, 180), 30, 1f, favorite = {}, watch = {})
            } }
        }
        try {
            assertEquals(bounds(noProgramme, "Favourite channel").top, bounds(withProgramme, "Favourite channel").top, 1f,
                "the favourite stays put when the headline is omitted")
            assertEquals(bounds(noProgramme, "OK to watch").top, bounds(withProgramme, "OK to watch").top, 1f,
                "the artwork stays put when the headline is omitted")
        } finally { noProgramme.close(); withProgramme.close() }
    }

    private fun channel() = LiveChannel(
        Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1", title = "LAT | Animal Planet HD", badges = emptyList()),
        id = "one", connectionId = "m3u:1")

    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render(it * 16_666_667L).close() }
    private fun occurrences(texts: List<String>, value: String) = texts.sumOf { it.split(value).size - 1 }
    private fun texts(scene: ImageComposeScene): List<String> { frames(scene); return nodes(scene).map(::text) }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun bounds(scene: ImageComposeScene, label: String) = nodes(scene).first { text(it) == label }.boundsInRoot
}