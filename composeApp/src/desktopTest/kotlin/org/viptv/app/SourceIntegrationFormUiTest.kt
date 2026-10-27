package org.viptv.app

import org.junit.Rule
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.features.settings.SourceIntegrationForm
import org.viptv.app.theme.ViptvTheme
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SourceIntegrationFormUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @Test fun credentialFormKeepsCancelAndSaveVisibleAboveKeyboardWithScrollableProviderChoices() {
        val schema = SourceIntegrationSchema("fixture", "Fixture source", "NeedsConfiguration", true,
            listOf(SourceCredentialField("key", "API key", required = true, secret = true)),
            provider_choices = (1..20).map { SourceProviderChoice("provider-$it", "Provider $it") })
        var saved: JsonObject? = null
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            SourceIntegrationForm(schema, false, null, {}, keyboardInsets = WindowInsets(bottom = 320), save = { saved = it })
        } }
        try {
            frames(scene)
            for (label in listOf("Cancel", "Save")) {
                val bounds = action(scene, label).boundsInRoot
                assertTrue(bounds.top >= 0f && bounds.bottom <= 524f, "$label hidden below keyboard: $bounds")
            }
            assertTrue(nodes(scene).any { it.config.contains(SemanticsActions.ScrollBy) })
            val field = nodes(scene).single { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config.contains(SemanticsProperties.Password))
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("fixture-only")))
            frames(scene, 20)
            System.getenv("VIPTV_INTEGRATION_CAPTURE_DIR")?.let { path ->
                File(path).mkdirs(); scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "source-form-keyboard.png").writeBytes(it.bytes) } }
            }
            assertTrue(action(scene, "Save").config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals("fixture-only", saved?.get("key")?.jsonPrimitive?.content)
            assertEquals(emptyList(), saved?.get("providers")?.jsonArray?.toList())
        } finally { scene.close() }
    }
    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun action(scene: ImageComposeScene, label: String) = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
}
