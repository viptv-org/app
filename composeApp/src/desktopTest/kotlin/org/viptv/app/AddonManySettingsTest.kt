package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.viptv.app.features.settings.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Capability
import org.viptv.engine.wire.IntegrationConnection
import java.awt.EventQueue
import java.io.File
import kotlinx.coroutines.*
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AddonManySettingsTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private var frameTime = 0L

    private fun name(index: Int) = "Add-on %02d".format(index)
    private val many = (1..25).map { index ->
        IntegrationConnection("addon:$index", "stremio", "fixture", null, name(index), true, 1,
            when (index % 4) {
                1 -> listOf(Capability.DISCOVERY, Capability.METADATA)
                2 -> listOf(Capability.SOURCES)
                3 -> listOf(Capability.SUBTITLES)
                else -> listOf(Capability.LIVETV)
            })
    }

    private fun scene(enabled: Set<String>, toggle: (String, Boolean) -> Unit) = ImageComposeScene(390, 844, Density(1f)) {
        ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
            AddonSettings(true, many, enabled, false, null, null, {}, toggle, {}, back = {})
        } }
    }

    private suspend fun frames(scene: ImageComposeScene, count: Int = 24) =
        repeat(count) { frameTime += 16_666_667L; check(EventQueue.isDispatchThread()); scene.render(frameTime).close(); delay(4) }

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun shown(scene: ImageComposeScene, label: String) = nodes(scene).any { text(it) == label }
    private fun searchField(scene: ImageComposeScene) = nodes(scene).single { it.config.contains(SemanticsActions.SetText) }
    private fun type(scene: ImageComposeScene, term: String) {
        assertTrue(searchField(scene).config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(term)))
    }
    private fun providesChip(scene: ImageComposeScene, label: String) = nodes(scene).single {
        it.config.contains(SemanticsProperties.Selected) && text(it).contains(label)
    }
    private fun invoke(node: SemanticsNode) { assertFalse(node.config.contains(SemanticsProperties.Disabled)); assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()) }
    private fun capture(scene: ImageComposeScene, name: String) { System.getenv("VIPTV_ADDONS_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs(); frameTime += 16_666_667L
        scene.render(frameTime).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    } }

    @Test fun theSearchFieldNarrowsTheListAndTogglingStillSaves() = onEdt {
        var enabled by mutableStateOf(many.map { it.id }.toSet())
        val toggled = mutableListOf<Pair<String, Boolean>>()
        val scene = scene(enabled) { id, value -> toggled += id to value; enabled = if (value) enabled + id else enabled - id }
        try {
            frames(scene)
            assertTrue(shown(scene, name(1)), "the first add-on is on screen")
            assertFalse(shown(scene, name(20)), "25 add-ons do not fit at once")
            capture(scene, "addons-many")
            type(scene, "Add-on 07"); frames(scene)
            assertTrue(shown(scene, name(7)), "the filter keeps the match")
            assertFalse(shown(scene, name(1)), "the filter drops the rest")
            assertTrue(shown(scene, "Showing 1 of 25 add-ons"))
            capture(scene, "addons-many-filtered")
            invoke(nodes(scene).single { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Enable Add-on 07 for this profile") == true })
            frames(scene)
            assertEquals(listOf("addon:7" to false), toggled)
            type(scene, ""); frames(scene)
            assertTrue(shown(scene, name(1)), "clearing the filter restores the list")
            assertTrue(shown(scene, name(2)))
        } finally { scene.close() }
    }

    @Test fun aProvidesChipNarrowsTheListToThatCapability() = onEdt {
        val scene = scene(many.map { it.id }.toSet(), { _, _ -> })
        try {
            frames(scene)
            val streams = providesChip(scene, "Streams")
            assertEquals(false, streams.config[SemanticsProperties.Selected])
            invoke(streams); frames(scene)
            assertEquals(true, providesChip(scene, "Streams").config[SemanticsProperties.Selected])
            assertTrue(shown(scene, name(2)), "a streams add-on stays")
            assertFalse(shown(scene, name(1)), "a catalogs add-on is filtered out")
            assertFalse(shown(scene, name(3)), "a subtitles add-on is filtered out")
            assertTrue(shown(scene, "Showing 6 of 25 add-ons"))
            capture(scene, "addons-many-streams")
            invoke(providesChip(scene, "Streams")); frames(scene)
            assertEquals(false, providesChip(scene, "Streams").config[SemanticsProperties.Selected])
            assertTrue(shown(scene, name(1)), "turning the chip off restores the list")
        } finally { scene.close() }
    }
}
