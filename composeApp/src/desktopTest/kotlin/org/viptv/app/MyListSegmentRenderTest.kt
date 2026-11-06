package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import coil3.annotation.DelicateCoilApi
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import org.junit.Rule
import org.viptv.app.connection.defaultLibraryQuery
import org.viptv.app.features.continuewatching.ContinueWatchingPage
import org.viptv.app.features.library.ContinueWatchingSection
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.features.library.MyListSegment
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Availability
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.Choice
import org.viptv.engine.wire.ControlKind
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.LibrarySnapshot
import org.viptv.engine.wire.LibrarySource
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.QueryControl
import org.viptv.engine.wire.Scope

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, DelicateCoilApi::class)
class MyListSegmentRenderTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val dir = run {
        val path = System.getenv("VIPTV_MYLIST_SEGMENT_CAPTURE_DIR") ?: return@run null
        File(path).apply { mkdirs() }
    }

    private val art = File("/tmp/app-1049-art").apply { mkdirs() }

    private fun art(name: String, width: Int, height: Int, from: Color, to: Color): String {
        val file = File(art, "$name.png")
        if (!file.exists()) {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()
            g.paint = GradientPaint(0f, 0f, from, width.toFloat(), height.toFloat(), to)
            g.fillRect(0, 0, width, height)
            g.dispose()
            ImageIO.write(image, "png", file)
        }
        return file.toURI().toString()
    }

    private val titles = listOf("Blue Hour", "The Long Road", "Salt & Static", "Night Shift", "Paper Lanterns",
        "Second Wind", "The Quiet Coast", "Golden Hour", "Harbour Lights", "Sundown")

    private fun libraryCard(index: Int) = Card(
        MediaIdentity("lib-$index", if (index % 2 == 0) "movie" else "series", emptyList()), provider_id = "library",
        title = titles[index % titles.size],
        badges = emptyList(), added_at_millis = 1_700_000_000_000L - index * 86_400_000L,
        poster = art("poster-$index", 400, 600, Color(0x2C3E70 + index * 0x0A0A0A), Color(0x8E5A2C + index * 0x070505)))

    private fun continueCard(index: Int) = Card(
        MediaIdentity("tt%07d".format(index), "series", emptyList(), EpisodeCoordinates("aired", 1L, index + 1L)),
        provider_id = "addon:4", title = titles[index % titles.size], subtitle = "S1 E${index + 1} · ${20 - index} min left",
        progress = 0.42, badges = emptyList(),
        landscape = art("still-$index", 640, 360, Color(0x1F3B4D + index * 0x080606), Color(0xB03A2E + index * 0x050606)),
        poster = art("cposter-$index", 400, 600, Color(0x223355), Color(0x997744)))

    private val snapshot = LibrarySnapshot(Scope("t", "t", "t", 1), 1, defaultLibraryQuery(),
        (0 until 10).map(::libraryCard), complete = true,
        controls = listOf(
            QueryControl("source", "List", ControlKind.SINGLECHOICE, false, listOf(Choice("favorites", "My List"), Choice("trakt", "Trakt watchlist"))),
            QueryControl("sort", "Sort by", ControlKind.SINGLECHOICE, false, listOf(Choice("last_watched", "Last watched"),
                Choice("date_added", "Date added"), Choice("name_asc", "Name A–Z"))),
            QueryControl("type", "Type", ControlKind.SINGLECHOICE, false, listOf(Choice("all", "All"), Choice("movie", "Movies"), Choice("series", "Series"))),
            QueryControl("status", "Status", ControlKind.SINGLECHOICE, false, listOf(Choice("", "Any"), Choice("watching", "Watching"))),
            QueryControl("genre", "Genre", ControlKind.SINGLECHOICE, false, listOf(Choice("", "Any"), Choice("drama", "Drama")))),
        sources = listOf(LibrarySource("favorites", "My List", Availability.AVAILABLE), LibrarySource("trakt", "Trakt watchlist", Availability.AVAILABLE)),
        availability = Availability.AVAILABLE)

    private fun capture(name: String, width: Int, height: Int, television: Boolean, pointer: Boolean = false,
        content: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television = television, pointer = pointer) { Box(Modifier.fillMaxSize().background(Palette.background)) { content() } }
        }
        try {
            repeat(30) { scene.render(it * 16_666_667L).close() }
            dir?.let { root ->
                File(root, "$name.png").also { file ->
                    scene.render(4_000_000_000L).use { image -> file.writeBytes(image.encodeToData()!!.bytes) }
                }
            }
        } finally { scene.close() }
    }

    @Test fun rendersBothSegmentsOnEveryLayout() {
        if (dir == null) return
        var segment by mutableStateOf(MyListSegment.Saved)
        val switch: (MyListSegment) -> Unit = { segment = it }
        for ((name, size) in listOf("phone" to (390 to 844), "desk" to (1280 to 800), "wide" to (1440 to 900))) {
            val pointer = name != "phone"
            for (target in listOf(MyListSegment.Saved, MyListSegment.Continue)) {
                segment = target
                capture("$name-${target.name.lowercase()}", size.first, size.second, television = false, pointer = pointer) {
                    LibraryScreen((0 until 10).map(::libraryCard), available = true, television = false, open = {}, history = {}, home = {},
                        snapshot = snapshot, query = defaultLibraryQuery(), changeQuery = {},
                        segment = target, selectSegment = switch,
                        continueWatching = ContinueWatchingSection(
                            ContinueWatchingPage((0 until 8).map(::continueCard), loaded = true), play = {}, more = {}, retry = {}, remove = {}, home = {}))
                }
            }
        }
        for (target in listOf(MyListSegment.Saved, MyListSegment.Continue)) {
            capture("tv-${target.name.lowercase()}", 1920, 1080, television = true) {
                LibraryScreen((0 until 10).map(::libraryCard), available = true, television = true, open = {}, history = {}, home = {},
                    snapshot = snapshot, query = defaultLibraryQuery(), changeQuery = {},
                    segment = target, selectSegment = switch,
                    continueWatching = ContinueWatchingSection(
                        ContinueWatchingPage((0 until 8).map(::continueCard), loaded = true), play = {}, more = {}, retry = {}, remove = {}, home = {}))
            }
        }
    }
}