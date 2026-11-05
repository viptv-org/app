package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.entry.ConnectScreen
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SignInMethodListTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun thePhoneAndDesktopChoicesOfferNoEmailMethod() {
        verify(phone = true)
        verify(phone = false)
    }

    private fun verify(phone: Boolean) {
        val scene = ImageComposeScene(if (phone) 390 else 1280, if (phone) 844 else 720, Density(1f)) { ViptvTheme {
            ConnectScreen(false, "https://fixture.example", false, null, {}, { _, _, _ -> }, desktop = !phone, phone = phone)
        } }
        try {
            repeat(20) { scene.render(it * 16_666_667L).close() }
            val texts = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
                .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            assertTrue(texts.none { it.contains("Email", ignoreCase = true) }, "sign-in list still offers email: $texts")
            for (method in listOf("Continue in browser", "Use a password", "Use another device", "Use without an account"))
                assertTrue(texts.contains(method), "missing $method in $texts")
        } finally { scene.close() }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}