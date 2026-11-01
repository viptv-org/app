package org.viptv.app

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvFocusColumn
import org.viptv.app.connection.BrowsingConfiguration
import org.viptv.app.connection.Gateway
import org.viptv.app.connection.GatewaySnapshot
import org.viptv.app.connection.XtreamConnection
import org.viptv.app.features.settings.*
import org.viptv.app.preview.PreviewData
import org.viptv.app.preview.UiPreview
import org.viptv.app.state.Screen
import org.viptv.app.state.VodMatchProvider
import org.viptv.app.state.VodMatchesState
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvDpadPassTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private class Tv(content: @Composable () -> Unit) : AutoCloseable {
        private var focusManager: FocusManager? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            focusManager = LocalFocusManager.current
            ViptvTheme(true) { content() }
        }
        private var frame = 0L
        suspend fun frames(count: Int = 6) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); yield() }
        suspend fun settle() { frames(8); delay(80); frames(6) }
        /** Desktop has no D-pad focus search, so an unconsumed arrow moves focus as Android TV would. */
        suspend fun press(key: Key, times: Int = 1) = repeat(times) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            val direction = when (key) {
                Key.DirectionUp -> FocusDirection.Up; Key.DirectionDown -> FocusDirection.Down
                Key.DirectionLeft -> FocusDirection.Left; Key.DirectionRight -> FocusDirection.Right
                else -> null
            }
            if (!consumed && direction != null) requireNotNull(focusManager).moveFocus(direction)
            frames(6)
        }
        suspend fun ok() {
            val node = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
                .first { it.config.getOrNull(SemanticsProperties.Focused) == true }
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            frames(6)
        }
        fun focused(): String? {
            val node = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
                .firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true } ?: return null
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
            return text.ifBlank { description }
        }
        override fun close() = scene.close()
    }

    private fun settings(section: TvSettingsSection, addons: List<IntegrationConnection> = emptyList(),
        gateways: (@Composable (FocusRequester, FocusRequester, Float) -> Unit)? = null,
        xtream: (@Composable (FocusRequester, FocusRequester, Float) -> Unit)? = null,
        vodMatches: (@Composable (FocusRequester, FocusRequester, Float) -> Unit)? = null,
        busy: () -> Boolean = { false }, toggle: (String, Boolean) -> Unit = { _, _ -> }): @Composable () -> Unit = {
        TvSettingsScreen(false, addons, addons.map { it.id }.toSet(), busy(), null, null, {}, toggle, {}, {}, PreviewData.profile,
            initialSection = section, prefs = TvSettingsPreferences(), change = {}, gateways = gateways, xtream = xtream, vodMatches = vodMatches)
    }

    @Test fun playerPillsAreChainedSoSourceAndNextEpisodeAreReachable() = onEdt {
        Tv { UiPreview(television = true, screen = Screen.Player, tvDetailVariant = "Controls") }.use { tv ->
            tv.settle()
            tv.press(Key.DirectionUp)
            tv.settle()
            tv.press(Key.DirectionUp)
            assertEquals("Audio & subtitles", tv.focused())
            tv.press(Key.DirectionRight)
            assertEquals("Source · 4K", tv.focused())
            tv.press(Key.DirectionRight)
            assertEquals("Next episode", tv.focused())
            tv.press(Key.DirectionLeft, 2)
            assertEquals("Audio & subtitles", tv.focused())
            tv.press(Key.DirectionLeft)
            assertEquals("Episodes", tv.focused())
            tv.press(Key.DirectionLeft)
            assertEquals("Episodes", tv.focused(), "Left stops at the first pill")
        }
    }

    @Test fun columnRowsThatAppearAboveAreReachedInLayoutOrder() = onEdt {
        var top by mutableStateOf(false)
        val topFocus = FocusRequester()
        Tv {
            TvFocusColumn(Modifier.fillMaxSize()) {
                if (top) TvAction(Modifier.fillMaxWidth().height(80.dp), 1f, requester = topFocus, activate = {}) { Text("Top") }
                TvAction(Modifier.fillMaxWidth().height(80.dp), 1f, activate = {}) { Text("Bottom") }
            }
        }.use { tv ->
            tv.settle()
            top = true
            tv.settle()
            topFocus.requestFocus(); tv.frames()
            assertEquals("Top", tv.focused())
            tv.press(Key.DirectionDown)
            assertEquals("Bottom", tv.focused())
            tv.press(Key.DirectionDown)
            assertEquals("Bottom", tv.focused(), "Down clamps at the last row")
            tv.press(Key.DirectionUp)
            assertEquals("Top", tv.focused())
            tv.press(Key.DirectionUp)
            assertEquals("Top", tv.focused(), "Up clamps at the first row")
        }
    }

    @Test fun gatewaysPageOpensWithoutNestingTheListInAScroller() = onEdt {
        val snapshot = GatewaySnapshot(listOf(
            Gateway("g1", "Alpha gateway", "https://alpha.invalid", "alpha", 1, true, 1, true),
            Gateway("g2", "Beta gateway", "https://beta.invalid", "beta", 2, true, 1, true)))
        val page: @Composable (FocusRequester, FocusRequester, Float) -> Unit = { first, sidebar, scale ->
            TvGatewaySettings(snapshot, emptyMap(), false, null, null, null, {}, {}, { _, _ -> }, scale, first, sidebar)
        }
        Tv(settings(TvSettingsSection.Gateways, gateways = page)).use { tv ->
            tv.settle()
            assertTrue(tv.focused().orEmpty().startsWith("Alpha gateway"), "first gateway is focused: ${tv.focused()}")
            tv.press(Key.DirectionDown)
            assertEquals("Check capacity", tv.focused())
            tv.press(Key.DirectionDown)
            assertTrue(tv.focused().orEmpty().startsWith("Beta gateway"), "second gateway is reachable: ${tv.focused()}")
            tv.press(Key.DirectionLeft)
            assertEquals("Playback gateways", tv.focused(), "Left returns to the selected sidebar row")
        }
    }

    @Test fun xtreamRetryKeepsFocusWhileTheListLoadsAndHandsItToTheListAfterwards() = onEdt {
        var connections by mutableStateOf<List<XtreamConnection>?>(null)
        var busy by mutableStateOf(false)
        val page: @Composable (FocusRequester, FocusRequester, Float) -> Unit = { first, sidebar, scale ->
            TvXtreamConnections(connections, null, busy, null, null, true, "managed", scale, first, sidebar,
                {}, { _, _, _, _, _ -> }, { _, _, _ -> }, { _, _ -> }, {}, {}, {})
        }
        Tv(settings(TvSettingsSection.Playback, xtream = page)).use { tv ->
            tv.settle()
            tv.press(Key.DirectionLeft)
            tv.press(Key.DirectionDown, 7)
            assertEquals("Xtream connections", tv.focused())
            tv.ok()
            tv.settle()
            assertEquals("Retry", tv.focused(), "the page takes focus")
            busy = true
            tv.settle()
            assertEquals("Retry", tv.focused(), "focus survives the load starting")
            connections = emptyList()
            busy = false
            tv.settle()
            assertEquals("Add an Xtream connection", tv.focused(), "focus follows the page once its list arrives")
            tv.press(Key.DirectionLeft)
            assertEquals("Xtream connections", tv.focused())
        }
    }

    @Test fun addonSearchChipLeadsBackToTheSelectedSidebarRow() = onEdt {
        val addons = listOf(
            IntegrationConnection("a", "stremio", "acc", null, "Alpha", true, 1, listOf(Capability.SOURCES)),
            IntegrationConnection("b", "stremio", "acc", null, "Beta", true, 1, listOf(Capability.METADATA)))
        Tv(settings(TvSettingsSection.Addons, addons)).use { tv ->
            tv.settle()
            tv.press(Key.DirectionUp)
            val reached = buildList { repeat(4) { tv.press(Key.DirectionLeft); add(tv.focused()) } }
            assertEquals("Add-ons", reached.last(), "Left from the chips lands on the selected row: $reached")
        }
    }

    @Test fun providerRowsLeadBackToTheSidebar() = onEdt {
        val sidebar = FocusRequester()
        fun descriptor(id: String, name: String) = PluginDescriptor(id, name, 1, listOf(ExecutionMode.SERVER), listOf(Capability.DISCOVERY))
        fun connection(id: String, plugin: String) = IntegrationConnection(id, plugin, "acc", null, plugin, true, 1, listOf(Capability.DISCOVERY))
        val configuration = BrowsingConfiguration(
            ProfileConfiguration(Scope("c", "acc", "p", 1), ExecutionMode.SERVER, "P", browsing_plugin = "simkl", assignments = emptyList()),
            listOf(connection("c1", "simkl"), connection("c2", "stremio")))
        val decoy = FocusRequester()
        Tv {
            Row(Modifier.fillMaxSize()) {
                Column {
                    Box(Modifier.size(200.dp, 400.dp).semantics { contentDescription = "decoy" }.focusRequester(decoy).focusable())
                    Spacer(Modifier.height(300.dp))
                    Box(Modifier.size(200.dp, 80.dp).semantics { contentDescription = "sidebar" }.focusRequester(sidebar).focusable())
                }
                Column(Modifier.width(800.dp)) {
                    BrowsingProviderSettings(configuration, listOf(descriptor("simkl", "Simkl"), descriptor("stremio", "Stremio addons")),
                        false, {}, tv = true, scale = 1f, left = sidebar)
                }
            }
        }.use { tv ->
            tv.settle()
            decoy.requestFocus(); tv.frames()
            tv.press(Key.DirectionRight)
            assertTrue(tv.focused().orEmpty().let { it == "Simkl" || it == "Stremio addons" }, "a provider row is focused: ${tv.focused()}")
            tv.press(Key.DirectionLeft)
            assertEquals("sidebar", tv.focused())
        }
    }

    @Test fun vodMatchesSearchChipLeadsBackToTheSelectedSidebarRow() = onEdt {
        val state = VodMatchesState(providers = listOf(VodMatchProvider(1, "Home Xtream")), loaded = true)
        val page: @Composable (FocusRequester, FocusRequester, Float) -> Unit = { first, sidebar, scale ->
            TvVodMatchesSettings(state, scale, first, sidebar, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
        val vod = settings(TvSettingsSection.VodMatches, vodMatches = page)
        Tv(vod).use { tv ->
            tv.settle()
            assertEquals("Search", tv.focused())
            tv.press(Key.DirectionLeft)
            assertEquals("VOD matches", tv.focused(), "Left from the first chip lands on the selected row")
        }
    }

    @Test fun togglingAnAddonKeepsFocusOnItsRowWhileTheRequestRuns() = onEdt {
        var busy by mutableStateOf(false)
        val addons = listOf(
            IntegrationConnection("a", "stremio", "acc", null, "Alpha", true, 1, listOf(Capability.SOURCES)),
            IntegrationConnection("b", "stremio", "acc", null, "Beta", true, 1, listOf(Capability.METADATA)))
        Tv(settings(TvSettingsSection.Addons, addons, busy = { busy }, toggle = { _, _ -> busy = true })).use { tv ->
            tv.settle()
            assertTrue(tv.focused().orEmpty().contains("Alpha"), "the first add-on is focused: ${tv.focused()}")
            tv.ok()
            tv.settle()
            assertTrue(busy)
            assertTrue(tv.focused().orEmpty().contains("Alpha"), "focus stays on the toggled row: ${tv.focused()}")
            busy = false
            tv.settle()
            tv.press(Key.DirectionDown)
            assertTrue(tv.focused().orEmpty().contains("Beta"), "the list is still walkable: ${tv.focused()}")
        }
    }
}
