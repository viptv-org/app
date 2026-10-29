package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.TvProfiles
import org.viptv.app.features.sources.TvSourceInfo
import org.viptv.app.features.sources.TvSourceRow
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvEntryAndSourcesUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @Test fun firstProfileReceivesFocusAfterAsyncLoginEnablesTheLazyRow() {
        val busy = mutableStateOf(true)
        val profiles = mutableStateOf(emptyList<Profile>())
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                TvProfiles(profiles.value, busy.value, null, false, false, {}, {}, {}, {}, {})
            } }
        }
        try {
            frames(scene, 0, 8)
            profiles.value = listOf(Profile("first", "First profile", null), Profile("second", "Second profile", null))
            frames(scene, 8, 8)
            busy.value = false
            frames(scene, 16, 24)
            assertTrue(nodes(scene).any { node -> text(node).contains("First profile") &&
                node.config.getOrNull(SemanticsProperties.Focused) == true }, "The first enabled profile must already have focus")
            capture(scene, "profiles")
        } finally { scene.close() }
    }

    @Test fun upFromManageProfilesReturnsToTheTileTheViewerLeft() {
        val profiles = listOf(Profile("a", "Alpha", null), Profile("b", "Beta", null),
            Profile("c", "Gamma", null), Profile("d", "Delta", null))
        var focusManager: FocusManager? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                focusManager = LocalFocusManager.current
                TvProfiles(profiles, false, null, true, false, {}, {}, {}, {}, {})
            } }
        }
        try {
            frames(scene, 0, 8)
            val fourth = nodes(scene).first { it.config.contains(SemanticsActions.RequestFocus) && text(it).contains("Delta") }
            assertTrue(fourth.config[SemanticsActions.RequestFocus].action!!.invoke(), "the fourth profile tile takes focus")
            frames(scene, 8, 8)
            press(scene, requireNotNull(focusManager), Key.DirectionDown)
            frames(scene, 16, 8)
            assertTrue(focused(scene)?.let { text(it).contains("Manage profiles") } == true,
                "Down from a profile tile lands on Manage profiles, got ${focused(scene)?.let(::text)}")
            press(scene, requireNotNull(focusManager), Key.DirectionUp)
            frames(scene, 24, 8)
            assertTrue(focused(scene)?.let { text(it).contains("Delta") } == true,
                "Up from Manage profiles returns to the tile the viewer left, got ${focused(scene)?.let(::text)}")
        } finally { scene.close() }
    }

    @Test fun longMultilineSourceGrowsAndEveryTitleLineRemainsVisible() {
        val short = "Short release"
        val long = "Example Series S04E12\n2160p WEB-DL HEVC HDR10+ Dolby Atmos\nEnglish / Japanese multi audio\nExtended edition and release group\nAdditional provider information"
        val scene = ImageComposeScene(960, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                Column(Modifier.padding(32.dp)) {
                    TvSourceRow(SourceCandidate("short", "addon", short), TvSourceInfo("1080p"), 1f, null, true, {})
                    Spacer(Modifier.height(16.dp))
                    TvSourceRow(SourceCandidate("long", "addon", long), TvSourceInfo("4K"), 1f, null, true, {})
                }
            } }
        }
        try {
            frames(scene, 0, 16)
            val labels = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }.filter { it.config.contains(SemanticsActions.GetTextLayoutResult) }
            val longLabel = labels.single { text(it) == long }
            val results = mutableListOf<TextLayoutResult>()
            assertTrue(longLabel.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
            assertFalse(results.single().hasVisualOverflow)
            assertTrue(results.single().lineCount >= 5)
            val shortRow = nodes(scene).single { it.config.contains(SemanticsActions.OnClick) && text(it).contains(short) }
            val longRow = nodes(scene).single { it.config.contains(SemanticsActions.OnClick) && text(it).contains(long) }
            assertTrue(longRow.boundsInRoot.height > shortRow.boundsInRoot.height)
            assertTrue(longRow.boundsInRoot.bottom <= 1080)
            capture(scene, "sources")
        } finally { scene.close() }
    }

    private fun frames(scene: ImageComposeScene, start: Int, count: Int) = repeat(count) {
        scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4)
    }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun focused(scene: ImageComposeScene): SemanticsNode? =
        nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
    // Desktop maps no arrow key to a focus move, so an unconsumed D-pad press has to drive the focus manager.
    private fun press(scene: ImageComposeScene, focusManager: FocusManager, key: Key) {
        val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        if (!consumed) focusManager.moveFocus(when (key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionLeft -> FocusDirection.Left
            else -> FocusDirection.Right
        })
    }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun capture(scene: ImageComposeScene, variant: String) {
        val dir = System.getenv("VIPTV_TV_FIX_CAPTURE_DIR") ?: return
        File(dir).mkdirs()
        scene.render(1_000_000_000L).use { image -> image.encodeToData()!!.use { File(dir, "$variant.png").writeBytes(it.bytes) } }
    }
}
