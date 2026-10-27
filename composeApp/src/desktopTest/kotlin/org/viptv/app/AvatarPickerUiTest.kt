package org.viptv.app

import org.junit.Rule
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.*
import org.viptv.app.theme.*
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AvatarPickerUiTest {
    @get:Rule val uiThread = UiThreadRule()
    init { runBlocking { loadAvatarCatalog() } }
    private val saved = Profile("kid", "Kid", null, avatarStyle = "clay", avatarChoice = 20, avatarUrl = "https://avatars.example/clay-20.png")

    @Test fun phoneOpensOnTheSavedPageAndPagesWrapWithinTheWorld() {
        val scene = picker(390, 844, saved, AvatarPickerLayout.Phone)
        try {
            frames(scene)
            assertTrue(nodes(scene).any { text(it) == "624 avatars. Pick a world, then pick your character." })
            assertTrue(nodes(scene).any { text(it) == "Clay friends · 2 / 4" })
            assertEquals(15, tiles(scene).size)
            val selected = tiles(scene).filter { it.config.getOrNull(SemanticsProperties.Selected) == true }
            assertEquals(listOf("Clay friends 20"), selected.map(::label))
            capture(scene, "avatar-picker-phone")
            click(scene, "Next"); frames(scene, 20)
            assertTrue(nodes(scene).any { text(it) == "Clay friends · 3 / 4" })
            click(scene, "Next"); frames(scene, 40); click(scene, "Next"); frames(scene, 60)
            assertTrue(nodes(scene).any { text(it) == "Clay friends · 1 / 4" })
            assertEquals("Clay friends 1", label(tiles(scene).first()))
            click(scene, "Previous"); frames(scene, 80)
            assertTrue(nodes(scene).any { text(it) == "Clay friends · 4 / 4" })
            assertEquals(listOf("Clay friends 46", "Clay friends 47", "Clay friends 48"), tiles(scene).map(::label))
            // Another world starts on its first page.
            click(scene, "Moods"); frames(scene, 100)
            assertTrue(nodes(scene).any { text(it) == "Moods · 1 / 4" })
            assertEquals("Moods 1", label(tiles(scene).first()))
            assertTrue(tiles(scene).none { it.config.getOrNull(SemanticsProperties.Selected) == true })
        } finally { scene.close() }
    }

    @Test fun choosingSendsTheWorldAndPositionAndALocalLockNeedsItsPin() {
        var chosen: Pair<AvatarPick, String>? = null
        val locked = saved.copy(locked = true)
        val scene = picker(1280, 800, locked, AvatarPickerLayout.Desktop) { pick, pin -> chosen = pick to pin }
        try {
            frames(scene)
            assertTrue(nodes(scene).any { text(it) == "Clay friends · 2 / 3" })
            assertEquals(18, tiles(scene).size)
            assertTrue(tiles(scene).all { it.config.contains(SemanticsProperties.Disabled) })
            val field = nodes(scene).single { it.config.contains(SemanticsActions.SetText) }
            field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("1234")); frames(scene, 20)
            val tile = tiles(scene).single { label(it) == "Clay friends 21" }
            assertFalse(tile.config.contains(SemanticsProperties.Disabled))
            tile.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(AvatarPick("clay", 21) to "1234", chosen)
        } finally { scene.close() }
        val open = picker(1280, 800, saved, AvatarPickerLayout.Desktop)
        try { frames(open); capture(open, "avatar-picker-desktop") } finally { open.close() }
    }

    @Test fun televisionFocusesTheSavedAvatarAndPagingFocusesTheFirst() {
        val scene = picker(1920, 1080, saved, AvatarPickerLayout.Television)
        try {
            frames(scene)
            assertTrue(nodes(scene).any { text(it) == "Clay friends · 2 / 3" })
            assertEquals("Clay friends 20", label(focused(scene)))
            capture(scene, "avatar-picker-tv")
            click(scene, "Next"); frames(scene, 20)
            assertEquals("Clay friends 37", label(focused(scene)))
        } finally { scene.close() }
    }

    @Test fun televisionStartsOnThePinFieldWhileALocalLockDisablesTheGrid() {
        val scene = picker(1920, 1080, saved.copy(locked = true), AvatarPickerLayout.Television)
        try {
            frames(scene)
            assertTrue(tiles(scene).all { it.config.contains(SemanticsProperties.Disabled) })
            assertTrue(nodes(scene).any { it.config.getOrNull(SemanticsProperties.Focused) == true && it.config.contains(SemanticsActions.SetText) })
        } finally { scene.close() }
    }

    @Test fun profileWithoutAPickOpensOnTheFirstWorld() {
        val scene = picker(390, 844, Profile("new", "New", null, avatarStyle = "critters", avatarUrl = "https://avatars.example/seed.png"), AvatarPickerLayout.Phone)
        try {
            frames(scene)
            // Character worlds hold one page each.
            assertTrue(nodes(scene).any { text(it) == "Disney favorites · 1 / 1" })
            assertEquals(14, tiles(scene).size)
            assertTrue(tiles(scene).none { it.config.getOrNull(SemanticsProperties.Selected) == true })
        } finally { scene.close() }
    }

    private fun picker(width: Int, height: Int, profile: Profile, layout: AvatarPickerLayout,
        choose: (AvatarPick, String) -> Unit = { _, _ -> }) =
        ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(layout == AvatarPickerLayout.Television) { Surface(color = Palette.background) {
                AvatarPicker(profile, false, layout, {}, choose)
            } }
        }
    private fun frames(s: ImageComposeScene, offset: Int = 0) = repeat(16) { s.render((offset + it) * 16_666_667L).close(); Thread.sleep(3) }
    private fun nodes(s: ImageComposeScene) = s.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
    private fun flatten(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::flatten)
    private fun text(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun label(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
    private fun tiles(s: ImageComposeScene) = nodes(s).filter { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }
    private fun focused(s: ImageComposeScene) = nodes(s).single { it.config.getOrNull(SemanticsProperties.Focused) == true && it.config.contains(SemanticsActions.OnClick) }
    private fun click(s: ImageComposeScene, label: String) {
        val node = nodes(s).first { (text(it) == label || text(it.parentOrSelfClickable()) == label) && it.parentOrSelfClickable().config.contains(SemanticsActions.OnClick) }
        assertTrue(node.parentOrSelfClickable().config[SemanticsActions.OnClick].action!!.invoke())
    }
    private fun SemanticsNode.parentOrSelfClickable(): SemanticsNode =
        if (config.contains(SemanticsActions.OnClick)) this else parent?.parentOrSelfClickable() ?: this
    /** Writes a PNG for the PR when VIPTV_PROFILE_CAPTURE_DIR is set; lets the avatar images finish loading first. */
    private fun capture(scene: ImageComposeScene, name: String) {
        val path = System.getenv("VIPTV_PROFILE_CAPTURE_DIR") ?: return
        repeat(40) { scene.render((200 + it) * 16_666_667L).close(); Thread.sleep(50) }
        File(path).mkdirs()
        scene.render(1_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    }
}
