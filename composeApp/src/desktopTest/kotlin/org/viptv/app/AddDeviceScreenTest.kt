package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import org.viptv.app.connection.*
import org.viptv.app.features.settings.*
import org.viptv.app.theme.*
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AddDeviceScreenTest {
    @get:Rule val uiThread = UiThreadRule()
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    @Test fun aSignedInPhoneOpensTheNativeAddDeviceScreenFromWatchOnTv() {
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine { error("The screen makes no request until a code is entered") }) { configureTransport() }, browserMode = false)
        val scene = ImageComposeScene(822, 1720, Density(2f)) { Fixture(remote) }
        try {
            fun render(from: Int) = repeat(12) { scene.render((from + it) * 16_666_667L).close(); Thread.sleep(4) }
            fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
            fun texts() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
                .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
            render(0)
            assertTrue("Sign in a TV or another device" in texts())
            val open = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
                .first { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Watch on TV" } == true && node.config.contains(SemanticsActions.OnClick) }
            open.config[SemanticsActions.OnClick].action!!.invoke()
            render(20)
            assertTrue("Add a device" in texts(), texts().toString())
            assertFalse("Open TV sign-in" in texts(), "No browser hand-off on a signed-in phone")
            assertTrue("Continue" in texts())
            System.getenv("VIPTV_ADD_DEVICE_CAPTURE_DIR")?.let { directory ->
                File(directory).mkdirs()
                scene.render(40 * 16_666_667L).use { image -> image.encodeToData()!!.use { File(directory, "add-device-phone.png").writeBytes(it.bytes) } }
            }
        } finally { scene.close(); remote.close() }
    }

    @Composable private fun Fixture(remote: RemoteConnection) {
        ViptvTheme {
            Surface(Modifier.fillMaxSize(), color = Palette.background) {
                SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
                    addDevice = { compact -> AddDeviceScreen(remote, compact) })
            }
        }
    }
}
