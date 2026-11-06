@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.features.settings.ADDON_TARGET_ROW_TAG
import org.viptv.app.features.settings.AddonSettings
import org.viptv.app.features.settings.TvAddonInfo
import org.viptv.app.features.settings.TvAddonSettings
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Capability
import org.viptv.engine.wire.IntegrationConnection
import androidx.compose.ui.focus.FocusRequester
import kotlin.test.*

class AddonTargetUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun addon(index: Int) = IntegrationConnection("addon:$index", "stremio", "1", null, "Fixture $index", true, 1,
        listOf(Capability.DISCOVERY))

    private val many = (1..30).map(::addon)
    private val target = "addon:27"

    private fun scene(width: Int, height: Int, television: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television, pointer = true) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }

    private fun nodes(scene: ImageComposeScene, merged: Boolean = false): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(merged) }

    private fun label(node: SemanticsNode): String =
        (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }).trim()

    private fun pixel(scene: ImageComposeScene, at: Offset): Int =
        scene.render(0).use { Bitmap.makeFromImage(it) }.getColor(at.x.toInt(), at.y.toInt())

    @Test fun theRowOnAPointerLayoutIsScrolledInFocusedAndRinged(): Unit = runBlocking {
        scene(1280, 700) {
            AddonSettings(false, many, many.map { it.id }.toSet(), false, null, null, {}, { _, _ -> }, {},
                managementAvailable = true, initialAddonId = target)
        }.use { scene ->
            repeat(20) { scene.render(it * 16_666_667L).close() }
            val nodes = nodes(scene)
            val focused = nodes.filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
            assertEquals(listOf("Enable Fixture 27 for this profile"), focused.map(::label), "the target add-on's switch holds focus")
            assertTrue(nodes.none { label(it) == "Fixture 1" }, "the list scrolled past the first add-on")
            val row = nodes.first { it.config.getOrNull(SemanticsProperties.TestTag) == ADDON_TARGET_ROW_TAG }
            assertTrue(row.boundsInRoot.top >= 0f && row.boundsInRoot.bottom <= 700f, "the target row is on screen: ${row.boundsInRoot}")
            val ring = pixel(scene, Offset(row.boundsInRoot.left + 1f, row.boundsInRoot.center.y))
            assertEquals(Palette.accent.toArgb(), ring, "the row carries the arrival ring")
        }
    }

    @Test fun anotherAddOnLeavesTheListAlone(): Unit = runBlocking {
        scene(1280, 700) {
            AddonSettings(false, many, many.map { it.id }.toSet(), false, null, null, {}, { _, _ -> }, {},
                managementAvailable = true, initialAddonId = "addon:26")
        }.use { scene ->
            repeat(20) { scene.render(it * 16_666_667L).close() }
            val nodes = nodes(scene)
            assertTrue(nodes.any { label(it) == "Fixture 26" }, "the viewer's add-on is in view")
            assertEquals(1, nodes.count { it.config.getOrNull(SemanticsProperties.TestTag) == ADDON_TARGET_ROW_TAG },
                "only the add-on Configure opened is ringed")
        }
    }

    @Test fun openingAddOnsNormallyShowsNoTarget(): Unit = runBlocking {
        scene(1280, 700) {
            AddonSettings(false, many, many.map { it.id }.toSet(), false, null, null, {}, { _, _ -> }, {},
                managementAvailable = true)
        }.use { scene ->
            repeat(20) { scene.render(it * 16_666_667L).close() }
            assertTrue(nodes(scene).none { it.config.getOrNull(SemanticsProperties.TestTag) == ADDON_TARGET_ROW_TAG })
        }
    }

    @Test fun theTvListOpensOnTheTargetRow(): Unit = runBlocking {
        val first = FocusRequester()
        val sidebar = FocusRequester()
        scene(1920, 1080, television = true) {
            TvAddonSettings(many, many.map { it.id }.toSet(), false, false, 1f, first, sidebar,
                many.associate { it.id to TvAddonInfo(letter = "F", subtitle = "Discovery") }, { _, _ -> },
                {}, manage = true, initialAddonId = target)
        }.use { scene ->
            repeat(30) { scene.render(it * 16_666_667L).close() }
            val focused = nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
            assertTrue(focused.any { label(it).contains("Fixture 27") }, "the remote lands on the add-on Configure opened: ${focused.map(::label)}")
            val node = nodes(scene).first { label(it).contains("Fixture 27") }
            assertTrue(node.boundsInRoot.top >= 0f && node.boundsInRoot.bottom <= 1080f, "the target row is on screen: ${node.boundsInRoot}")
        }
    }
}