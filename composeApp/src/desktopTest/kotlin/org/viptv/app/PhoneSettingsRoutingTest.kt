package org.viptv.app

import org.junit.Rule
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PhoneSettingsRoutingTest {
    @get:Rule val uiThread = UiThreadRule()
    @Test fun phoneOpensAvailablePreferencesAndReturnsToSettings() {
        val scene = ImageComposeScene(390,844,Density(1f)) {
            ViptvTheme { SettingsScreen(false,emptyList(),emptySet(),false,null,null,{}, { _, _ -> },{}, {},{},
                preferences = { Text("Preferences from the selected profile") }) }
        }
        var frame = 0L
        fun render() = repeat(12) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node)+node.children.flatMap(::flatten)
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun matches(node: SemanticsNode, value: String) = node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true
        fun click(value: String) {
            val node = nodes().first { matches(it,value) && it.config.contains(SemanticsActions.OnClick) }
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()); render()
        }
        try {
            render()
            for (label in listOf("Playback","Subtitles & audio")) {
                click(label)
                assertTrue(nodes().any { matches(it,"Preferences from the selected profile") })
                click("Back")
                assertTrue(nodes().any { matches(it,"Settings") })
            }
        } finally { scene.close() }
    }
}
