package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.live.LiveChannel
import org.viptv.app.features.live.LiveChannelRow
import org.viptv.app.features.live.LivePreview
import org.viptv.app.features.live.liveChannelMonogram
import org.viptv.app.images.ConfigureImageLoading
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class LiveChannelEmblemTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun monogramUsesShortInitialsOfTheChannelNotItsProvidersPrefix() {
        assertEquals("DC", liveChannelMonogram("LAT | Discovery Channel HD"))
        assertEquals("AP", liveChannelMonogram("LAT | Animal Planet UHD (MX)"))
        assertEquals("DE", liveChannelMonogram("LAT | Discovery en Español (MX) UHD"))
        assertEquals("DF", liveChannelMonogram("Discovery Familia HD"))
        assertEquals("FN", liveChannelMonogram("Fixture news"))
        assertEquals("DISC", liveChannelMonogram("Discovery"))
        assertEquals("ESPN", liveChannelMonogram("ESPN HD"))
        assertNull(liveChannelMonogram("LAT | "))
        assertNull(liveChannelMonogram(""))
    }

    @Test fun theRowPrintsTheChannelNameOnceAndFallsBackToItsMonogram() {
        val channel = LiveChannel(channel(title = "LAT | Discovery Channel HD"), id = "one", connectionId = "m3u:1")
        val scene = row(channel)
        try {
            val texts = texts(scene)
            assertEquals(1, occurrences(texts, "LAT | Discovery Channel HD"), "the name is the row's own label once: $texts")
            assertTrue(texts.any { it.split(" ").contains("DC") }, "the emblem falls back to initials: $texts")
        } finally { scene.close() }
    }

    @Test fun theEmblemShowsTheChannelLogoInsteadOfAnyTextWhenItHasOne() {
        val channel = LiveChannel(channel(title = "LAT | Discovery Channel HD", landscape = "https://art.invalid/logo.png"), id = "one", connectionId = "m3u:1")
        val scene = row(channel)
        try {
            val texts = texts(scene)
            assertFalse(texts.any { it.split(" ").contains("DC") }, "no monogram over a channel logo: $texts")
        } finally { scene.close() }
    }

    @Test fun thePhoneHeroPrintsTheChannelNameOnceWhenNoProgrammeIsScheduled() {
        val channel = LiveChannel(channel(title = "LAT | Animal Planet HD"), id = "one", connectionId = "m3u:1")
        val scene = ImageComposeScene(390, 400, Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                ConfigureImageLoading()
                LivePreview(channel, null, null, phone = true, television = false, scale = 1f, watch = {}, favourite = null)
            } }
        }
        try {
            val texts = texts(scene)
            assertEquals(1, occurrences(texts, "LAT | Animal Planet HD"), "the hero names the channel in its eyebrow only: $texts")
        } finally { scene.close() }
    }

    private fun row(channel: LiveChannel) = ImageComposeScene(400, 120, Density(1f)) {
        ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
            ConfigureImageLoading()
            LiveChannelRow(channel, null, {}, null)
        } }
    }

    private fun channel(title: String, landscape: String? = null) =
        Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1", title = title, badges = emptyList(), landscape = landscape)

    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(3) }
    private fun occurrences(texts: List<String>, value: String) = texts.sumOf { it.split(value).size - 1 }
    private fun texts(scene: ImageComposeScene): List<String> { frames(scene); return nodes(scene).map(::text) }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}