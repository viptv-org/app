package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.components.LocalSelectPresentation
import org.viptv.app.components.SelectPresentation
import org.viptv.app.components.TextSelect
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalComposeUiApi::class)
class ClearTextButtonTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun theSelectFieldClearTextIsA44dpButton() {
        var frame = 0L
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme(false) {
                CompositionLocalProvider(LocalSelectPresentation provides SelectPresentation.Sheet) {
                    Surface(Modifier.fillMaxSize(), color = Palette.background) {
                        TextSelect("Rating", "8.0", {})
                    }
                }
            }
        }
        try {
            repeat(20) { scene.render(frame++ * 16_666_667L).close() }
            val trigger = nodes(scene).singleOrNull {
                it.config.contains(SemanticsActions.OnClick) && text(it) == "Rating: 8.0"
            } ?: fail("no Rating trigger: " + texts(scene))
            assertTrue(trigger.config[SemanticsActions.OnClick].action!!.invoke(), "the trigger opens the panel")
            repeat(40) { scene.render(frame++ * 16_666_667L).close() }
            val clear = nodes(scene).singleOrNull {
                it.config.contains(SemanticsActions.OnClick) &&
                    it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf("Clear text")
            } ?: fail("no Clear text button: " + texts(scene))
            assertEquals(Role.Button, clear.config.getOrNull(SemanticsProperties.Role), "Clear text is Role.Button")
            assertTrue(clear.boundsInRoot.width >= 44f && clear.boundsInRoot.height >= 44f,
                "Clear text is a 44dp target: ${clear.boundsInRoot}")
        } finally { scene.close() }
    }

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun text(node: SemanticsNode) =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun texts(scene: ImageComposeScene) = nodes(scene).map(::text).filter(String::isNotBlank)
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}