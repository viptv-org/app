package org.viptv.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.ProfilesScreen
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ProfilePickerAvatarTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val advanced = Profile("1", "Stremio import QA", avatar = null, avatarUrl = "https://avatars.example/art.png")

    @Test fun everyPickerWidthDrawsTheProfileAvatar() {
        // Phone picks the compact grid, tablet/web the centred row, desktop the chrome tiles.
        for ((width, desktop) in listOf(390 to false, 800 to false, 1440 to true)) {
            val scene = ImageComposeScene(width, 900, Density(1f)) { ViptvTheme(pointer = desktop) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val compact = maxWidth < 640.dp
                    ProfilesScreen(listOf(advanced), busy = false, error = null, local = false, managing = false,
                        compact = compact, desktop = desktop,
                        select = {}, edit = {}, manage = {}, done = {}, create = {})
                }
            } }
            try {
                frames(scene)
                val avatar = nodes(scene).firstOrNull {
                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d == advanced.name } == true
                }
                assertNotNull(avatar, "$width: the tile draws the profile avatar")
                assertFalse(nodes(scene).any { text(it) == "S" }, "$width: no bare initial replaces the avatar")
            } finally { scene.close() }
        }
    }

    private fun frames(scene: ImageComposeScene) = repeat(16) { scene.render(it * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}