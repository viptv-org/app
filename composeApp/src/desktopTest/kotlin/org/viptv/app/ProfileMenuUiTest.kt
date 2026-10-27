package org.viptv.app

import org.junit.Rule
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.components.*
import org.viptv.app.connection.Profile
import org.viptv.app.preview.UiPreview
import org.viptv.app.state.Screen
import org.viptv.app.theme.*
import java.io.File
import kotlin.test.*

/** The profile-picture menu is one shared popover in the phone header and the browser top bar. */
@OptIn(ExperimentalComposeUiApi::class)
class ProfileMenuUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val profile = Profile("qa", "Stremio import QA", null, avatarId = "initial-blue")

    @Test fun phoneHeaderOpensTheProfileMenuInPortraitAndLandscape() {
        for ((width, height) in listOf(390 to 844, 844 to 390)) checkMenu(width, height) { navigate -> PhoneHeader(Screen.Home, profile, navigate) }
    }

    @Test fun browserTopBarOpensTheSameProfileMenuWideAndCollapsed() {
        for ((width, height) in listOf(1440 to 900, 900 to 600, 390 to 844)) checkMenu(width, height) { navigate -> TopNavigation(Screen.Home, false, navigate, profile) }
    }

    @Test fun withoutAProfileTheMenuKeepsBothActionsAndDropsTheHeader() {
        var navigated: Screen? = null
        val scene = scene(1440, 900) { TopNavigation(Screen.Home, false, { navigated = it }, null) }
        try {
            open(scene)
            assertFalse(nodes(scene).any { text(it).contains("Watching as") })
            assertEquals(listOf("Switch profile", "Settings"), actions(scene))
            click(scene, "Settings"); assertEquals(Screen.Settings, navigated)
        } finally { scene.close() }
    }

    @Test fun clickingOutsideDismissesTheMenuWithoutNavigating() {
        var navigated: Screen? = null
        val scene = scene(1440, 900) { TopNavigation(Screen.Home, false, { navigated = it }, profile) }
        try {
            open(scene)
            scene.sendPointerEvent(PointerEventType.Press, Offset(400f, 600f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(400f, 600f))
            frames(scene, 40)
            assertFalse(menuOpen(scene)); assertNull(navigated)
        } finally { scene.close() }
    }

    /** Writes the menu over the preview Home when VIPTV_PROFILE_MENU_CAPTURE_DIR is set (PR screenshots). */
    @Test fun captureTheMenuOverThePreviewHome() {
        val dir = System.getenv("VIPTV_PROFILE_MENU_CAPTURE_DIR") ?: return
        for (shot in listOf(Shot("phone", 780, 1688, 2f, false), Shot("browser", 1440, 900, 1f, true), Shot("browser-collapsed", 900, 700, 1f, true))) {
            frame = 0
            val scene = ImageComposeScene(shot.w, shot.h, Density(shot.density)) { UiPreview(browser = shot.browser) }
            try {
                frames(scene, 120); open(scene)
                save(scene, dir, shot.name)
                if (shot.browser) {
                    val settings = nodes(scene).single { text(it) == "Settings" && it.config.contains(SemanticsActions.OnClick) }.boundsInRoot.center
                    scene.sendPointerEvent(PointerEventType.Move, settings)
                    frames(scene, 20); save(scene, dir, "${shot.name}-hover")
                }
            } finally { scene.close() }
        }
    }
    private fun save(scene: ImageComposeScene, dir: String, name: String) {
        File(dir).mkdirs()
        scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(dir, "$name.png").writeBytes(it.bytes) } }
    }
    private data class Shot(val name: String, val w: Int, val h: Int, val density: Float, val browser: Boolean)

    /** Opens the menu, checks header, icons and actions, then that each action closes it and navigates as before. */
    private fun checkMenu(width: Int, height: Int, header: @Composable ((Screen) -> Unit) -> Unit) {
        val navigated = mutableListOf<Screen>()
        val scene = scene(width, height) { header { navigated += it } }
        try {
            open(scene)
            val headerNode = nodes(scene).single { text(it) == "Watching as Stremio import QA" }
            assertTrue(headerNode.boundsInRoot.width > 0f, "header row at ${width}x$height")
            assertEquals(listOf("Switch profile", "Settings"), actions(scene), "actions at ${width}x$height")
            assertEquals(2, unmergedNodes(scene).count { it.config.getOrNull(SemanticsProperties.TestTag) == PopoverMenuItemIconTag }, "one leading icon per action")
            val menuBounds = nodes(scene).filter { text(it) in listOf("Switch profile", "Settings") }.map { it.boundsInRoot }
            assertTrue(menuBounds.all { it.left >= 0f && it.right <= width && it.bottom <= height }, "menu inside the ${width}x$height window: $menuBounds")
            click(scene, "Switch profile")
            assertEquals(listOf(Screen.Profiles), navigated); assertFalse(menuOpen(scene))
            open(scene); click(scene, "Settings")
            assertEquals(listOf(Screen.Profiles, Screen.Settings), navigated); assertFalse(menuOpen(scene))
        } finally { scene.close() }
    }

    private fun scene(width: Int, height: Int, content: @Composable () -> Unit): ImageComposeScene {
        frame = 0
        return ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) { Column { content() } } }
        }
    }
    private fun open(scene: ImageComposeScene) {
        frames(scene)
        // Announced as "Profile menu" alone, not with the avatar's initial or artwork name.
        val trigger = nodes(scene).single { it.config.contains(SemanticsActions.OnClick) && it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf("Profile menu") && text(it).isEmpty() }
        assertTrue(trigger.config[SemanticsActions.OnClick].action!!.invoke())
        frames(scene, 40)
        assertTrue(menuOpen(scene))
    }
    private fun menuOpen(scene: ImageComposeScene) = nodes(scene).any { text(it) == "Switch profile" }
    private fun actions(scene: ImageComposeScene) = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) && text(it) in listOf("Switch profile", "Settings") }
        .sortedBy { it.boundsInRoot.top }.map(::text)
    private fun click(scene: ImageComposeScene, label: String) {
        val node = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
        frames(scene, 40)
    }
    private var frame = 0L
    private fun frames(s: ImageComposeScene, count: Int = 20) = repeat(count) { s.render(frame++ * 16_666_667L).close(); Thread.sleep(3) }
    private fun nodes(s: ImageComposeScene) = s.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun unmergedNodes(s: ImageComposeScene) = s.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
    private fun flatten(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::flatten)
    private fun text(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
}
