@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
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
import kotlin.test.*

class AddonTargetUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun addon(index: Int) = IntegrationConnection("addon:$index", "stremio", "1", null, "Fixture $index", true, 1,
        listOf(Capability.DISCOVERY))

    private val many = (1..30).map(::addon)
    private val target = "addon:27"

    private class Scene(width: Int, height: Int, television: Boolean = false, content: @Composable () -> Unit) : AutoCloseable {
        private val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television, pointer = true) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }
        private var frame = 0L

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }

        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }

        private fun label(node: SemanticsNode): String {
            val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            return (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + text).joinToString(" ").trim()
        }

        fun labelled(label: String) = nodes().filter { this.label(it) == label || this.label(it).contains(label) }

        fun focused() = nodes().filter { it.config.getOrNull(SemanticsProperties.Focused) == true }.map(::label)

        fun ringed() = nodes().filter { it.config.getOrNull(SemanticsProperties.TestTag) == ADDON_TARGET_ROW_TAG }

        fun pixel(at: Offset): Int = scene.render(frame++ * 16_666_667L).use { Bitmap.makeFromImage(it) }.getColor(at.x.toInt(), at.y.toInt())

        fun capture(dir: String, name: String) {
            java.io.File(dir).mkdirs()
            scene.render(frame++ * 16_666_667L).use { image ->
                image.encodeToData()!!.use { java.io.File(dir, "$name.png").writeBytes(it.bytes) }
            }
        }

        override fun close() = scene.close()
    }

    @Test fun theRowOnAPointerLayoutIsScrolledInFocusedAndRinged(): Unit = runBlocking {
        var accent = 0
        Scene(1280, 700) {
            accent = Palette.accent.toArgb()
            AddonSettings(false, many, many.map { it.id }.toSet(), false, null, null, {}, { _, _ -> }, {},
                managementAvailable = true, initialAddonId = target)
        }.use { scene ->
            scene.frames()
            assertEquals(1, scene.focused().count { it.startsWith("Enable Fixture 27 ") }, "the target add-on's switch holds focus: ${scene.focused()}")
            assertTrue(scene.labelled("Fixture 1").isEmpty(), "the list scrolled past the first add-on")
            val row = scene.ringed().single()
            assertTrue(row.boundsInRoot.top >= 0f && row.boundsInRoot.bottom <= 700f, "the target row is on screen: ${row.boundsInRoot}")
            assertEquals(accent, scene.pixel(Offset(row.boundsInRoot.left + 1f, row.boundsInRoot.center.y)),
                "the row carries the arrival ring")
        }
    }

    @Test fun onlyTheRowConfigureOpenedIsRinged(): Unit = runBlocking {
        Scene(1280, 700) {
            AddonSettings(false, many, many.map { it.id }.toSet(), false, null, null, {}, { _, _ -> }, {},
                managementAvailable = true, initialAddonId = "addon:26")
        }.use { scene ->
            scene.frames()
            assertFalse(scene.labelled("Fixture 26").isEmpty(), "the viewer's add-on is in view")
            assertEquals(1, scene.ringed().size, "one row carries the arrival ring")
        }
    }

    @Test fun openingAddOnsNormallyShowsNoTarget(): Unit = runBlocking {
        Scene(1280, 700) {
            AddonSettings(false, many, many.map { it.id }.toSet(), false, null, null, {}, { _, _ -> }, {},
                managementAvailable = true)
        }.use { scene ->
            scene.frames()
            assertTrue(scene.ringed().isEmpty(), "an ordinary visit rings nothing")
        }
    }

    @Test fun rendersForEvidence(): Unit = runBlocking {
        val dir = System.getenv("VIPTV_ADDON_TARGET_CAPTURE_DIR") ?: return@runBlocking
        Scene(390, 844) {
            AddonSettings(false, many, many.map { it.id }.toSet(), false, null, null, {}, { _, _ -> }, {},
                managementAvailable = true, initialAddonId = target)
        }.use { it.frames(20); it.capture(dir, "addon-target-phone-390") }
        Scene(1280, 800) {
            AddonSettings(false, many, many.map { it.id }.toSet(), false, null, null, {}, { _, _ -> }, {},
                managementAvailable = true, initialAddonId = target)
        }.use { it.frames(20); it.capture(dir, "addon-target-desktop-1280") }
        Scene(1920, 1080, television = true) {
            TvAddonSettings(many, many.map { it.id }.toSet(), false, false, 1f, FocusRequester(), FocusRequester(),
                many.associate { it.id to TvAddonInfo(subtitle = "Discovery", color = 0xFF3D6C88, letter = "F") }, { _, _ -> },
                {}, manage = true, initialAddonId = target)
        }.use { it.frames(30); it.capture(dir, "addon-target-tv-1920") }
    }

    @Test fun theTvListOpensOnTheTargetRow(): Unit = runBlocking {
        Scene(1920, 1080, television = true) {
            TvAddonSettings(many, many.map { it.id }.toSet(), false, false, 1f, FocusRequester(), FocusRequester(),
                many.associate { it.id to TvAddonInfo(subtitle = "Discovery", color = 0xFF3D6C88, letter = "F") }, { _, _ -> },
                {}, manage = true, initialAddonId = target)
        }.use { scene ->
            scene.frames(30)
            assertTrue(scene.focused().any { it.contains("Fixture 27") }, "the remote lands on the add-on Configure opened: ${scene.focused()}")
            val row = scene.labelled("Fixture 27").first()
            assertTrue(row.boundsInRoot.top >= 0f && row.boundsInRoot.bottom <= 1080f, "the target row is on screen: ${row.boundsInRoot}")
        }
    }
}