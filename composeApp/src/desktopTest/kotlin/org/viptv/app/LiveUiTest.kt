package org.viptv.app

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap
import org.viptv.app.connection.*
import org.viptv.app.features.live.*
import org.viptv.app.state.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.engine.wire.MediaIdentity
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class LiveUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun channel() = LiveChannel(Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1", title = "Fixture news", badges = emptyList()), id = "one", connectionId = "m3u:1")
    @Test fun tvFavoriteAndCategoryAreRealActionsWithinTheExistingLayout() {
        var saved = 0; var filtered = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                LiveScreen(listOf(channel()), true, null, null, {}, {}, favoriteChanged = { saved++ }, favoritesFilter = { filtered++ })
            } }
        }
        try {
            frames(scene)
            val favorite = clickable(scene, "Favourite channel")
            assertTrue(favorite.boundsInRoot.bottom < 460f, "Favorite stays beside the preview, above guide rows")
            assertTrue(favorite.config[SemanticsActions.OnClick].action!!.invoke()); assertEquals(1, saved)
            assertTrue(clickable(scene, "Favourites").config[SemanticsActions.OnClick].action!!.invoke()); assertEquals(1, filtered)
            capture(scene, "tv-live-favorites")
        } finally { scene.close() }
    }

    @Test fun sourceSelectorIsConditionalAndNamesTheSelectedAuthorizedFeed() {
        for (sources in listOf(mapOf("m3u:1" to "Playlist"), mapOf("m3u:1" to "Playlist", "xtream:2" to "Other feed"))) {
            val scene = ImageComposeScene(1200, 800, Density(1f)) {
                ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    LiveScreen(listOf(channel()), false, null, null, {}, {}, sources = sources, selectedSource = "m3u:1", sourceChanged = {})
                } }
            }
            try {
                frames(scene)
                val labels = nodes(scene).filter { text(it).startsWith("Source:") }
                assertEquals(sources.size > 1, labels.isNotEmpty())
                if (sources.size > 1) assertTrue(labels.any { text(it) == "Source: Playlist" })
            } finally { scene.close() }
        }
    }

    @Test fun emptyFavoritesStateExplicitlyNamesTheSelectedFeedOnDesktopAndTv() {
        for (tv in listOf(false, true)) {
            val scene = ImageComposeScene(if (tv) 1920 else 1200, if (tv) 1080 else 800, Density(1f)) {
                ViptvTheme(tv) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    LiveScreen(listOf(channel()), tv, null, null, {}, {}, favoritesOnly = true, favoritesFilter = {},
                        sources = mapOf("m3u:1" to "Playlist", "xtream:2" to "Other feed"), selectedSource = "xtream:2", sourceChanged = {})
                } }
            }
            try { frames(scene); assertTrue(nodes(scene).any { text(it) == "No favourite channels from Other feed." }) }
            finally { scene.close() }
        }
    }
    @Test fun groupAndFavouritesAreChipsThatApplyAtOnceAndClear() {
        for (tv in listOf(false, true)) {
            val groups = mutableListOf<String?>()
            var favorites = 0
            val channels = listOf(channel(), LiveChannel(Card(MediaIdentity("two", "live", emptyList()), provider_id = "m3u:1",
                title = "Fixture sport", badges = emptyList()), category = "Sport", groupId = "sport"))
            val scene = ImageComposeScene(if (tv) 1920 else 1200, if (tv) 1080 else 800, Density(1f)) {
                ViptvTheme(tv) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    LiveScreen(channels, tv, null, null, {}, {}, groupLabels = mapOf("news" to "News", "sport" to "Sport"),
                        groupChanged = { groups += it }, favoritesFilter = { favorites++ })
                } }
            }
            try {
                frames(scene)
                val layout = if (tv) "TV" else "desktop"
                assertTrue(nodes(scene).any { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList && text(it) == "Group: All" }, "$layout: the group is a chip")
                assertFalse(nodes(scene).any { text(it) == "Clear" }, "$layout: nothing to clear yet")
                open(scene, "Group: All")
                pick(scene, "Sport")
                assertEquals(listOf<String?>("sport"), groups, "$layout: a group pick applies at once")
                assertTrue(clickable(scene, "Clear").config[SemanticsActions.OnClick].action!!.invoke())
                frames(scene)
                assertEquals(listOf<String?>("sport", null), groups, "$layout: Clear drops the group")
                assertFalse(nodes(scene).any { it.config.getOrNull(SemanticsProperties.Role) == Role.Button && text(it) == "Clear" })
                assertTrue(clickable(scene, "Favourites").config[SemanticsActions.OnClick].action!!.invoke())
                assertEquals(1, favorites, "$layout: Favourites is a toggle chip")
            } finally { scene.close() }
        }
    }
    @Test fun tvNoSourceIsOneBroadcastFrameAndBothSetupActionsReachTheirSettingsSection() {
        val controller = liveController(livePage())
        val scene = liveScene(1920, 1080, true, controller)
        try {
            frames(scene)
            val texts = nodes(scene).map(::text)
            assertTrue(texts.contains(LiveEyebrow)); assertTrue(texts.contains(LiveNoSourceHeadline)); assertTrue(texts.contains(LiveNoSourceBody))
            assertFalse(nodes(scene).any { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList }, "no filter chip on the no-source page")
            listOf("Favourite channel", "Favourites", "Group: All", "All channels", "Manage add-ons", LiveNoChannelsHeadline, "No channels are available for this profile.")
                .forEach { assertFalse(texts.contains(it), "the no-source page draws no $it") }
            assertFalse(texts.any { it.startsWith("Source:") })
            assertTrue(distinct(scene) > 200, "the stadium artwork fills the page")
            capture(scene, "tv-live-no-source")
            assertTrue(action(scene, LiveSetUp).config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(Screen.Settings, controller.state.value.screen)
            assertEquals(LiveSourceIntegrationsSection, controller.state.value.settingsEntrySection)
            assertTrue(controller.state.value.liveSetupReturnFocus, "Back from Settings returns to the primary")
        } finally { scene.close() }
    }

    @Test fun tvAddonsOpensSettingsOnAddons() {
        val controller = liveController(livePage())
        val scene = liveScene(1920, 1080, true, controller)
        try {
            frames(scene)
            assertTrue(action(scene, LiveAddons).config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(Screen.Settings, controller.state.value.screen)
            assertEquals(LiveAddonsSection, controller.state.value.settingsEntrySection)
        } finally { scene.close() }
    }

    @Test fun tvDownFromTheNavigationEntersThePageOnThePrimary() {
        for (returnFocus in listOf(false, true)) {
            val controller = liveController(livePage(), returnFocus = returnFocus)
            val focusManager = mutableListOf<androidx.compose.ui.focus.FocusManager>()
            var frame = 0L
            val scene = ImageComposeScene(1920, 1080, Density(1f)) {
                ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    focusManager += LocalFocusManager.current
                    val state by controller.state.collectAsState()
                    val navigation = remember { FocusRequester() }
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(132.dp).focusRequester(navigation).focusable())
                        Box(Modifier.weight(1f)) { LiveRoute(state, controller, true) }
                    }
                    LaunchedEffect(Unit) { navigation.requestFocus() }
                } }
            }
            try {
                repeat(30) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(3) }
                assertEquals(if (returnFocus) LiveSetUp else null, focusedText(scene),
                    "the primary takes focus only when the empty state is re-entered")
                val consumed = scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyDown))
                scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyUp))
                repeat(20) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(3) }
                if (!consumed) focusManager.last().moveFocus(FocusDirection.Down)
                repeat(20) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(3) }
                assertEquals(LiveSetUp, focusedText(scene), "Down from the navigation lands on the primary")
            } finally { scene.close() }
        }
    }

    @Test fun identityStillLoadingLeavesTheActionBlockEmpty() {
        val controller = liveController(livePage(), access = null)
        val scene = liveScene(1920, 1080, true, controller)
        try {
            frames(scene)
            val texts = nodes(scene).map(::text)
            assertTrue(texts.contains(LiveNoSourceHeadline))
            assertFalse(texts.contains(LiveSetUp)); assertFalse(texts.contains(LiveMemberWho)); assertFalse(texts.contains(LivePairedWho))
        } finally { scene.close() }
    }

    @Test fun memberAndPairedSessionsGetTheWhoLineInsteadOfActions() {
        for ((access, who) in listOf(AccountAccess("member", true) to LiveMemberWho, AccountAccess("device", false) to LivePairedWho, AccountAccess("owner", false) to LivePairedWho)) {
            val controller = liveController(livePage(), access = access)
            val scene = liveScene(1280, 800, false, controller)
            try {
                frames(scene)
                val texts = nodes(scene).map(::text)
                assertTrue(texts.contains(who), "${access.role} sees the who line")
                assertFalse(texts.contains(LiveSetUp), "${access.role} gets no primary")
                assertFalse(texts.contains(LiveAddons), "${access.role} gets no secondary")
            } finally { scene.close() }
        }
    }

    @Test fun desktopNoSourceSitsAtThePageInsetWithTheQuietSecondary() {
        val controller = liveController(livePage())
        val scene = liveScene(1280, 800, false, controller)
        try {
            frames(scene)
            assertTrue(nodes(scene).map(::text).contains(LiveNoSourceHeadline))
            val primary = action(scene, LiveSetUp).boundsInRoot
            assertEquals(40f, primary.left, 1.5f)
            assertEquals(48f, primary.height, 1.5f)
            assertEquals(40f, action(scene, LiveAddons).boundsInRoot.height, 1.5f)
            capture(scene, "desktop-live-no-source")
        } finally { scene.close() }
    }

    @Test fun phoneNoSourceUsesTheTallBlockAboveTheBottomBar() {
        val controller = liveController(livePage())
        val scene = liveScene(390, 844, false, controller)
        try {
            frames(scene)
            val primary = action(scene, LiveSetUp).boundsInRoot
            assertEquals(358f, primary.width, 1.5f)
            assertEquals(54f, primary.height, 1.5f)
            assertTrue(primary.bottom <= 844f - 112f + 1f, "the block sits above the bottom bar")
            capture(scene, "phone-live-no-source")
        } finally { scene.close() }
    }

    @Test fun narrowDesktopWindowUsesThePhoneBlock() {
        val controller = liveController(livePage())
        val scene = liveScene(600, 800, false, controller)
        try {
            frames(scene)
            assertTrue(nodes(scene).map(::text).contains(LiveNoSourceHeadline))
            assertEquals(54f, action(scene, LiveSetUp).boundsInRoot.height, 1.5f)
            capture(scene, "narrow-desktop-live-no-source")
        } finally { scene.close() }
    }

    @Test fun noChannelsWhileTheRequestIsOpenDrawsSixSkeletonsAndOnlyTheSourceChip() {
        for (television in listOf(false, true)) {
            for (second in listOf(false, true)) {
                val page = livePage(*if (second) arrayOf("m3u:1" to "Playlist", "xtream:2" to "Other feed") else arrayOf("m3u:1" to "Playlist"))
                val controller = liveController(page, busy = true, query = LiveQuery(connection_id = "m3u:1"))
                val width = if (television) 1920 else 1280
                val scene = liveScene(width, if (television) 1080 else 800, television, controller)
                try {
                    frames(scene)
                    val texts = nodes(scene).map(::text)
                    val layout = "${if (television) "TV" else "desktop"}, ${if (second) "two" else "one"} connection(s)"
                    assertFalse(texts.any { it.startsWith("No channels") }, "$layout: the page never says No channels while the request is open")
                    assertEquals(second, nodes(scene).any { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList && text(it).startsWith("Source:") },
                        "$layout: the Source chip is the only chip, and only with a second connection")
                    assertFalse(nodes(scene).any { text(it) == "Favourites" }, "$layout: no favourites chip without channels")
                    assertEquals(6, skeletonRows(scene, if (television) 1700 else 70, if (television) 1080 else 800),
                        "$layout: six skeleton rows in the page's row shape")
                    if (second) capture(scene, "${if (television) "tv" else "desktop"}-live-no-channels-loading")
                } finally { scene.close() }
            }
        }
    }

    @Test fun noChannelsAnsweredShowsTheReasonAndRetryOnlyWhenSomethingFailed() {
        for (television in listOf(false, true)) {
            val failed = liveController(livePage("m3u:1" to "Playlist", reason = "The provider refused the stream.", availability = "TemporarilyUnavailable"))
            val quiet = liveController(livePage("m3u:1" to "Playlist"))
            for ((controller, reason, retry) in listOf(Triple(failed, "The provider refused the stream.", true), Triple(quiet, LiveNoChannelsBody, false))) {
                val scene = liveScene(if (television) 1920 else 1280, if (television) 1080 else 800, television, controller)
                try {
                    frames(scene)
                    val texts = nodes(scene).map(::text)
                    assertTrue(texts.contains(LiveNoChannelsHeadline), "the answered headline")
                    assertTrue(texts.contains(reason), "the reason line")
                    assertEquals(retry, texts.contains(LiveTryAgain), "Try again only when the failure is retryable")
                    assertFalse(texts.contains(LiveSetUp), "the no-channels state offers no setup")
                    assertTrue(action(scene, LiveSourceIntegrations).config.contains(SemanticsActions.OnClick))
                    capture(scene, "${if (television) "tv" else "desktop"}-live-no-channels-${if (retry) "failed" else "empty"}")
                } finally { scene.close() }
            }
        }
    }

    @Test fun sourceIntegrationsIsAbsentForAMemberEvenWhenNoChannelsCameBack() {
        val controller = liveController(livePage("m3u:1" to "Playlist", reason = "The provider refused the stream.", availability = "TemporarilyUnavailable"),
            access = AccountAccess("member", true))
        val scene = liveScene(1280, 800, false, controller)
        try {
            frames(scene)
            assertTrue(nodes(scene).map(::text).contains(LiveTryAgain))
            assertFalse(nodes(scene).map(::text).contains(LiveSourceIntegrations))
        } finally { scene.close() }
    }

    @Test fun aSourceAddedElsewhereReplacesTheEmptyStateInPlace() {
        val controller = liveController(livePage("m3u:1" to "Playlist"))
        val scene = liveScene(1280, 800, false, controller)
        try {
            frames(scene)
            assertTrue(nodes(scene).map(::text).contains(LiveNoChannelsHeadline))
            controller.mutable.value = controller.state.value.copy(livePage = livePage("m3u:1" to "Playlist").copy(channels = listOf(liveChannel())))
            frames(scene)
            val texts = nodes(scene).map(::text)
            assertTrue(texts.contains("Fixture news"), "the channel list replaces the empty state")
            assertFalse(texts.contains(LiveNoChannelsHeadline))
        } finally { scene.close() }
    }

    private fun liveChannel() = LiveCatalogChannel("one", "m3u:1", Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1", title = "Fixture news", badges = emptyList()))

    private fun livePage(vararg connections: Pair<String, String>, reason: String? = null, availability: String = "Available") =
        LiveCatalogPage(availability = availability, reason = reason, connections = connections.map { (id, name) ->
            IntegrationConnection(id, "m3u", "7", name = name, enabled = true, revision = 1, capabilities = emptyList())
        })

    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun liveController(page: LiveCatalogPage?, access: AccountAccess? = AccountAccess("owner", true), busy: Boolean = false,
        query: LiveQuery = LiveQuery(), returnFocus: Boolean = false) =
        ApplicationController(CoroutineScope(Dispatchers.Unconfined), store).also {
            it.mutable.value = ApplicationState(screen = Screen.Live, selectedProfile = "12", livePage = page, liveQuery = query,
                accountAccess = access, busy = busy, liveSetupReturnFocus = returnFocus)
        }

    private fun liveScene(width: Int, height: Int, television: Boolean, controller: ApplicationController) =
        ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                val state by controller.state.collectAsState()
                LiveRoute(state, controller, television)
            } }
        }

    private fun action(scene: ImageComposeScene, label: String): SemanticsNode =
        nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }.first { node -> flatten(node).any { text(it) == label } }

    private fun focusedText(scene: ImageComposeScene) =
        nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::text)

    private fun distinct(scene: ImageComposeScene): Int = scene.render(1_000_000_000L).use { image ->
        Bitmap.makeFromImage(image).use { bitmap -> buildSet {
            for (y in 0 until image.height step 7) for (x in 0 until image.width step 7) add(bitmap.getColor(x, y))
        }.size }
    }

    private fun skeletonRows(scene: ImageComposeScene, x: Int, height: Int): Int =
        scene.render(1_000_000_000L).use { image ->
            Bitmap.makeFromImage(image).use { bitmap ->
                val background = Palette.background.toArgb()
                var runs = 0; var length = 0
                for (y in 0 until height) {
                    val colour = bitmap.getColor(x, y)
                    if (colour != background) length++ else { if (length >= 20) runs++; length = 0 }
                }
                if (length >= 20) runs++
                runs
            }
        }

    private fun open(scene: ImageComposeScene, label: String) {
        val trigger = nodes(scene).single { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList && text(it) == label }
        assertTrue(trigger.config[SemanticsActions.OnClick].action!!.invoke()); frames(scene)
    }
    private fun pick(scene: ImageComposeScene, label: String) {
        val row = nodes(scene).single { it.config.getOrNull(SemanticsProperties.Role) in listOf(Role.RadioButton, Role.Checkbox) && text(it).startsWith(label) }
        assertTrue(row.config[SemanticsActions.OnClick].action!!.invoke()); frames(scene)
    }
    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(3) }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun clickable(scene: ImageComposeScene, label: String) = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
    private fun capture(scene: ImageComposeScene, name: String) {
        val directory = System.getenv("VIPTV_LIVE_CAPTURE_DIR") ?: return
        File(directory).mkdirs()
        scene.render(1_000_000_000L).use { image -> image.encodeToData()!!.use { File(directory, "$name.png").writeBytes(it.bytes) } }
    }
}
