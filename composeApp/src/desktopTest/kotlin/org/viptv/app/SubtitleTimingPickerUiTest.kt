@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.features.player.*
import org.viptv.app.player.*
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

class SubtitleTimingPickerUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private var frame = 0L
    private fun until(scene: ImageComposeScene, condition: () -> Boolean) {
        frame = scene.renderUntil(from = frame, what = "subtitle timing state", condition = condition)
    }
    private val tracks = PlaybackTracks(subtitles = listOf(PlaybackTrack(Int.MIN_VALUE, "English", true, "en")))
    private fun session(available: Boolean) = SubtitleSyncSession().apply {
        select(SubtitleSyncIdentity("owned", "audio", "subtitle"), listOf(SubtitleCueInterval(0, 20_000_000)))
        setLocalAudioAvailable(available)
    }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun button(scene: ImageComposeScene, label: String) = nodes(scene).firstOrNull { node ->
        node.config.contains(SemanticsActions.OnClick) && flatten(node).any { text(it) == label }
    }
    private fun click(scene: ImageComposeScene, label: String) {
        assertTrue(assertNotNull(button(scene, label), label).config[SemanticsActions.OnClick].action!!.invoke())
    }

    @Test fun compactPickerKeepsDefaultOffAndManualControlsFreezeAcceptedTiming() {
        val session = session(true)
        var selectionCalls = 0
        val scene = ImageComposeScene(1280, 720, Density(1f)) { ViptvTheme {
            PlaybackTrackPicker(tracks, { selectionCalls++ }, {}, false, {}, ProfilePreferences(), {}, {},
                WindowInsets(0, 0, 0, 0), subtitleSync = session)
        } }
        try {
            until(scene) { button(scene, "Subtitle timing (0.0 s)") != null }
            click(scene, "Subtitle timing (0.0 s)")
            until(scene) { button(scene, "On") != null }
            assertFalse(session.ui.value.automatic)
            click(scene, "On")
            until(scene) { session.ui.value.automatic }
            val oldEpoch = session.epoch
            assertTrue(session.offer(oldEpoch, 1, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 2_000_000)))
            assertEquals(8_000_000L, session.subtitleTimeUs(10_000_000, true))
            until(scene) { button(scene, "Earlier by 0.5 s") != null }
            assertEquals(Role.Button, button(scene, "Earlier by 0.5 s")!!.config.getOrNull(SemanticsProperties.Role))
            click(scene, "Earlier by 0.5 s")
            until(scene) { nodes(scene).any { text(it) == "Manual timing" } }
            assertEquals(8_500_000L, session.subtitleTimeUs(10_000_000))
            assertFalse(session.offer(oldEpoch, 2, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 0)))
            click(scene, "Off")
            assertEquals(10_500_000L, session.subtitleTimeUs(10_000_000))
            click(scene, "On")
            assertEquals(10_000_000L, session.subtitleTimeUs(10_000_000))
            session.adjustManual(500_000)
            click(scene, "Reset automatic timing")
            assertEquals(10_000_000L, session.subtitleTimeUs(10_000_000))
            assertFalse(session.manual)
            assertEquals(0, selectionCalls)
        } finally { scene.close() }
    }

    @Test fun unavailableAudioOffersManualTimingWithoutOn() {
        val session = session(false)
        val scene = ImageComposeScene(1280, 720, Density(1f)) { ViptvTheme {
            PlaybackTrackPicker(tracks, {}, {}, false, {}, ProfilePreferences(), {}, {},
                WindowInsets(0, 0, 0, 0), subtitleSync = session)
        } }
        try {
            until(scene) { button(scene, "Subtitle timing (0.0 s)") != null }
            click(scene, "Subtitle timing (0.0 s)")
            until(scene) { button(scene, "Later by 0.5 s") != null }
            assertNull(button(scene, "On"))
            assertTrue(nodes(scene).any { text(it) == "Automatic timing unavailable" })
            click(scene, "Later by 0.5 s")
            assertEquals(9_500_000L, session.subtitleTimeUs(10_000_000))
        } finally { scene.close() }
    }

    @Test fun tvTimingMenuFocusAndDismissRemainInsideExistingPicker() = runBlocking {
        val session = session(true)
        var focusedManager: FocusManager? = null
        var dismiss: (() -> Unit)? = null
        var open by mutableStateOf(true)
        var closed = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(television = true) {
            focusedManager = LocalFocusManager.current
            if (open) TvPlaybackTrackPicker(tracks, 1f, 1f, {}, { closed++; open = false },
                dismissChanged = { dismiss = it }, subtitleSync = session)
        } }
        fun focused() = nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let { node ->
            flatten(node).firstNotNullOfOrNull { text(it).takeIf(String::isNotEmpty) }
        }
        try {
            until(scene) { button(scene, "Subtitle timing (0.0 s)") != null }
            click(scene, "Subtitle timing (0.0 s)")
            until(scene) {
                focused() == "Earlier by 0.5 s" && button(scene, "Earlier by 0.5 s")?.boundsInRoot?.let {
                    it.top >= 0 && it.bottom <= 1080 && it.height >= 63
                } == true
            }
            assertTrue(focusedManager!!.moveFocus(FocusDirection.Up))
            until(scene) { focused() == "On" }
            click(scene, "On")
            until(scene) { session.ui.value.automatic && focused() == "On" }
            assertTrue(focusedManager!!.moveFocus(FocusDirection.Down))
            until(scene) { focused() == "Earlier by 0.5 s" }
            click(scene, "Earlier by 0.5 s")
            until(scene) { session.manual && focused() == "Earlier by 0.5 s" }
            assertNotNull(dismiss).invoke()
            until(scene) { closed == 1 && dismiss == null }
            assertEquals(1, closed)
        } finally { scene.close() }
    }
}
