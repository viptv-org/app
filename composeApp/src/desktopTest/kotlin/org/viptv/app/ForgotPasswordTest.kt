package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
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
class ForgotPasswordTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun thePhoneForgotPasswordLinkOpensTheServerWebSignInPage() = verify(mobile = true)
    @Test fun theDesktopForgotPasswordLinkOpensTheServerWebSignInPage() = verify(mobile = false)

    private fun verify(mobile: Boolean) {
        var opened: String? = null
        val scene = ImageComposeScene(if (mobile) 390 else 1280, if (mobile) 844 else 800, Density(1f)) { ViptvTheme {
            if (mobile) MobilePasswordPanel("https://watch.example", false, null, {}, { _, _, _ -> }, forgot = { opened = it })
            else DesktopPasswordPanel("https://watch.example", false, null, {}, { _, _, _ -> }, forgot = { opened = it })
        } }
        try {
            repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(3) }
            val link = nodes(scene).singleOrNull { text(it) == "Forgot password?" && it.config.contains(SemanticsActions.OnClick) }
                ?: fail("${if (mobile) "phone" else "desktop"}: no Forgot password link")
            assertTrue(link.config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals("https://watch.example", opened)
        } finally { scene.close() }
    }

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}