package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.components.BACKEND_BANNER_BODY
import org.viptv.app.components.BACKEND_BANNER_TITLE
import org.viptv.app.components.BackendBanner
import org.viptv.app.components.pointerClickable
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class BackendBannerUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val started = 1_760_000_000_000L

    private class Probe(val scene: ImageComposeScene) {
        private var frame = 0L
        fun frames(count: Int = 8) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun texts() = nodes().map(::text).filter { it.isNotEmpty() }
        fun card() = requireNotNull(nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == "backend-banner" }) {
            "the banner is not on screen"
        }
        fun click(label: String) {
            val node = nodes().last {
                it.config.contains(SemanticsActions.OnClick) && (text(it) == label || it.children.any { child -> text(child) == label })
            }
            tap(node.boundsInRoot.center)
        }
        fun tap(at: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, at)
            scene.sendPointerEvent(PointerEventType.Release, at)
            frames()
        }
    }

    private fun bannerScene(width: Int, height: Int, television: Boolean, dismiss: () -> Unit, page: () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    Box(Modifier.fillMaxSize().pointerClickable { page() })
                    BackendBanner(television, phone = width < 640, aboveNav = width < 640, unreachableSince = started, dismiss = dismiss)
                }
            }
        }

    @Test fun phoneAndDesktopReadTheDesignCopyOverThePageAndDismiss() {
        for ((width, height, phone) in listOf(Triple(390, 844, true), Triple(1280, 900, false), Triple(1440, 900, false))) {
            var dismissed = 0
            var page = 0
            val scene = bannerScene(width, height, television = false, dismiss = { dismissed++ }, page = { page++ })
            try {
                val probe = Probe(scene); probe.frames()
                val texts = probe.texts()
                assertTrue(BACKEND_BANNER_TITLE in texts, "$width: the title is on screen")
                assertTrue(BACKEND_BANNER_BODY in texts, "$width: the explanation is on screen")
                assertTrue(texts.any { it.startsWith("Backend unreachable since ") && it.endsWith(".") }, "$width: the outage time is on screen")
                val card = probe.card().boundsInRoot
                assertTrue(card.width <= if (phone) 358.5f else 520.5f, "$width: the banner keeps its width cap (${card.width})")
                assertTrue(card.bottom <= height && card.top >= 0f, "$width: the banner is inside the page")
                probe.tap(Offset(width / 2f, 120f))
                assertEquals(1, page, "$width: the page behind the banner still takes taps")
                probe.click("Dismiss")
                assertEquals(1, dismissed, "$width: Dismiss hides the banner")
            } finally { scene.close() }
        }
    }

    @Test fun televisionShowsTheSameCopyWithoutCoveringThePage() {
        var dismissed = 0
        var page = 0
        val scene = bannerScene(1920, 1080, television = true, dismiss = { dismissed++ }, page = { page++ })
        try {
            val probe = Probe(scene); probe.frames()
            val texts = probe.texts()
            assertTrue(BACKEND_BANNER_TITLE in texts, "the title is on screen")
            assertTrue(BACKEND_BANNER_BODY in texts, "the explanation is on screen")
            assertTrue(texts.any { it.startsWith("Backend unreachable since ") && it.endsWith(".") }, "the outage time is on screen")
            val card = probe.card().boundsInRoot
            assertTrue(card.height < 1080f * 0.5f, "the banner covers a strip, not the page (${card.height})")
            probe.tap(Offset(960f, 900f))
            assertEquals(1, page, "the page under the banner still takes taps")
            probe.click("Dismiss")
            assertEquals(1, dismissed)
        } finally { scene.close() }
    }
}
