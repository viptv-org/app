package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.player.PLAYER_SETTINGS_LINK
import org.viptv.app.player.*
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class AudioSubtitlesPanelUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val title = org.viptv.engine.wire.Card(org.viptv.engine.wire.MediaIdentity("m", "movie", emptyList()),
        provider_id = "metadata", title = "Movie", badges = emptyList())
    private var frame = 0L
    private val progress = PlayerFact.Progress(842_000, 3_132_000)
    private fun frames(scene: ImageComposeScene, count: Int = 16) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    private fun clickable(scene: ImageComposeScene, value: String) = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }
        .firstOrNull { node -> texts(node).contains(value) || flatten(node).any { texts(it).contains(value) } }
    private fun click(scene: ImageComposeScene, value: String) {
        val node = assertNotNull(clickable(scene, value), "No clickable \"$value\"")
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
    private fun shows(scene: ImageComposeScene, value: String) = nodes(scene).any { texts(it).contains(value) }

    private fun subtitles(vararg ids: Int) = ids.map { id ->
        PlaybackTrack(id, when (id) { 3 -> "English"; 4 -> "English (SDH)"; 5 -> "Spanish"; else -> "English" }, id == 3, "en")
    }

    private fun scene(width: Int, height: Int, phone: Boolean, audio: List<PlaybackTrack>, subs: List<PlaybackTrack>): ImageComposeScene {
        val facts = kotlinx.coroutines.flow.MutableSharedFlow<PlayerFact>(replay = 1)
        facts.tryEmit(PlayerFact.Tracks(PlaybackTracks(audio, subs)))
        return ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
            org.viptv.app.features.player.PlayerScreen(null, true, null, {}, {}, {}, title = title, preview = progress,
                phone = phone, externalFacts = facts, openSettings = {})
        } }
    }

    @Test fun subtitleRowsShareOneHeightAndThePanelStaysInsideThePlayer() {
        val audio = listOf(PlaybackTrack(1, "English · 5.1", true, "en"), PlaybackTrack(2, "Japanese · Stereo", false, "ja"))
        for (width in listOf(1280, 1440)) {
            val scene = scene(width, 900, phone = false, audio = audio, subs = subtitles(3, 4, 5))
            try {
                frames(scene); click(scene, "Audio and subtitles"); frames(scene, 20)
                assertTrue(shows(scene, "Audio & subtitles"))
                val rows = listOf("Off", "English", "English (SDH)", "Spanish")
                val heights = rows.map { label -> assertNotNull(clickable(scene, label), "No \"$label\" row at $width").boundsInRoot.run { bottom - top } }
                assertTrue(heights.all { it == heights.first() }, "Rows must share one height at $width: $heights")
                val link = assertNotNull(clickable(scene, PLAYER_SETTINGS_LINK)).boundsInRoot
                assertTrue(link.right <= width && link.bottom <= 900, "The panel footer escapes the player at $width: $link")
                val panelBottom = assertNotNull(nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.PaneTitle) == "Audio and subtitles" }, "No panel at $width").boundsInRoot.bottom
                val timelineTop = assertNotNull(nodes(scene).firstOrNull { texts(it).contains("14:02") }, "No timeline at $width").boundsInRoot.top
                assertTrue(panelBottom < timelineTop, "The panel bottom ($panelBottom) must sit above the timeline ($timelineTop) at $width")
            } finally { scene.close() }
        }
    }

    @Test fun oneOrNoAudioTrackCollapsesToDefaultAudio() {
        for (audio in listOf(listOf(PlaybackTrack(1, "English · 5.1", true, "en")), emptyList())) {
            val scene = scene(1280, 900, phone = false, audio = audio, subs = subtitles(3))
            try {
                frames(scene); click(scene, "Audio and subtitles"); frames(scene, 20)
                assertTrue(shows(scene, "Default audio"), "Single or no audio must collapse")
                assertFalse(shows(scene, "English · 5.1"), "The lone audio name must not list")
            } finally { scene.close() }
        }
    }

    @Test fun thePhoneSheetShowsTheInlineSettingsVertically() {
        val scene = scene(390, 844, phone = true, audio = listOf(PlaybackTrack(1, "English · 5.1", true, "en")), subs = subtitles(3))
        try {
            frames(scene); click(scene, "Audio & subtitles"); frames(scene, 20)
            for (value in listOf("Default audio", "Subtitles", "Language", "Size", "Background", PLAYER_SETTINGS_LINK, "Always", "Default")) {
                assertTrue(shows(scene, value), "The phone sheet is missing \"$value\"")
            }
        } finally { scene.close() }
    }
}