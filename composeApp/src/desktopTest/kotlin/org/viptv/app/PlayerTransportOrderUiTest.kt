package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.features.player.TvPlaybackExtras
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class PlayerTransportOrderUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(number: Long) = Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        metadata = CardMetadata(runtime_minutes = 52), provider_id = "addon:7", title = "Episode $number", badges = emptyList(),
        parent_identity = parent, metadata_origin = origin)
    private var frame = 0L

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private inner class Player(width: Int, height: Int) {
        private val facts = kotlinx.coroutines.flow.MutableSharedFlow<PlayerFact>(replay = 1).apply {
            tryEmit(PlayerFact.Progress(768_000, 3_132_000))
        }
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(pointer = true) {
            PlayerScreen(null, true, null, {}, {}, {}, title = episode(24), externalFacts = facts,
                tvExtras = TvPlaybackExtras(episodes = listOf(episode(24)), nextEpisode = episode(25), sourceLabel = "Torrentio 1080p"),
                playEpisode = {}, chooseSources = {}, toggleFullscreen = {})
        } }
        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        fun control(value: String) = nodes(scene).firstOrNull { node -> texts(node).any { it == value } }
        fun bounds(value: String) = assertNotNull(control(value), "No \"$value\"").boundsInRoot
        fun close() = scene.close()
    }

    @Test fun theWideTransportKeepsTenSecondSkipsAndVolumeOnTheRight() {
        for (width in listOf(1280, 1440)) {
            val player = Player(width, 900)
            try {
                player.frames()
                val back = player.bounds("Back 10 seconds")
                val play = player.bounds("Play")
                val forward = player.bounds("Forward 10 seconds")
                val next = player.bounds("Next episode")
                val tracks = player.bounds("Audio and subtitles")
                val mute = player.bounds("Mute or unmute")
                val volume = player.bounds("Volume")
                assertTrue(back.right <= play.left, "$width: −10 comes before play: $back vs $play")
                assertTrue(play.right <= forward.left, "$width: play comes before +10: $play vs $forward")
                assertTrue(forward.right <= next.left, "$width: +10 comes before next: $forward vs $next")
                assertTrue(mute.left > next.right, "$width: mute leaves the transport group: $mute vs $next")
                assertTrue(mute.left > tracks.right, "$width: mute follows Audio & subtitles: $mute vs $tracks")
                assertTrue(volume.left >= mute.right, "$width: the volume slider follows mute: $volume vs $mute")
            } finally { player.close() }
        }
    }

    @Test fun theNarrowTransportSplitsIntoTransportAndToolsRows() {
        val player = Player(900, 700)
        try {
            player.frames()
            val back = player.bounds("Back 10 seconds")
            val play = player.bounds("Play")
            val forward = player.bounds("Forward 10 seconds")
            val next = player.bounds("Next episode")
            val mute = player.bounds("Mute or unmute")
            assertTrue(back.right <= play.left, "−10 comes before play: $back vs $play")
            assertTrue(play.right <= forward.left, "play comes before +10: $play vs $forward")
            assertTrue(forward.right <= next.left, "+10 comes before next: $forward vs $next")
            assertEquals(play.center.y, mute.center.y, 1f, "the narrow row keeps mute with the transport")
            assertTrue(player.control("Volume") == null, "the narrow row has no room for the volume slider")
        } finally { player.close() }
    }
}