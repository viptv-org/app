package org.viptv.app

import org.junit.Rule
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import org.viptv.app.features.details.DesktopEpisodeRow
import org.viptv.app.features.details.DetailEpisodeCard
import org.viptv.app.features.details.TvEpisodeCard
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import kotlin.test.*

/**
 * The server marks the selected profile's finished episodes with a `Watched`
 * badge and part-way ones with `progress`. Phone, TV and web episode lists show
 * the AND-EPISODE-WATCHED-001 pill for the first and only the bar for the second.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class EpisodeWatchedStateUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun episode(number: Long, badges: List<String> = emptyList(), progress: Double? = null) = Card(
        MediaIdentity("tt38262097:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        provider_id = "addon:4", title = "Episode $number", badges = badges, progress = progress)
    private val episodes = listOf(episode(1, listOf("Watched")), episode(2, listOf("Watched")), episode(3, progress = 0.65), episode(4))

    private fun texts(scene: ImageComposeScene): List<String> = scene.semanticsOwners
        .flatMap { it.getAllSemanticsNodes(false) }
        .flatMap { node: SemanticsNode -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

    private fun render(scene: ImageComposeScene, name: String) {
        repeat(4) { scene.render(it * 16_666_667L).close() }
        val image = scene.render(100_000_000L)
        File("build/reports/episode-watched").apply { mkdirs() }.resolve(name)
            .writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
        image.close()
    }

    private fun assertWatchedPills(scene: ImageComposeScene, platform: String) {
        val all = texts(scene)
        assertEquals(2, all.count { it == "Watched" }, "$platform: E1 and E2 carry the pill, E3/E4 do not: $all")
    }

    @Test fun phoneAndWebEpisodeListsShowWatchedForFinishedEpisodesOnly() {
        val phone = ImageComposeScene(390, 760, Density(1f)) { ViptvTheme {
            Column(Modifier.fillMaxSize().background(Palette.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                episodes.forEach { DetailEpisodeCard(it, true, {}, Modifier.fillMaxWidth()) }
            }
        } }
        try { render(phone, "phone-episodes.png"); assertWatchedPills(phone, "phone") } finally { phone.close() }
        val web = ImageComposeScene(1000, 700, Density(1f)) { ViptvTheme {
            Column(Modifier.fillMaxSize().background(Palette.background)) {
                episodes.forEach { DesktopEpisodeRow(it, {}, browser = true) }
            }
        } }
        try { render(web, "web-episodes.png"); assertWatchedPills(web, "web") } finally { web.close() }
    }

    @Test fun tvEpisodeShelfShowsWatchedPillAndKeepsTheBarForThePartialEpisode() {
        val up = FocusRequester()
        val tv = ImageComposeScene(1920, 520, Density(1f)) { ViptvTheme(true) {
            Row(Modifier.fillMaxSize().background(Palette.background).padding(48.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                episodes.forEach { TvEpisodeCard(it, 0.95f, {}, {}, null, up) }
            }
        } }
        try {
            render(tv, "tv-episodes.png")
            assertWatchedPills(tv, "tv")
            assertTrue(texts(tv).none { it == "Simkl" })
        } finally { tv.close() }
    }
}
