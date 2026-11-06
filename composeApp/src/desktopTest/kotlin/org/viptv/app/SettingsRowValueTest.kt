package org.viptv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.*
import org.viptv.app.connection.Profile
import org.viptv.app.features.settings.ProfilePageActions
import org.viptv.app.features.settings.ProfilePageValues
import org.viptv.app.features.settings.ProfileSettingsPage
import org.viptv.app.features.settings.SettingsItem
import org.viptv.app.features.settings.SettingsLayout
import org.viptv.app.features.settings.SettingsRowValue
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SettingsRowValueTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private var frame = 0L
    private val rating = "Movies up to PG-13 · Series up to TV-14"
    private val language = "Português (Brasil) · Português (Brasil)"

    private val values = ProfilePageValues(profile = Profile("2", "Child", null), local = false, kids = true, maxAge = 10,
        approvedTitles = 12, householdUnlocked = true, audioLanguage = "Português (Brasil)",
        subtitleLanguage = "Português (Brasil)", policy = parentKids(true, 10, "PG-13", "TV-14"))

    private fun scene(width: Int, height: Int, content: @androidx.compose.runtime.Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }

    private fun render(scene: ImageComposeScene) = repeat(12) { scene.render(++frame * 16_666_667L).close() }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }

    private fun textOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private fun node(scene: ImageComposeScene, startsWith: String): SemanticsNode =
        nodes(scene).firstOrNull { textOf(it).firstOrNull() == startsWith || textOf(it).singleOrNull() == startsWith }
            ?: fail("no node for '$startsWith' in ${nodes(scene).map(::textOf).filter(List<String>::isNotEmpty)}")

    private fun labelAndValueWidth(width: Int): Pair<Float, Float> {
        val scene = scene(width, 200) {
            Row { Text("Maturity limit", Modifier.weight(1f)); SettingsRowValue(rating) }
        }
        try {
            render(scene)
            return labelWidth(scene) to node(scene, rating).boundsInRoot.width
        } finally { scene.close() }
    }

    private fun labelWidth(scene: ImageComposeScene): Float =
        node(scene, "Maturity limit").boundsInRoot.width

    @Test fun theValueSharesTheRowInsteadOfTakingItOnAPhone() {
        val (label, value) = labelAndValueWidth(390)
        assertTrue(label > 150f, "the label keeps the larger share of a phone row, was $label wide")
        assertTrue(value < label, "the value is bounded by its share, was $value wide beside a $label label")
    }

    @Test fun aWideRowStillShowsTheWholeValue() {
        val (narrowLabel, narrowValue) = labelAndValueWidth(390)
        val (wideLabel, wideValue) = labelAndValueWidth(1280)
        assertTrue(narrowLabel > 150f, "the phone label stays readable, was $narrowLabel")
        assertTrue(wideValue > narrowValue, "the value grows with the row: $narrowValue at 390, $wideValue at 1280")
        assertTrue(wideValue > 200f, "a desktop row shows the whole limit, was $wideValue wide")
        assertTrue(wideLabel > wideValue, "the label keeps precedence on a desktop row too, $wideLabel beside $wideValue")
    }

    @Test fun everyProfileRowKeepsItsLabelOnAPhone() {
        val scene = scene(390, 900) {
            ProfileSettingsPage(values, false, null, 0L, ProfilePageActions(), SettingsLayout.Phone)
        }
        try {
            render(scene)
            for (row in listOf("Maturity limit", "Language", "Approved titles")) {
                val height = node(scene, row).boundsInRoot.height
                assertTrue(height < 120f, "$row keeps a settings-row height, was $height tall")
                assertTrue(height > 40f, "$row still draws its label and note, was $height tall")
            }
        } finally { scene.close() }
    }

    @Test fun theShellRowUsesTheSameShapeOnAPhone() {
        val scene = ImageComposeScene(390, 400, Density(1f)) {
            ViptvTheme { Column(Modifier.fillMaxSize()) {
                Surface(Modifier.fillMaxWidth(), color = Palette.background) {
                    SettingsItem("Watch on TV", Lucide.Monitor, subtitle = "Use this phone as a Vizio TV remote",
                        value = "Living room television, second floor") {}
                }
            } }
        }
        try {
            render(scene)
            val height = node(scene, "Watch on TV").boundsInRoot.height
            assertTrue(height < 120f, "the shell row keeps a settings-row height, was $height tall")
            assertTrue(height > 40f, "the shell row still draws its label and note, was $height tall")
        } finally { scene.close() }
    }
}