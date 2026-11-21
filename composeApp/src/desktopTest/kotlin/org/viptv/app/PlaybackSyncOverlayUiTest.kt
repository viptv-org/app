@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.features.player.*
import org.viptv.app.player.*
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

class PlaybackSyncOverlayUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private var time = 0L
    private fun ImageComposeScene.frame() { render(time).close(); time += 16_666_667L }
    private fun ImageComposeScene.frames(millis: Long) = repeat((millis * 1_000_000L / 16_666_667L).toInt()) { frame() }
    private fun ImageComposeScene.until(condition: () -> Boolean) {
        repeat(600) { frame(); if (condition()) return }
        fail("the scene did not reach the expected state")
    }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
    private fun described(scene: ImageComposeScene, label: String) = nodes(scene).firstOrNull {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true && it.config.contains(SemanticsActions.OnClick)
    }
    private fun labelled(scene: ImageComposeScene, label: String) = nodes(scene).firstOrNull { node ->
        node.config.contains(SemanticsActions.OnClick) && flatten(node).any { label in texts(it) }
    }
    private fun shows(scene: ImageComposeScene, label: String) = nodes(scene).any { label in texts(it) }
    private fun offsets(initial: TimingOffsets = TimingOffsets()) = PlaybackOffsets(initial, SubtitleSyncSession()).apply { supports(true, true) }
    private fun key(scene: ImageComposeScene, key: Key) {
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
    }

    @Test fun eachPressAppliesInTheSameFrameAndHoldingRepeats() {
        val offsets = offsets()
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme { PlaybackSyncOverlay(SyncKind.Audio, offsets, {}) } }
        try {
            scene.until { described(scene, "Later") != null }
            assertTrue(described(scene, "Later")!!.config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(100_000L, offsets.audioUs, "the offset is applied by the press itself, before any frame")
            scene.frame()
            assertTrue(shows(scene, "+0.1 s"))
            val earlier = described(scene, "Earlier")!!.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, earlier)
            scene.frame()
            assertEquals(0L, offsets.audioUs)
            scene.frames(1_000)
            scene.sendPointerEvent(PointerEventType.Release, earlier)
            scene.frame()
            val held = offsets.audioUs
            assertTrue(held <= -500_000L, "holding repeats every 0.1 s after a short wait, saw $held")
            scene.frames(500)
            assertEquals(held, offsets.audioUs, "releasing stops the repeat")
            assertTrue(labelled(scene, "Reset")!!.config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(0L, offsets.audioUs)
        } finally { scene.close() }
    }

    @Test fun itClosesItselfAfterFiveIdleSecondsAndInputKeepsItOpen() {
        val offsets = offsets()
        var open by mutableStateOf(true)
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            if (open) PlaybackSyncOverlay(SyncKind.Subtitle, offsets, { open = false })
        } }
        try {
            scene.until { described(scene, "Later") != null }
            scene.frames(4_000)
            assertTrue(open)
            described(scene, "Later")!!.config[SemanticsActions.OnClick].action!!.invoke()
            scene.frames(4_000)
            assertTrue(open, "a press restarts the idle timer")
            scene.frames(1_300)
            assertFalse(open)
            assertEquals(100_000L, offsets.subtitleUs)
        } finally { scene.close() }
    }

    @Test fun tvLeftAndRightAdjustAndDownReachesReset() {
        val offsets = offsets(TimingOffsets(audioUs = 300_000))
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(television = true) {
            PlaybackSyncOverlay(SyncKind.Audio, offsets, {}, tv = true)
        } }
        try {
            scene.until { shows(scene, "+0.3 s") && shows(scene, SYNC_SAVED) }
            scene.frames(100)
            key(scene, Key.DirectionRight)
            key(scene, Key.DirectionRight)
            assertEquals(500_000L, offsets.audioUs)
            key(scene, Key.DirectionLeft)
            assertEquals(400_000L, offsets.audioUs)
            scene.frame()
            assertFalse(shows(scene, SYNC_SAVED))
            key(scene, Key.DirectionDown)
            scene.frame()
            key(scene, Key.DirectionCenter)
            assertEquals(0L, offsets.audioUs)
        } finally { scene.close() }
    }

    private val tracks = PlaybackTracks(audio = listOf(PlaybackTrack(1, "Japanese", true, "ja"), PlaybackTrack(2, "English", false, "en")),
        subtitles = listOf(PlaybackTrack(3, "English", false, "en")))

    @Test fun theSheetOpensEachOverlayFromItsSideAndSubtitleSyncWaitsForSubtitles() {
        val offsets = offsets(TimingOffsets(audioUs = 300_000))
        var subtitles by mutableStateOf(tracks)
        val opened = mutableListOf<SyncKind>()
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            PlaybackTrackPicker(subtitles, {}, {}, true, {}, ProfilePreferences(), {}, {}, WindowInsets(0, 0, 0, 0),
                offsets = offsets, openSync = { opened += it })
        } }
        try {
            scene.until { labelled(scene, "Audio sync") != null }
            assertTrue(shows(scene, "+0.3 s"))
            assertTrue(shows(scene, SUBTITLES_OFF))
            val subtitleRow = assertNotNull(nodes(scene).firstOrNull { node -> flatten(node).any { "Subtitle sync" in texts(it) } && node.config.contains(SemanticsProperties.Disabled) })
            assertFalse(subtitleRow.config.contains(SemanticsActions.OnClick))
            labelled(scene, "Audio sync")!!.config[SemanticsActions.OnClick].action!!.invoke()
            subtitles = tracks.copy(subtitles = listOf(PlaybackTrack(3, "English", true, "en")))
            scene.until { labelled(scene, "Subtitle sync") != null }
            labelled(scene, "Subtitle sync")!!.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(listOf(SyncKind.Audio, SyncKind.Subtitle), opened)
        } finally { scene.close() }
    }

    @Test fun tvSheetListsBothEntries() {
        val offsets = offsets()
        val opened = mutableListOf<SyncKind>()
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(television = true) {
            TvPlaybackTrackPicker(tracks.copy(subtitles = listOf(PlaybackTrack(3, "English", true, "en"))), 1f, 1f, {}, {},
                dismissChanged = {}, offsets = offsets, openSync = { opened += it })
        } }
        try {
            scene.until { labelled(scene, "Audio sync  0.0 s") != null && labelled(scene, "Subtitle sync  0.0 s") != null }
            labelled(scene, "Subtitle sync  0.0 s")!!.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(listOf(SyncKind.Subtitle), opened)
        } finally { scene.close() }
    }
}
