package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.features.settings.*
import org.viptv.app.features.sources.SourceRow
import org.viptv.app.features.sources.TvSourceRow
import org.viptv.app.features.sources.TvSourceInfo
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import java.awt.EventQueue
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlin.test.*

/** D-pad geometry of the TV preference pages and the release-fact badges in source rows. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvPreferenceRowsUiTest {
    private val edt = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = !EventQueue.isDispatchThread()
        override fun dispatch(context: CoroutineContext, block: Runnable) = EventQueue.invokeLater(block)
    }

    @Test fun playbackStartsOnTheFirstStoredRowAndMovesUpDownAndLeft() = runBlocking(edt) {
        val choices = mutableListOf<String>()
        var prefs by mutableStateOf(TvSettingsPreferences())
        val scene = tvSettings("Playback", { prefs }) { prefs = it; choices += it.hdr }
        try {
            frames(scene)
            val rows = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }.map(::text)
                .mapNotNull { label -> tvPlaybackRows(false).firstOrNull { label.startsWith(it) } }
            assertEquals(listOf("Default source", "Preferred quality", "HDR and Dolby Vision"), rows)
            assertFalse(nodes(scene).any { text(it).startsWith("Start with subtitles") })
            assertFocused(scene, "Default source")
            press(scene, Key.DirectionDown); assertFocused(scene, "Preferred quality")
            assertTrue(focused(scene)!!.let(::text).contains("Auto"))
            press(scene, Key.DirectionDown); assertFocused(scene, "HDR and Dolby Vision")
            assertTrue(focused(scene)!!.let(::text).contains("No preference"))
            press(scene, Key.DirectionDown); assertFocused(scene, "HDR and Dolby Vision")
            press(scene, Key.Enter); frames(scene, 20)
            click(scene, "Avoid"); frames(scene, 40)
            assertEquals("Avoid", prefs.hdr)
            capture(scene, "tv-playback-settings")
            press(scene, Key.DirectionLeft); frames(scene, 60)
            assertTrue(focused(scene)!!.let(::text).startsWith("Playback"), "Left reaches the sidebar: ${focused(scene)?.let(::text)}")
            press(scene, Key.DirectionRight); frames(scene, 80); assertFocused(scene, "Default source")
        } finally { scene.close() }
    }

    @Test fun subtitlesAndAudioIsOneVerticalList() = runBlocking(edt) {
        var prefs by mutableStateOf(TvSettingsPreferences(audioLanguage = "French"))
        val scene = tvSettings("Subtitles & audio", { prefs }) { prefs = it }
        try {
            frames(scene)
            assertFocused(scene, "Audio language")
            for (row in tvSubtitleRows.drop(1)) { press(scene, Key.DirectionDown); assertFocused(scene, row) }
            press(scene, Key.DirectionDown); assertFocused(scene, "Subtitle colour")
            press(scene, Key.DirectionUp); press(scene, Key.DirectionUp); press(scene, Key.DirectionUp); press(scene, Key.DirectionUp)
            assertFocused(scene, "Subtitles")
            assertTrue(focused(scene)!!.let(::text).contains("Always"))
            press(scene, Key.Enter); frames(scene, 20)
            click(scene, "When audio isn’t French"); frames(scene, 40)
            assertEquals("foreign", prefs.subtitleMode)
            assertFalse(nodes(scene).any { text(it).startsWith("The preview updates") })
            capture(scene, "tv-subtitle-settings")
            press(scene, Key.DirectionLeft); frames(scene, 60)
            assertTrue(focused(scene)!!.let(::text).startsWith("Subtitles & audio"), "Left reaches the sidebar: ${focused(scene)?.let(::text)}")
        } finally { scene.close() }
    }

    @Test fun badgesStayOnTheTitleLineAndDropOnNarrowPhones() = runBlocking(edt) {
        val source = SourceCandidate("a", "p", "Provider name that is fairly long", detail = "Some.Release.2160p.WEB-DL.DV.mkv",
            quality_height = 2160, release_type = "web_dl", dynamic_range = listOf("dv"), audio_flags = listOf("dual"), three_d = true)
        fun heightOf(scene: ImageComposeScene) = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }.maxOf { it.boundsInRoot.height }
        fun phone(candidate: SourceCandidate, width: Int) = ImageComposeScene(width, 400, Density(1f)) { ViptvTheme {
            Column(Modifier.width(width.dp)) { SourceRow(candidate, false, {}) } } }
        // (candidate, width, at least this many badges shown)
        val cases = listOf(Triple(source, 320, 1), Triple(source, 390, 0), Triple(source.copy(label = "LordStreams"), 390, 2), Triple(source, 800, 4))
        for ((candidate, width, least) in cases) {
            val withBadges = phone(candidate, width)
            val without = phone(candidate.copy(release_type = null, dynamic_range = emptyList(), audio_flags = emptyList(), three_d = false), width)
            try {
                frames(withBadges); frames(without)
                assertEquals(heightOf(without), heightOf(withBadges), "badges never add a line at $width")
                val badges = unmerged(withBadges).filter { it.layoutInfo.isPlaced && text(it) in listOf("WEB-DL", "DV", "Dual audio", "3D") }
                // Order is kept and only trailing badges drop; a one-line label that fills the row leaves room for none.
                assertEquals(listOf("WEB-DL", "DV", "Dual audio", "3D").take(badges.size), badges.map(::text), "at $width")
                assertTrue(badges.size >= least, "at $width ${candidate.label}: ${badges.map(::text)}")
                assertTrue(badges.all { it.boundsInRoot.right <= width }, "no overflow at $width")
                capture(withBadges, "phone-source-row-$width-${candidate.label.length}")
            } finally { withBadges.close(); without.close() }
        }
        val tv = ImageComposeScene(1920, 300, Density(1f)) { ViptvTheme(true) { Column(Modifier.width(1200.dp)) {
            TvSourceRow(source, TvSourceInfo("4K", "MKV", "[Add-on]", bestMatch = true), 1f, null, true, {}) } } }
        try {
            frames(tv)
            assertEquals(listOf("WEB-DL", "DV", "Dual audio", "3D"), unmerged(tv).filter { it.layoutInfo.isPlaced && text(it) in listOf("WEB-DL", "DV", "Dual audio", "3D") }.map(::text))
            capture(tv, "tv-source-row")
        } finally { tv.close() }
    }

    // Desktop scenes do not turn arrow keys into focus moves; the focus manager
    // applies the same focusProperties (up/down/left) a D-pad press does.
    private var focusManager: FocusManager? = null
    private fun tvSettings(section: String, current: () -> TvSettingsPreferences = { TvSettingsPreferences() },
        change: (TvSettingsPreferences) -> Unit) = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
        focusManager = LocalFocusManager.current
        SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {}, television = true,
            initialSection = section, tvPreferences = current(), changeTvPreferences = change)
    } }
    private var clock = 0L
    private suspend fun frames(scene: ImageComposeScene, @Suppress("UNUSED_PARAMETER") start: Int = 0) = repeat(16) { scene.render(clock++ * 16_666_667L).close(); delay(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun unmerged(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(false) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun focused(scene: ImageComposeScene) = nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
    private suspend fun assertFocused(scene: ImageComposeScene, label: String) {
        frames(scene)
        val now = focused(scene)?.let(::text).orEmpty()
        assertTrue(now.startsWith(label), "expected focus on $label, was '$now'")
    }
    private suspend fun press(scene: ImageComposeScene, key: Key) {
        when (key) {
            Key.DirectionDown -> focusManager!!.moveFocus(FocusDirection.Down)
            Key.DirectionUp -> focusManager!!.moveFocus(FocusDirection.Up)
            Key.DirectionLeft -> focusManager!!.moveFocus(FocusDirection.Left)
            Key.DirectionRight -> focusManager!!.moveFocus(FocusDirection.Right)
            else -> focused(scene)!!.config[SemanticsActions.OnClick].action!!.invoke()
        }
        frames(scene)
    }
    private fun click(scene: ImageComposeScene, label: String) {
        val node = nodes(scene).firstOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) } ?: fail("no '$label' in ${nodes(scene).map(::text).filter(String::isNotBlank)}")
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
    private fun capture(scene: ImageComposeScene, name: String) { System.getenv("VIPTV_PREFERENCES_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs(); scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    } }
}
