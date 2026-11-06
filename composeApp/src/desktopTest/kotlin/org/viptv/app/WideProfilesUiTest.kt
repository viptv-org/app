package org.viptv.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.ProfilesScreen
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class WideProfilesUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val household = listOf(Profile("1", "vynxc", avatar = null), Profile("2", "zayne", avatar = null),
        Profile("3", "cedes", avatar = null, locked = true))

    @Test fun tabletAndWebDrawTheBoardTiles() {
        for (width in listOf(768, 1024, 1280, 1440)) {
            val scene = picker(width) { ProfilesScreen(household, busy = false, error = null, local = true, managing = false,
                compact = false, desktop = false, select = {}, edit = {}, manage = {}, done = {}, create = {}) }
            try {
                val nodes = nodes(scene)
                assertTrue(nodes.any { text(it) == "Who’s watching?" }, "$width: the board heading")
                for (profile in household) {
                    val tile = nodes.single { it.config.contains(SemanticsActions.OnClick) && labels(it).contains(profile.name) }
                    assertEquals(150f, tile.size.width.toFloat(), "$width: ${profile.name} sits on a 150 dp tile")
                }
                val manage = nodes.single { it.config.contains(SemanticsActions.OnClick) && text(it) == "Manage profiles" }
                assertEquals(44f, manage.size.height.toFloat(), "$width: Manage profiles is 44 tall")
                assertTrue(nodes.any { it.config.contains(SemanticsActions.OnClick) && text(it) == "Add profile" }, "$width: the add tile")
            } finally { scene.close() }
        }
    }

    @Test fun aLockedProfileDrawsALockGlyphInsteadOfTheWord() {
        val scene = picker(1280) { ProfilesScreen(household, busy = false, error = null, local = true, managing = false,
            compact = false, desktop = false, select = {}, edit = {}, manage = {}, done = {}, create = {}) }
        try {
            val nodes = nodes(scene)
            assertFalse(nodes.any { text(it) == "Locked" }, "the picker never writes the word Locked")
            val locked = nodes.single { it.config.contains(SemanticsActions.OnClick) && labels(it).contains("cedes") }
            assertTrue(locked.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains("Locked"),
                "the lock is a glyph beside the name")

            val open = picker(1280) { ProfilesScreen(household, busy = false, error = null, local = true, managing = false,
                compact = false, desktop = false, select = {}, edit = {}, manage = {}, done = {}, create = {}) }
            try {
                assertFalse(nodes(open).any { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains("Locked") && labels(it).contains("vynxc") },
                    "an unlocked profile carries no lock")
            } finally { open.close() }
        } finally { scene.close() }
    }

    @Test fun theAddTileShowsDisabledAtTwelveProfiles() {
        val twelve = (1..12).map { Profile("$it", "profile $it", avatar = null) }
        val scene = picker(2400) { ProfilesScreen(twelve, busy = false, error = null, local = true, managing = false,
            compact = false, desktop = false, select = {}, edit = {}, manage = {}, done = {}, create = {}) }
        try {
            val add = nodes(scene).single { it.config.contains(SemanticsActions.OnClick) && labels(it).contains("Add profile") }
            assertTrue(add.config.contains(SemanticsProperties.Disabled), "the add tile is drawn and disabled at the profile limit")
        } finally { scene.close() }
    }

    @Test fun aHouseholdThatCannotManageProfilesGetsNoManageOrAddEntry() {
        val scene = picker(1280) { ProfilesScreen(household, busy = false, error = null, local = false, managing = false,
            compact = false, desktop = false, select = {}, edit = {}, manage = {}, done = {}, create = {}) }
        try {
            val nodes = nodes(scene)
            assertFalse(nodes.any { text(it) == "Manage profiles" })
            assertFalse(nodes.any { text(it) == "Add profile" })
        } finally { scene.close() }
    }

    @Test fun managingOpensTheEditorAndDoneClosesIt() {
        var edited: String? = null
        var finished = 0
        val scene = picker(1280) { ProfilesScreen(household, busy = false, error = null, local = true, managing = true,
            compact = false, desktop = false, select = {}, edit = { edited = it }, manage = {}, done = { finished++ }, create = {}) }
        try {
            val nodes = nodes(scene)
            assertTrue(nodes.any { text(it) == "Manage profiles" }, "the managed heading")
            assertTrue(nodes.single { it.config.contains(SemanticsActions.OnClick) && text(it) == "Done" }
                .config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, finished)
            assertTrue(nodes.single { it.config.contains(SemanticsActions.OnClick) && labels(it).contains("zayne") }
                .config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals("2", edited)
        } finally { scene.close() }
    }

    private fun picker(width: Int, content: @androidx.compose.runtime.Composable () -> Unit): ImageComposeScene {
        val scene = ImageComposeScene(width, 900, Density(1f)) {
            ViptvTheme(pointer = true) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    check(maxWidth >= 640.dp)
                    content()
                }
            }
        }
        repeat(20) { scene.render(it * 16_666_667L).close() }
        return scene
    }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun labels(node: SemanticsNode) = text(node) + " " +
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
}