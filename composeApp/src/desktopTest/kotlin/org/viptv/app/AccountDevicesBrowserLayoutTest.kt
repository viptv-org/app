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
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AccountDevicesBrowserLayoutTest {
    @Test fun browserAccountLayoutUsesTheSuppliedSidebarAndContentGeometry() {
        val scene = ImageComposeScene(1440,860,Density(1f)) { Fixture() }
        try {
            repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
            fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node)+node.children.flatMap(::flatten)
            val nodes = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
            val titles = nodes.filter { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Devices" } == true }
            assertTrue(titles.any { it.boundsInRoot.left in 344f..346f && it.boundsInRoot.top in 99f..102f })
            val rows = nodes.filter { it.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Living room TV" } == true }
            assertEquals(1,rows.size)
            assertTrue(rows.single().boundsInRoot.right <= 1105f)
            System.getenv("VIPTV_ACCOUNT_CAPTURE_DIR")?.let { directory ->
                File(directory).mkdirs()
                scene.render(21 * 16_666_667L).use { image -> image.encodeToData()!!.use {
                    File(directory,"account-devices-browser.png").writeBytes(it.bytes)
                } }
            }
        } finally { scene.close() }
    }

    @Composable private fun Fixture() {
        val snapshot = Json.decodeFromString<AccountDevices>(accountFixture)
        ViptvTheme {
            Surface(Modifier.fillMaxSize(),color = Palette.background) {
                Column {
                    TopNavigation(Screen.Settings,false,{},Profile("1","Viewer",null))
                    SettingsScreen(false,emptyList(),emptySet(),false,null,null,{}, { _, _ -> },{}, {},{},
                        initialSection = "Devices",devices = { compact, back ->
                            AccountDevicesScreen(snapshot,false,null,{}, {},{},compact,if (compact) back else null)
                        })
                }
            }
        }
    }
}
