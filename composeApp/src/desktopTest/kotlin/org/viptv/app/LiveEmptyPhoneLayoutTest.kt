package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.viptv.app.components.LocalBottomClearance
import org.viptv.app.components.bottomBarClearance
import org.viptv.app.connection.AccountAccess
import org.viptv.app.features.live.LiveAddons
import org.viptv.app.features.live.LiveEyebrow
import org.viptv.app.features.live.LiveMemberWho
import org.viptv.app.features.live.LiveNoSourceBody
import org.viptv.app.features.live.LiveNoSourceHeadline
import org.viptv.app.features.live.LiveNoSourcePage
import org.viptv.app.features.live.LivePhoneArtTag
import org.viptv.app.features.live.LiveSetUp
import org.viptv.app.images.ConfigureImageLoading
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class LiveEmptyPhoneLayoutTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun thePhoneBlockFitsUnderTheHeaderWithItsActionsClearOfTheBottomBar() {
        for ((width, height) in listOf(360 to 640, 412 to 915)) {
            var clearance = 0.dp
            val scene = ImageComposeScene(width, height, Density(1f)) {
                ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    CompositionLocalProvider(LocalBottomClearance provides bottomBarClearance()) {
                        clearance = bottomBarClearance()
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxWidth().height(StatusBarHeight + HeaderHeight))
                            Box(Modifier.fillMaxWidth().weight(1f)) {
                                LiveNoSourcePage(false, AccountAccess("owner", true), local = false, returnFocus = false,
                                    setUp = {}, addons = {}, requestAccess = {}, focusTaken = {})
                            }
                        }
                    }
                } }
            }
            try {
                frames(scene)
                val nodes = nodes(scene)
                val layout = "${width}x$height"
                val pageTop = (StatusBarHeight + HeaderHeight).value
                val pageBottom = height - clearance.value
                val art = tagged(nodes, LivePhoneArtTag).boundsInRoot
                val eyebrow = single(nodes, LiveEyebrow).boundsInRoot
                val headline = single(nodes, LiveNoSourceHeadline).boundsInRoot
                val body = single(nodes, LiveNoSourceBody).boundsInRoot
                val primary = action(nodes, LiveSetUp).boundsInRoot
                val secondary = action(nodes, LiveAddons).boundsInRoot

                assertEquals(0f, art.left, .5f, "$layout: the photo is full-bleed")
                assertEquals(width.toFloat(), art.right, .5f, "$layout: the photo is full-bleed")
                assertEquals(pageTop, art.top, .5f, "$layout: the photo starts under the header")
                assertTrue(art.height > 80f, "$layout: the photo keeps a band of ${art.height}")
                assertTrue(nodes.none { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }, "$layout: nothing scrolls")

                assertTrue(art.bottom <= eyebrow.top, "$layout: the copy starts below the photo")
                assertTrue(eyebrow.bottom <= headline.top, "$layout: eyebrow above headline")
                assertTrue(headline.bottom <= body.top, "$layout: headline above body")
                assertTrue(body.bottom <= primary.top, "$layout: the body clears the primary")
                assertTrue(primary.bottom <= secondary.top, "$layout: primary above secondary")

                assertEquals(16f, primary.left, .5f, "$layout: the actions sit on the page gutter")
                assertEquals(width - 32f, primary.width, .5f, "$layout: the primary is the full block width")
                assertEquals(width - 32f, secondary.width, .5f, "$layout: the secondary is the full block width")
                assertEquals(16f, eyebrow.left, .5f, "$layout: the copy sits on the page gutter")

                assertTrue(secondary.bottom <= pageBottom + .5f,
                    "$layout: the actions end ${secondary.bottom} above the clearance ending at $pageBottom")
                assertTrue(primary.top >= pageTop, "$layout: the primary is on the page")
                capture(scene, "phone-live-empty-${width}x$height")
            } finally { scene.close() }
        }
    }

    @Test fun thePhotoGivesWayBeforeTheCopyOnAShortPhone() {
        val tall = artHeight(412, 915)
        val short = artHeight(360, 640)
        assertTrue(tall > short, "the photo shrinks first: $tall at 412x915, $short at 360x640")
        assertTrue(tall >= 380f, "the photo keeps the top of a tall phone: $tall")
    }

    @Test fun aViewerWhoCannotSetUpLiveReadsTheWhoLineWhereTheActionsWouldBe() {
        val scene = scene(360, 640, AccountAccess("member", false))
        try {
            frames(scene)
            val nodes = nodes(scene)
            val who = single(nodes, LiveMemberWho).boundsInRoot
            assertFalse(nodes.any { clickable(it) && text(it) == LiveSetUp }, "no primary")
            assertFalse(nodes.any { clickable(it) && text(it) == LiveAddons }, "no secondary")
            assertTrue(who.top >= single(nodes, LiveNoSourceBody).boundsInRoot.bottom, "the who line takes the action place")
            assertTrue(who.bottom <= 640f - 108f + .5f, "the who line clears the bottom bar")
            capture(scene, "phone-live-empty-member")
        } finally { scene.close() }
    }

    private fun artHeight(width: Int, height: Int): Float {
        val scene = scene(width, height, AccountAccess("owner", true))
        try { frames(scene); return tagged(nodes(scene), LivePhoneArtTag).boundsInRoot.height } finally { scene.close() }
    }

    private fun scene(width: Int, height: Int, access: AccountAccess?) = ImageComposeScene(width, height, Density(1f)) {
        ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
            ConfigureImageLoading()
            CompositionLocalProvider(LocalBottomClearance provides bottomBarClearance()) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(StatusBarHeight + HeaderHeight))
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LiveNoSourcePage(false, access, local = false, returnFocus = false,
                            setUp = {}, addons = {}, requestAccess = {}, focusTaken = {})
                    }
                }
            }
        } }
    }

    private fun single(nodes: List<SemanticsNode>, label: String): SemanticsNode =
        nodes.firstOrNull { text(it) == label } ?: fail("no $label")

    private fun tagged(nodes: List<SemanticsNode>, tag: String): SemanticsNode =
        nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag } ?: fail("no $tag")

    private fun action(nodes: List<SemanticsNode>, label: String): SemanticsNode =
        nodes.filter { clickable(it) && flattenChildren(it).any { child -> text(child) == label } }.first()

    private fun flattenChildren(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flattenChildren)

    private fun clickable(node: SemanticsNode) = node.config.contains(SemanticsActions.OnClick)

    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(3) }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flattenChildren(it.rootSemanticsNode) }

    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

    private fun capture(scene: ImageComposeScene, name: String) {
        val directory = System.getenv("VIPTV_LIVE_CAPTURE_DIR") ?: return
        File(directory).mkdirs()
        scene.render(1_000_000_000L).use { image -> image.encodeToData()?.use { File(directory, "$name.png").writeBytes(it.bytes) } }
    }

    private companion object {
        val StatusBarHeight: Dp = 24.dp
        val HeaderHeight: Dp = 44.dp
    }
}