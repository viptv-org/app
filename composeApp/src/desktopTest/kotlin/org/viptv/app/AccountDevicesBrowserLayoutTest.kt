package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.serialization.json.*
import org.viptv.app.components.TopNavigation
import org.viptv.app.connection.AccountDevices
import org.viptv.app.connection.Profile
import org.viptv.app.features.settings.*
import org.viptv.app.state.Screen
import org.viptv.app.theme.*
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AccountDevicesBrowserLayoutTest {
    @get:Rule val uiThread = UiThreadRule()
    @Test fun browserAccountLayoutUsesTheSuppliedSidebarAndContentGeometry() {
        var addonLoads = 0
        val scene = ImageComposeScene(1440,860,Density(1f)) { Fixture { addonLoads++ } }
        try {
            repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
            fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node)+node.children.flatMap(::flatten)
            val nodes = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
            val titles = nodes.filter { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Devices" } == true }
            assertTrue(titles.any { it.boundsInRoot.left in 344f..346f && it.boundsInRoot.top in 99f..102f })
            val rows = nodes.filter { it.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Living room TV" } == true }
            assertEquals(1,rows.size)
            assertTrue(rows.single().boundsInRoot.right <= 1105f)
            assertEquals(1,addonLoads,"Add-on configuration preloads so counts are known before the section opens")
            System.getenv("VIPTV_ACCOUNT_CAPTURE_DIR")?.let { directory ->
                File(directory).mkdirs()
                scene.render(21 * 16_666_667L).use { image -> image.encodeToData()!!.use {
                    File(directory,"account-devices-browser.png").writeBytes(it.bytes)
                } }
            }
            val addonAction = nodes.single { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Add-ons" } == true && node.config.contains(SemanticsActions.OnClick) }
            addonAction.config[SemanticsActions.OnClick].action!!.invoke()
            repeat(12) { scene.render((it+30)*16_666_667L).close();Thread.sleep(4) }
            assertEquals(2,addonLoads,"Add-on configuration refreshes when its section opens (1 eager + 1 on open)")
        } finally { scene.close() }
    }

    @Composable private fun Fixture(addonsRequested: () -> Unit) {
        val snapshot = Json.decodeFromString<AccountDevices>(accountFixture)
        ViptvTheme {
            Surface(Modifier.fillMaxSize(),color = Palette.background) {
                Column {
                    TopNavigation(Screen.Settings,false,{},Profile("1","Viewer",null))
                    SettingsScreen(false,emptyList(),emptySet(),false,null,null,{}, { _, _ -> },{}, {},{},
                        initialSection = "Devices",addonsRequested = addonsRequested,devices = { compact, back ->
                            AccountDevicesScreen(snapshot,false,null,{}, {},{},compact,if (compact) back else null)
                        },browser = true)
                }
            }
        }
    }
}
