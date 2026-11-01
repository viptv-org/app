package org.viptv.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.Gateway
import org.viptv.app.connection.GatewaySnapshot
import org.viptv.app.features.settings.TvGatewaySettings
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvGatewaySettingsFocusTest {
    @get:Rule val uiThread = UiThreadRule()

    private var focusManager: FocusManager? = null
    private var clock = 0L

    private val gateways = listOf(
        Gateway("g1", "Beta transcoder", "https://gateway.example/", "embedez-kmp", 100, true, 3, can_manage = true),
        Gateway("g2", "Shared edge", "https://edge.example/", "public", 200, false, 1, can_manage = false),
    )

    private fun scene(
        snapshot: GatewaySnapshot? = GatewaySnapshot(gateways), error: String? = null, loadError: String? = null,
    ) = ImageComposeScene(1920, 1080, Density(1f)) {
        ViptvTheme(true) {
            focusManager = LocalFocusManager.current
            val first = remember { FocusRequester() }
            val left = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                repeat(30) {
                    withFrameNanos { }
                    if (first.requestFocus()) return@LaunchedEffect
                }
            }
            TvGatewaySettings(snapshot, emptyMap(), false, error, null, loadError,
                {}, {}, { _, _ -> }, 1f, first, left)
        }
    }

    private suspend fun frames(scene: ImageComposeScene) = repeat(16) { scene.render(clock++ * 16_666_667L).close(); delay(4) }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode): String = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun focused(scene: ImageComposeScene) = nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
    private suspend fun assertFocused(scene: ImageComposeScene, label: String) {
        frames(scene)
        val now = focused(scene)?.let(::text).orEmpty()
        assertTrue(now.startsWith(label), "expected focus on $label, was '$now'")
    }
    private suspend fun press(scene: ImageComposeScene, key: Key) {
        requireNotNull(focusManager).moveFocus(when (key) {
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionLeft -> FocusDirection.Left
            else -> FocusDirection.Right
        })
        frames(scene)
    }

    @Test fun downFromTheFirstGatewayReachesItsCheckAction() = runBlocking {
        val scene = scene()
        try {
            frames(scene)
            assertFocused(scene, "Beta transcoder")
            press(scene, Key.DirectionDown)
            assertFocused(scene, "Check capacity")
        } finally { scene.close() }
    }

    private fun texts(scene: ImageComposeScene) = nodes(scene).map(::text)

    @Test fun showsLoadingTextWhileTheListLoads() = runBlocking {
        val scene = scene(snapshot = null)
        try {
            frames(scene)
            val shown = texts(scene)
            assertTrue("Loading gateways…" in shown, "shown: $shown")
            assertTrue("Retry" !in shown && "No gateways are configured for your account." !in shown, "shown: $shown")
        } finally { scene.close() }
    }

    @Test fun showsTheEmptyStateForAnEmptyList() = runBlocking {
        val scene = scene(snapshot = GatewaySnapshot(emptyList()))
        try {
            frames(scene)
            val shown = texts(scene)
            assertTrue("No gateways are configured for your account." in shown, "shown: $shown")
            assertTrue("Loading gateways…" !in shown, "shown: $shown")
        } finally { scene.close() }
    }

    @Test fun showsTheLoadErrorWithAFocusedRetry() = runBlocking {
        val scene = scene(snapshot = null, loadError = "Could not load gateways.")
        try {
            frames(scene)
            val shown = texts(scene)
            assertTrue("Could not load gateways." in shown && "Retry" in shown, "shown: $shown")
            assertTrue("Loading gateways…" !in shown, "shown: $shown")
            assertFocused(scene, "Retry")
        } finally { scene.close() }
    }

    @Test fun showsTheActionErrorInPlaceOfTheLoadingText() = runBlocking {
        val scene = scene(snapshot = null, error = "Gateway request failed.")
        try {
            frames(scene)
            val shown = texts(scene)
            assertTrue("Gateway request failed." in shown && "Loading gateways…" !in shown, "shown: $shown")
        } finally { scene.close() }
    }
}
