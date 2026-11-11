package org.viptv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.components.*
import org.viptv.app.connection.Profile
import org.viptv.app.features.remote.RemoteHeaderButton
import org.viptv.app.features.remote.RemoteHeaderPages
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

/**
 * The Watch on TV header button lives on Home, Live and My List on the touch layouts and nowhere else
 * (components.md §11): Home is the discover surface on phone and tablet, which have no Discover page,
 * and TV, browser and desktop do not adopt the remote. The tip names the pages this one set defines.
 */
@OptIn(ExperimentalComposeUiApi::class)
class RemoteHeaderPagesUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val profile = Profile("qa", "Stremio import QA", null, avatarId = "initial-blue")
    private val remoteLabel = "TV remote: Living Room"
    /** components.md §11: Home is the discover surface on phone and tablet, which have no Discover page. */
    private val pages = setOf(Screen.Home, Screen.Live, Screen.Library)

    @Test fun thePhoneHeaderShowsTheRemoteOnlyOnThePagesTheTipNames() {
        for (destination in phoneDestinations) {
            val scene = scene(390, 844) { PhoneHeader(destination.screen, profile) {} }
            try {
                assertEquals(destination.screen in pages, hasRemote(scene),
                    "phone ${destination.label} shows the remote only on Home, Live and My List: ${descriptions(scene)}")
                assertTrue(nodes(scene).any { description(it) == listOf("Profile menu") }, "phone ${destination.label} keeps the profile menu")
            } finally { scene.close() }
        }
    }

    @Test fun theTabletHeaderShowsTheRemoteOnlyOnThePagesTheTipNames() {
        for (destination in phoneDestinations) {
            val scene = scene(1280, 800) { TabletHeader(destination.screen) }
            try {
                assertEquals(destination.screen in pages, hasRemote(scene),
                    "tablet ${destination.label} shows the remote only on Home, Live and My List: ${descriptions(scene)}")
            } finally { scene.close() }
        }
    }

    @Test fun theTipNamesEveryPageTheHeaderShowsTheButtonOn() {
        assertEquals("Home, Live and My List", RemoteHeaderPages.names)
        val scene = scene(390, 844, showTip = true) { PhoneHeader(Screen.Library, profile) {} }
        try {
            val texts = nodes(scene).map(::text)
            assertTrue("Your TV remote" in texts, texts.toString())
            assertTrue("It stays up here on Home, Live and My List. Turn it off in Settings › Watch on TV." in texts, texts.toString())
        } finally { scene.close() }
    }

    @Test fun theTvBrowserAndDesktopChromeNeverDrawTheRemote() {
        for (destination in desktopDestinations) {
            val scene = scene(1920, 1080) { TvNavigation(destination.screen, profile) {} }
            try { assertFalse(hasRemote(scene), "TV ${destination.label}: ${descriptions(scene)}") } finally { scene.close() }
            for (television in listOf(true, false)) {
                val top = scene(1920, 1080) { TopNavigation(destination.screen, television, {}, profile) }
                try { assertFalse(hasRemote(top), "${if (television) "TV" else "browser"} title bar on ${destination.label}: ${descriptions(top)}") } finally { top.close() }
            }
            val side = scene(1440, 900) { SideNavigation(destination.screen) {} }
            try { assertFalse(hasRemote(side), "desktop ${destination.label}: ${descriptions(side)}") } finally { side.close() }
        }
    }

    private var frame = 0L
    private fun scene(width: Int, height: Int, showTip: Boolean = false, content: @Composable () -> Unit): ImageComposeScene {
        frame = 0
        val headerRemote: @Composable () -> Unit =
            { RemoteHeaderButton("Living Room", showTip = showTip, dismissTip = {}, open = {}) }
        return ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) { Column {
                CompositionLocalProvider(LocalHeaderRemote provides headerRemote) { content() }
            } } }
        }
    }
    private fun hasRemote(scene: ImageComposeScene) =
        nodes(scene).any { description(it) == listOf(remoteLabel) && it.config.contains(SemanticsActions.OnClick) }
    private fun descriptions(scene: ImageComposeScene) = nodes(scene).flatMap(::description)
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> {
        repeat(12) { scene.render(frame++ * 16_666_667L).close() }
        return scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
}