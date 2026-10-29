package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import org.jetbrains.skia.Bitmap
import org.viptv.app.features.player.*
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, DelicateCoilApi::class)
class PlayerEpisodePickerUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun still(number: Long) = "https://img.example/private-show/e$number-still.jpg"
    private fun poster(number: Long) = "https://img.example/private-show/e$number-poster.jpg"
    private fun episode(number: Long, badges: List<String> = emptyList(), progress: Double? = null,
        landscape: String? = still(number), poster: String? = null) =
        Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
            provider_id = "addon:7", title = "Episode $number", badges = badges, progress = progress, landscape = landscape,
            poster = poster, parent_identity = parent, metadata_origin = origin,
            description = "The crew regroups after the storm and plans the next crossing.")

    private val episodes = listOf(
        episode(1, listOf("Watched"), poster = poster(1)),
        episode(2, listOf("Watched"), landscape = null, poster = poster(2)),
        episode(3, progress = 0.45),
        episode(4, progress = 0.12).copy(subtitle = "Aired 12 Oct 2026 · 52m"),
        episode(5, landscape = null),
        episode(6))
    private val current = episodes[3]

    @Test fun phonePickerShowsArtworkWatchedProgressAndNowPlayingInsideTheSafeInsets() = withFakeArtwork { requested ->
        var played: Card? = null
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = current, phone = true,
                tvExtras = TvPlaybackExtras(episodes = episodes), playEpisode = { played = it },
                controlInsets = WindowInsets(right = 48.dp, bottom = 16.dp))
        } }
        val ui = Ui(scene)
        try {
            ui.frames(); ui.click("Episodes"); ui.frames()
            val now = ui.row(4)
            assertTrue("Now playing" in ui.text(now), ui.text(now))
            assertEquals(true, now.config.getOrNull(SemanticsProperties.Selected))
            assertEquals(true, now.config.getOrNull(SemanticsProperties.Focused), "the picker opens focused on the playing episode")
            assertEquals(0.12f, ui.progress(now)!!, 0.001f)
            ui.capture("phone-player-episodes-open")
            ui.scrollTo(0)
            for (number in 1L..2L) {
                val row = ui.row(number)
                assertTrue("Watched" in ui.text(row), "E$number is watched: ${ui.text(row)}")
                assertNull(ui.progress(row), "a watched episode has no bar")
                assertFalse("Now playing" in ui.text(row))
            }
            ui.row(3).let { row ->
                assertEquals(0.45f, ui.progress(row)!!, 0.001f)
                assertFalse("Watched" in ui.text(row))
            }
            ui.assertArtwork(ui.row(3), still(3))
            ui.scrollTo(1); ui.capture("phone-player-episodes")
            ui.assertRowsInside(right = 844 - 48, bottom = 390 - 16)
            ui.scrollTo(4)
            ui.row(5).let { row ->
                assertNull(ui.progress(row)); assertFalse("Watched" in ui.text(row)); assertFalse("Now playing" in ui.text(row))
            }
            assertTrue(still(1) in requested && poster(1) !in requested, "landscape is the episode still: $requested")
            assertTrue(poster(2) in requested, "a poster stands in when there is no still: $requested")
            ui.click(ui.text(ui.row(5)))
            assertEquals(episodes[4], played)
        } finally { scene.close() }
    }

    @Test fun portraitPhonePickerStacksTheCardsInsideTheSheet() = withFakeArtwork {
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = current, phone = true,
                tvExtras = TvPlaybackExtras(episodes = episodes), playEpisode = {})
        } }
        val ui = Ui(scene)
        try {
            ui.frames(); ui.click("Episodes"); ui.frames()
            assertTrue("Now playing" in ui.text(ui.row(4)))
            ui.capture("phone-player-episodes-portrait")
            ui.assertRowsInside(right = 390, bottom = 844)
        } finally { scene.close() }
    }

    @Test fun tvPickerUsesTheDetailsEpisodeCardsOpensOnThePlayingEpisodeAndKeepsCardsFocusable() = withFakeArtwork { requested ->
        var chosen: Card? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(television = true) {
            Box(Modifier.fillMaxSize().background(Color(0xFF2E7DB8)))
            TvPlayerEpisodePicker(episodes, 1f, current, { chosen = it }, {})
        } }
        val ui = Ui(scene)
        try {
            ui.frames(); ui.frames()
            val now = ui.row(4)
            assertTrue("Now playing" in ui.text(now), ui.text(now))
            assertEquals(true, now.config.getOrNull(SemanticsProperties.Focused), "the picker opens focused on the playing episode")
            assertEquals(0.12f, ui.progress(now)!!, 0.001f)
            ui.assertArtwork(now, still(4))
            assertTrue(now.boundsInWindow.top + now.size.height <= 1080, "the playing card with every line fits on screen")
            ui.focus(ui.row(5))
            assertEquals(true, ui.row(5).config.getOrNull(SemanticsProperties.Focused), "the next card takes focus")
            assertEquals(false, ui.row(4).config.getOrNull(SemanticsProperties.Focused))
            ui.focus(ui.row(4))
            assertEquals(true, ui.row(4).config.getOrNull(SemanticsProperties.Focused))
            ui.scrollTo(0); ui.frames()
            for (number in 1L..2L) {
                val row = ui.row(number)
                assertTrue("Watched" in ui.text(row), "E$number is watched: ${ui.text(row)}")
                assertNull(ui.progress(row))
            }
            assertEquals(0.45f, ui.progress(ui.row(3))!!, 0.001f)
            ui.capture("tv-player-episodes")
            assertTrue(still(1) in requested && poster(1) !in requested && poster(2) in requested, "$requested")
            ui.click(ui.text(ui.row(2)))
            assertEquals(episodes[1], chosen)
        } finally { scene.close() }
    }

    @Test fun desktopPickerShowsTheSameDetailsCardsAndPlaysTheChosenEpisode() = withFakeArtwork { requested ->
        var played: Card? = null
        val scene = ImageComposeScene(1280, 720, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = current, phone = false,
                tvExtras = TvPlaybackExtras(episodes = episodes), playEpisode = { played = it })
        } }
        val ui = Ui(scene)
        try {
            ui.frames(); ui.click("Episodes"); ui.frames()
            val now = ui.row(4)
            assertTrue("Now playing" in ui.text(now), ui.text(now))
            assertEquals(true, now.config.getOrNull(SemanticsProperties.Selected))
            assertEquals(0.12f, ui.progress(now)!!, 0.001f)
            ui.assertArtwork(now, still(4))
            ui.capture("desktop-player-episodes-open")
            ui.scrollTo(0)
            for (number in 1L..2L) {
                val row = ui.row(number)
                assertTrue("Watched" in ui.text(row), "E$number is watched: ${ui.text(row)}")
                assertNull(ui.progress(row))
            }
            assertEquals(0.45f, ui.progress(ui.row(3))!!, 0.001f)
            assertTrue(still(1) in requested && poster(1) !in requested, "landscape is the episode still: $requested")
            assertTrue(poster(2) in requested, "a poster stands in when there is no still: $requested")
            ui.scrollTo(4); ui.capture("desktop-player-episodes")
            ui.click(ui.text(ui.row(5)))
            assertEquals(episodes[4], played)
        } finally { scene.close() }
    }

    @Test fun desktopMovieAndLiveHaveNoEpisodesControl() {
        val movie = episode(1).copy(identity = MediaIdentity("film", "movie", emptyList()), parent_identity = null)
        val channel = episode(1).copy(identity = MediaIdentity("channel-7", "channel", emptyList()), parent_identity = null)
        listOf(movie, channel).forEach { title ->
            val scene = ImageComposeScene(1280, 720, Density(1f)) { ViptvTheme {
                PlayerScreen(null, true, null, {}, {}, {}, title = title, phone = false,
                    tvExtras = TvPlaybackExtras(episodes = episodes), playEpisode = {})
            } }
            val ui = Ui(scene)
            try {
                ui.frames()
                assertTrue(ui.nodes().none { ui.text(it) == "Episodes" }, "no Episodes control for ${title.identity.kind}")
            } finally { scene.close() }
        }
    }

    private class Ui(val scene: ImageComposeScene) {
        var time = 0L
        fun frames() = repeat(16) { time += 16_666_667L; scene.render(time).close(); Thread.sleep(4) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        fun clickable() = nodes().filter { it.config.contains(SemanticsActions.OnClick) }
        fun row(number: Long) = clickable().single { Regex("""(^| )$number\. Episode $number( |$)""").containsMatchIn(text(it)) }
        fun click(label: String) {
            assertTrue(clickable().single { text(it) == label }.config[SemanticsActions.OnClick].action!!.invoke(), label); frames()
        }
        fun progress(node: SemanticsNode) = flatten(node).firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current }
        fun scrollTo(index: Int) {
            val list = nodes().single { it.config.contains(SemanticsActions.ScrollToIndex) }
            assertTrue(list.config[SemanticsActions.ScrollToIndex].action!!.invoke(index)); frames()
        }
        fun focus(node: SemanticsNode) { assertTrue(node.config[SemanticsActions.RequestFocus].action!!.invoke()); frames() }
        fun assertArtwork(card: SemanticsNode, url: String) {
            val bounds = card.boundsInWindow
            val x = bounds.left.toInt() + 24; val y = bounds.top.toInt() + 24
            val color = scene.render(time).use { image -> Bitmap.makeFromImage(image).use { it.getColor(x, y) } }
            assertEquals(Integer.toHexString(artColor(url)), Integer.toHexString(color), "artwork at ($x, $y) of ${text(card)}")
        }
        fun assertRowsInside(right: Int, bottom: Int) {
            val rows = clickable().filter { Regex("""(^| )\d+\. Episode""").containsMatchIn(text(it)) }
            assertTrue(rows.isNotEmpty())
            rows.forEach { row ->
                val b = row.boundsInWindow
                assertTrue(b.left >= 0 && b.right <= right && b.bottom <= bottom, "${text(row)} at $b leaves the safe area ($right, $bottom)")
            }
        }
        fun capture(name: String) {
            val path = System.getenv("VIPTV_PLAYER_EPISODES_CAPTURE_DIR") ?: return
            frames(); File(path).mkdirs()
            scene.render(time).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        }
    }

    private companion object {
        fun artColor(url: String) = (0xFF shl 24) or (url.hashCode() and 0x7F7F7F) or 0x202020

        fun withFakeArtwork(block: (requested: Set<String>) -> Unit) {
            val requested = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            val context = PlatformContext.INSTANCE
            val previous = SingletonImageLoader.get(context)
            val loader = ImageLoader.Builder(context).components {
                add(Interceptor { chain ->
                    val url = chain.request.data.toString(); requested += url
                    val bitmap = Bitmap().apply { allocN32Pixels(160, 90); erase(artColor(url)) }
                    SuccessResult(bitmap.asImage(), chain.request, DataSource.MEMORY) as ImageResult
                })
            }.build()
            SingletonImageLoader.setUnsafe(loader)
            try { block(requested) } finally { loader.shutdown(); SingletonImageLoader.setUnsafe(previous) }
        }
    }
}
