package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.entry.DesktopSignIn
import org.viptv.app.features.entry.MobileSignIn
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SignInAccountEntryTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun thePhoneCreateAccountEntryOpensTheServerWebSignInPage() = verify(mobile = true)
    @Test fun theDesktopCreateAccountEntryOpensTheServerWebSignInPage() = verify(mobile = false)

    private fun verify(mobile: Boolean) {
        var opened: String? = null
        var count = 0
        val scene = ImageComposeScene(if (mobile) 390 else 1280, if (mobile) 844 else 800, Density(1f)) { ViptvTheme {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                if (mobile) MobileSignIn("https://watch.example", false, null, {}, { _, _, _ -> },
                    startPairing = null, openRegister = { opened = it; count++ })
                else DesktopSignIn("https://watch.example", false, null, {}, { _, _, _ -> },
                    startPairing = null, openRegister = { opened = it; count++ })
            }
        } }
        try {
            repeat(30) { scene.render(it * 16_666_667L).close() }
            val choice = nodes(scene).firstOrNull { node ->
                node.config.contains(SemanticsActions.OnClick) && subtreeText(node).contains("Create account")
            } ?: fail("${if (mobile) "phone" else "desktop"}: no Create account entry")
            assertTrue(choice.config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals("https://watch.example", opened)
            assertEquals(1, count)
        } finally { scene.close() }
    }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)

    private fun subtreeText(node: SemanticsNode): String {
        val own = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        val children = node.children.flatMap { subtreeText(it).split(" ") }
        return (own + children).filter { it.isNotBlank() }.joinToString(" ")
    }
}
