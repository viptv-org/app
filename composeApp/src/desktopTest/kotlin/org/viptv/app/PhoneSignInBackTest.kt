@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import java.io.File
import org.junit.Rule
import org.viptv.app.components.PageBackTag
import org.viptv.app.features.entry.MobileSignIn
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

class PhoneSignInBackTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun scene(startPairing: ((String) -> Unit)?) = ImageComposeScene(390, 844, Density(1f)) {
        ViptvTheme(pointer = false) {
            CompositionLocalProvider(
                LocalUriHandler provides object : UriHandler { override fun openUri(uri: String) {} },
                LocalClipboardManager provides object : ClipboardManager {
                    override fun getText(): AnnotatedString = AnnotatedString("")
                    override fun setText(text: AnnotatedString) {}
                },
            ) {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    MobileSignIn("https://example.invalid", false, null, null, { _, _, _ -> },
                        startPairing = startPairing, cancelPairing = {}, backChanged = {})
                }
            }
        }
    }

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render(it * 16_666_667L).close() }

    private fun backCount(scene: ImageComposeScene) = nodes(scene).count { it.config.getOrNull(SemanticsProperties.TestTag) == PageBackTag }

    private fun allSignInOptions(scene: ImageComposeScene) = nodes(scene).any {
        it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "All sign-in options" } == true
    }

    private fun click(scene: ImageComposeScene, label: String) {
        val node = nodes(scene).first {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true &&
                it.config.contains(SemanticsActions.OnClick)
        }
        node.config[SemanticsActions.OnClick].action!!.invoke()
    }

    @Test fun phoneSignInSubStepsKeepTheSingleSharedBackArrow() {
        for (startPairing in listOf<((String) -> Unit)?>(null, {})) {
            for (choice in listOf("Use a password", "Use another device", "Continue in browser")) {
                val scene = scene(startPairing)
                try {
                    frames(scene)
                    click(scene, choice)
                    frames(scene)
                    assertEquals(1, backCount(scene),
                        "$choice (pairing=${startPairing != null}) must keep the single shared back arrow")
                    assertFalse(allSignInOptions(scene),
                        "$choice must drop the duplicate All sign-in options link")
                } finally { scene.close() }
            }
        }
    }

    @Test fun capturePhoneSignInSubStepsAt390() {
        for ((choice, file) in listOf(
            "Use a password" to "phone-726-signin-password",
            "Use another device" to "phone-726-signin-device",
            "Continue in browser" to "phone-726-signin-browser",
        )) {
            val scene = scene(null)
            try {
                frames(scene)
                click(scene, choice)
                frames(scene)
                System.getenv("VIPTV_SIGNIN_BACK_CAPTURE_DIR")?.let { path ->
                    File(path).mkdirs()
                    scene.render(3_000_000_000L).use { image ->
                        image.encodeToData()!!.use { data -> File(path, "$file.png").writeBytes(data.bytes) }
                    }
                }
            } finally { scene.close() }
        }
    }
}