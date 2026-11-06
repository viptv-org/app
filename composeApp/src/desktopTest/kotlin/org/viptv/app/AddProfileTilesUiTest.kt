package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.ProfilesScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class AddProfileTilesUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val three = listOf(Profile("1", "vynxc", avatar = null), Profile("2", "zayne", avatar = null),
        Profile("3", "cedes", avatar = null))
    private val twelve = (1..12).map { Profile("$it", "profile $it", avatar = null) }

    @Test fun everyPickerOffersTheAddTileBeforeTheLimit() {
        for (picker in Pickers) {
            val scene = picker.render(three, local = true)
            try {
                val add = picker.addTile(scene)
                assertNull(add.config.getOrNull(SemanticsProperties.Disabled), "${picker.name}: the add tile is offered")
                assertTrue(add.config.contains(SemanticsActions.OnClick), "${picker.name}: the add tile opens the name dialog")
            } finally { scene.close() }
        }
    }

    // A TV shelf scrolls by focus, so a disabled add tile past the last profile cannot be reached there;
    // ProfileLimitTest holds the drawn-and-disabled rule for that picker.
    @Test fun thePickersThatScrollFreelyKeepTheAddTileAtTwelveAndDisableIt() {
        for (picker in Pickers - Pickers.last()) {
            val scene = picker.render(twelve, local = true)
            try {
                val add = picker.addTile(scene)
                assertTrue(add.config.contains(SemanticsProperties.Disabled), "${picker.name}: the add tile stays and is disabled")
            } finally { scene.close() }
        }
    }

    @Test fun aHouseholdThatCannotManageProfilesStillGetsNoAddTile() {
        for (picker in Pickers) {
            val scene = picker.render(three, local = false)
            try {
                assertTrue(picker.nodes(scene).none { picker.labels(it).contains("Add a profile") }, "${picker.name}: nothing to add")
            } finally { scene.close() }
        }
    }

    private class Picker(val name: String, val width: Int, val height: Int, val compact: Boolean, val desktop: Boolean,
        val television: Boolean) {
        fun render(profiles: List<Profile>, local: Boolean): ImageComposeScene {
            val scene = ImageComposeScene(width, height, Density(1f)) {
                ViptvTheme(television = television, pointer = !television) {
                    Box(Modifier.fillMaxSize().background(Palette.background)) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            ProfilesScreen(profiles, busy = false, error = null, local = local, managing = false,
                                compact = compact, desktop = desktop, television = television,
                                select = {}, edit = {}, manage = {}, done = {}, create = {})
                        }
                    }
                }
            }
            repeat(24) { scene.render(it * 16_666_667L).close() }
            return scene
        }

        fun addTile(scene: ImageComposeScene): SemanticsNode =
            nodes(scene).single { it.config.contains(SemanticsActions.OnClick) && labels(it).contains("Add a profile") }

        fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun labels(node: SemanticsNode): String = text(node) + " " +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    }

    private companion object {
        val Pickers = listOf(
            Picker("phone", 390, 1800, compact = true, desktop = false, television = false),
            Picker("desktop app", 2400, 900, compact = false, desktop = true, television = false),
            Picker("TV", 1920, 1080, compact = false, desktop = false, television = true))
    }
}