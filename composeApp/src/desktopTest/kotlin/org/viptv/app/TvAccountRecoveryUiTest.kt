package org.viptv.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import org.viptv.app.features.settings.*
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class TvAccountRecoveryUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    @Test fun failedAccountFocusesRetryThenReplacesTheLoadingStateWithActualIdentity() = onEdt {
        var busy by mutableStateOf(false); var error by mutableStateOf<String?>(null)
        var info by mutableStateOf<TvAccountInfo?>(null); var loads = 0; var dashboards = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            TvSettingsScreen(false, emptyList(), emptySet(), busy, error, null, {}, { _, _ -> }, {}, {}, null,
                initialSection = TvSettingsSection.Account, account = info,
                accountRequested = { loads++; busy = true; error = null }, accountDashboard = { dashboards++ })
        } }
        try {
            frames(scene); assertEquals(1, loads)
            assertTrue(action(scene, "Loading account…").config.contains(SemanticsProperties.Disabled))
            assertFalse(nodes(scene).any { text(it).startsWith("Username") || text(it).startsWith("Devices") })
            busy = false; error = "Could not reach your server. Check the connection and try again."; frames(scene)
            assertEquals(true, action(scene, "Retry account").config.getOrNull(SemanticsProperties.Focused))
            assertEquals(1, nodes(scene).count { text(it) == error }); capture(scene, "tv-account-load-failure")
            click(scene, "Open server dashboard"); assertEquals(1, dashboards)
            click(scene, "Retry account"); frames(scene); assertEquals(2, loads)
            assertTrue(action(scene, "Loading account…").config.contains(SemanticsProperties.Disabled))
            info = TvAccountInfo(username = "viewer"); busy = false; frames(scene)
            assertFalse(nodes(scene).any { text(it) == "Retry account" })
            assertEquals(true, action(scene, "Username viewer").config.getOrNull(SemanticsProperties.Focused))
            assertTrue(nodes(scene).any { text(it) == "Devices Managed on web" })
            click(scene, "Refresh account"); assertEquals(3, loads)
        } finally { scene.close() }
    }

    @Test fun cachedIdentityRemainsVisibleOnFailureAndDashboardFallbackStillWorks() = onEdt {
        var busy by mutableStateOf(false); var error by mutableStateOf<String?>(null); var loads = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            TvSettingsScreen(false, emptyList(), emptySet(), busy, error, null, {}, { _, _ -> }, {}, {}, null,
                initialSection = TvSettingsSection.Account, account = TvAccountInfo(username = "cached"),
                accountRequested = { loads++; busy = true; error = null }, dashboardAddress = "https://fixture.example/")
        } }
        try {
            frames(scene); busy = false; error = "This account cannot perform that action."; frames(scene)
            assertTrue(nodes(scene).any { text(it) == "Username cached" })
            assertEquals(1, nodes(scene).count { text(it) == error })
            click(scene, "Username cached"); frames(scene)
            assertTrue(nodes(scene).any { text(it) == "https://fixture.example/" })
            click(scene, "Close"); frames(scene); click(scene, "Refresh account"); assertEquals(2, loads)
        } finally { scene.close() }
    }

    @Test fun localAccountNeverRequestsRemoteIdentityOrShowsServerRecovery() = onEdt {
        var loads = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            TvSettingsScreen(true, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, null,
                initialSection = TvSettingsSection.Account, accountRequested = { loads++ })
        } }
        try {
            frames(scene); assertEquals(0, loads)
            assertTrue(nodes(scene).any { text(it) == "Connection Local mode" })
            assertFalse(nodes(scene).any { text(it) in listOf("Retry account", "Refresh account", "Open server dashboard") })
        } finally { scene.close() }
    }

    @Test fun tvAddonFailureRetainsAddressAndOnlyCommittedSuccessClosesThePage() = onEdt {
        var error by mutableStateOf<String?>(null); var attempts = 0
        var completion: ((Boolean) -> Unit)? = null
        val submitted = mutableListOf<String>()
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            TvSettingsScreen(true, emptyList(), emptySet(), false, error, null, { throw AssertionError("Legacy install path used") }, { _, _ -> }, {}, {}, null,
                initialSection = TvSettingsSection.Addons, installWithCompletion = { url, done ->
                    attempts++; submitted += url; completion = done
                })
        } }
        try {
            frames(scene); click(scene, "Add an add-on"); frames(scene)
            // The TV entry page exposes the address as an editable field.
            nodes(scene).single { it.config.contains(SemanticsActions.SetText) }
                .config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("https://fixture.example/manifest.json"))
            frames(scene)
            click(scene, "Install"); frames(scene)          // opens the confirm page
            val confirms = nodes(scene).filter { text(it) == "Install" && it.config.contains(SemanticsActions.OnClick) }
            confirms.last().config[SemanticsActions.OnClick].action!!.invoke()
            frames(scene)
            assertEquals(1, attempts); assertEquals(listOf("https://fixture.example/manifest.json"), submitted)
            val failedAttempt = requireNotNull(completion)
            error = "Could not reach this add-on."; failedAttempt.invoke(false); frames(scene)
            // Failure keeps the page and the typed address; only a committed
            // success closes it.
            val field = nodes(scene).single { it.config.contains(SemanticsActions.SetText) }
            assertEquals("https://fixture.example/manifest.json", field.config[SemanticsProperties.EditableText].text)
            assertTrue(nodes(scene).any { text(it) == error }); capture(scene, "tv-addon-install-failure")
            error = null; requireNotNull(completion).invoke(true); frames(scene)
            assertFalse(nodes(scene).any { it.config.contains(SemanticsActions.SetText) })
            click(scene, "Add an add-on"); frames(scene)
            assertEquals("", nodes(scene).single { it.config.contains(SemanticsActions.SetText) }.config[SemanticsProperties.EditableText].text)
        } finally { scene.close() }
    }

    private var frame = 0L
    private suspend fun frames(scene: ImageComposeScene) = repeat(20) { scene.render(frame++ * 16_666_667L).close(); delay(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun action(scene: ImageComposeScene, label: String) = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
    private fun click(scene: ImageComposeScene, label: String) { assertTrue(action(scene, label).config[SemanticsActions.OnClick].action!!.invoke()) }
    private fun capture(scene: ImageComposeScene, name: String) { System.getenv("VIPTV_ACCOUNT_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs(); scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    } }
}
