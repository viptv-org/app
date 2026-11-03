package org.viptv.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.entry.DesktopPasswordPanel
import org.viptv.app.features.entry.MobilePasswordPanel
import org.viptv.app.theme.ViptvTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalComposeUiApi::class)
class PasswordToggleTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun thePhoneRevealToggleIsA44dpButtonWithState() = toggleTarget("mobile") {
        MobilePasswordPanel("", false, null, {}, {}, { _, _, _ -> })
    }

    @Test fun theDesktopRevealToggleIsA44dpButtonWithState() = toggleTarget("desktop") {
        DesktopPasswordPanel("", false, null, {}, {}, { _, _, _ -> })
    }

    private fun toggleTarget(kind: String, panel: @Composable () -> Unit) {
        var frame = 0L
        val width = if (kind == "mobile") 390 else 1280
        val height = if (kind == "mobile") 844 else 800
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(false) { panel() } }
        try {
            repeat(20) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(3) }
            val toggle = nodes(scene).singleOrNull {
                it.config.contains(SemanticsActions.OnClick) &&
                    it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf("Show password")
            } ?: fail("$kind: no Show password toggle")
            assertEquals(Role.Button, toggle.config.getOrNull(SemanticsProperties.Role), "$kind: the toggle is Role.Button")
            assertTrue(toggle.boundsInRoot.width >= 44f && toggle.boundsInRoot.height >= 44f,
                "$kind: the toggle is a 44dp target: ${toggle.boundsInRoot}")
            assertTrue(toggle.config[SemanticsActions.OnClick].action!!.invoke(), "$kind: the toggle toggles")
            repeat(20) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(3) }
            val hidden = nodes(scene).singleOrNull {
                it.config.contains(SemanticsActions.OnClick) &&
                    it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf("Hide password")
            } ?: fail("$kind: the toggle did not switch to Hide password")
            assertEquals(Role.Button, hidden.config.getOrNull(SemanticsProperties.Role), "$kind: the toggled state is Role.Button")
        } finally { scene.close() }
    }

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}