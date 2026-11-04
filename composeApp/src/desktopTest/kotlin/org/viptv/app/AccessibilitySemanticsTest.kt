package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.compose.LocalPlatformContext
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.viptv.app.components.AccessibleIconButton
import org.viptv.app.components.LocalSelectPresentation
import org.viptv.app.components.PhoneSheet
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption
import org.viptv.app.components.SelectPresentation
import org.viptv.app.connection.Profile
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.features.calendar.CalendarDay
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalComposeUiApi::class, DelicateCoilApi::class)
class AccessibilitySemanticsTest {
    @get:Rule val uiThread = UiThreadRule()

    private val profiles = listOf(Profile("1", "Ana", null))

    private val days = (1..7).map { index ->
        CalendarDay("day-$index", "W", "$index", "Day $index", "Day $index")
    }

    private val screens: Map<String, ApplicationState> = mapOf(
        "Home" to ApplicationState(screen = Screen.Home, selectedProfile = "1", profiles = profiles,
            localMode = true, home = PreviewData.home),
        "Details" to ApplicationState(screen = Screen.Details, selectedProfile = "1", profiles = profiles,
            localMode = true, detail = PreviewData.detail),
        "Settings" to ApplicationState(screen = Screen.Settings, selectedProfile = "1", profiles = profiles, localMode = true),
        "Search" to ApplicationState(screen = Screen.Search, selectedProfile = "1", profiles = profiles,
            localMode = true, searchTerm = "monster", results = PreviewData.posters),
        "Calendar" to ApplicationState(screen = Screen.Calendar, selectedProfile = "1", profiles = profiles,
            localMode = true, calendarDays = days, calendarAvailable = true),
    )

    @Test fun everyClickableNodeOnTheMainScreensIsNamedOrHasARole() {
        Shell().use { shell ->
            val violations = buildList {
                for (name in listOf("Home", "Details", "Settings", "Search", "Calendar")) {
                    shell.show(screens.getValue(name))
                    shell.frames()
                    addAll(shell.anonymousClickables().map { "$name: $it" })
                }

                // Open the profile menu on Home and walk its overlay items too.
                shell.show(screens.getValue("Home"))
                shell.frames()
                val trigger = shell.nodes().singleOrNull {
                    it.config.contains(SemanticsActions.OnClick) &&
                        it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf("Profile menu")
                } ?: fail("no profile menu trigger on Home")
                assertTrue(trigger.config[SemanticsActions.OnClick].action!!.invoke(), "the profile trigger opens")
                shell.frames(40)
                addAll(shell.anonymousClickables().map { "Profile menu: $it" })
            }
            assertTrue(violations.isEmpty(), "unnamed, role-less clickables:\n${violations.joinToString("\n")}")
        }
    }

    @Test fun calendarDateChipsExposeTabRoleAndSelection() {
        Shell().use { shell ->
            shell.show(screens.getValue("Calendar"))
            shell.frames()
            val chips = shell.nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
            assertEquals(7, chips.size, "seven date chips, not ${chips.size}")
            assertEquals(1, chips.count { it.config.getOrNull(SemanticsProperties.Selected) == true },
                "exactly one date chip is selected")
        }
    }

    @Test fun theSheetCloseButtonsExposeA48dpButtonRole() {
        var closed = false
        val phone = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme(false) { PhoneSheet(visible = true, title = "Seasons", dismiss = { closed = true }, touch = true) { Text("content") } }
        }
        try {
            frames(phone, 40)
            val close = closeButtons(phone).singleOrNull() ?: fail("no phone sheet close button")
            assertCloseTarget(close)
        } finally { phone.close() }

        val open = mutableStateOf(true)
        val select = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme(false) {
                CompositionLocalProvider(LocalSelectPresentation provides SelectPresentation.Sheet) {
                    SearchableSelect("Type", listOf(SelectOption("series", "TV")), listOf("series"), {}, open = open)
                }
            }
        }
        try {
            frames(select, 40)
            val close = closeButtons(select).singleOrNull() ?: fail("no select sheet close button")
            assertCloseTarget(close)
        } finally { select.close() }
    }

    @Test fun theSharedIconButtonCarriesItsLabelAndRole() {
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme(false) {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    AccessibleIconButton(Lucide.X, "Clear text", {})
                }
            }
        }
        try {
            frames(scene)
            val button = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
                .single { it.config.contains(SemanticsActions.OnClick) }
            assertEquals(Role.Button, button.config.getOrNull(SemanticsProperties.Role))
            assertEquals(listOf("Clear text"), button.config.getOrNull(SemanticsProperties.ContentDescription))
            assertTrue(button.boundsInRoot.width >= 48f && button.boundsInRoot.height >= 48f, "48dp target: ${button.boundsInRoot}")
        } finally { scene.close() }
    }

    private fun assertCloseTarget(node: SemanticsNode) {
        assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role), "the close button is Role.Button")
        assertEquals(listOf("Close"), node.config.getOrNull(SemanticsProperties.ContentDescription), "the close button reads Close")
        assertTrue(node.boundsInRoot.width >= 48f && node.boundsInRoot.height >= 48f,
            "the close button is a 48dp target: ${node.boundsInRoot}")
    }

    private fun closeButtons(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        .filter { it.config.contains(SemanticsActions.OnClick) &&
            it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { c -> c == "Close" } }

    private class Shell : AutoCloseable {
        private val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        private val controller = ApplicationController(scope, store)
        private var previousLoader: ImageLoader? = null
        private var appLoader: ImageLoader? = null
        private var frame = 0L
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            val context = LocalPlatformContext.current
            remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
            App(providedController = controller, manageControllerLifecycle = false)
            appLoader = SingletonImageLoader.get(context)
        }

        fun show(state: ApplicationState) {
            controller.mutable.value = state
            frame = 0
        }

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(3) }

        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }

        fun anonymousClickables(): List<String> = nodes()
            .filter { it.config.contains(SemanticsActions.OnClick) }
            .filter {
                it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().isEmpty() &&
                    it.config.getOrNull(SemanticsProperties.Text).orEmpty().isEmpty() &&
                    it.config.getOrNull(SemanticsProperties.Role) == null
            }
            .map { it.boundsInRoot.toString() }

        override fun close() {
            scene.close(); scope.cancel()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 20) =
        repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(3) }

    private companion object {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    }
}