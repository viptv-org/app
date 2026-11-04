package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.components.metadataParts
import org.viptv.app.features.settings.*
import org.viptv.app.theme.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.CardMetadata
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class TvDisplaySettingsTest {
    @get:Rule val uiThread = UiThreadRule()

    private class MemoryStore(var raw: String? = null) : DeviceRecordStore {
        override fun read() = raw
        override fun write(value: String) { raw = value }
    }

    private val rated = Card(MediaIdentity("tt0133093", "movie", emptyList()), provider_id = "addon:1",
        title = "Rated", badges = emptyList(),
        metadata = CardMetadata(year = 1999, runtime_minutes = 136, genres = listOf("Action"), rating = 8.7))

    @Test fun accentChoiceRecolorsTheAccentToken() {
        assertEquals(Color(0xFFF5C542), accentColor(TvDisplayPreferences()), "Amber is still the default")
        assertEquals(Color(0xFFFF8B5C), accentColor(TvDisplayPreferences(accent = 0xFFFF8B5C)), "Coral maps to the chosen colour")
        val scene = ImageComposeScene(20, 20, Density(1f)) {
            CompositionLocalProvider(LocalAccent provides Color(0xFFFF0000)) {
                ViptvTheme { AccentSwatch() }
            }
        }
        try {
            render(scene)
            assertEquals(Color(0xFFFF0000).toArgb(), pixel(scene, Offset(10f, 10f)), "Palette.accent draws the provided colour")
        } finally { scene.close() }
    }

    @Test fun ratingsToggleDropsTheStarFromTvCards() {
        assertTrue(rated.metadataParts(includeRating = true).any { it.startsWith("★") }, "ratings show by default")
        assertEquals(listOf("1999", "2h 16m", "Action"), rated.metadataParts(includeRating = false), "off drops the rating, keeps the rest")
        assertFalse(showRatings(TvDisplayPreferences(ratings = false)))
        assertTrue(showRatings(TvDisplayPreferences(ratings = true)))
    }

    @Test fun textSizeScalesTheTvTypeScale() {
        assertEquals(1f, tvTextScale(TvDisplayPreferences(textSize = "Default")))
        assertEquals(1.15f, tvTextScale(TvDisplayPreferences(textSize = "Large")))
        assertEquals(1.3f, tvTextScale(TvDisplayPreferences(textSize = "Larger")))
        assertTrue(textHeight(1.5f) > textHeight(1f), "a larger TV text scale draws taller type")
    }

    @Test fun fitToScreenDrivesThePlayerCrop() {
        assertFalse(fitToScreen(TvDisplayPreferences()))
        assertTrue(fitToScreen(TvDisplayPreferences(fitToScreen = true)))
    }

    @Test fun aStoredDisplayChangeReachesEveryReaderWithoutARestart() {
        val store = MemoryStore(encodeTvDisplay(TvDisplayPreferences()))
        val display = TvDisplayState(decodeTvDisplay(store.raw), store)
        val scene = ImageComposeScene(100, 100, Density(1f)) {
            CompositionLocalProvider(
                LocalAccent provides accentColor(display.value),
                LocalShowRatings provides showRatings(display.value),
                LocalTvTextScale provides tvTextScale(display.value),
                LocalFitToScreen provides fitToScreen(display.value),
            ) { DisplayValuesProbe() }
        }
        try {
            render(scene)
            assertEquals("${Color(0xFFF5C542).toArgb()},true,1.0,false", description(scene), "defaults reach every reader")
            display.change(TvDisplayPreferences(accent = 0xFFFF8B5C, ratings = false, textSize = "Larger", fitToScreen = true))
            render(scene)
            assertEquals("${Color(0xFFFF8B5C).toArgb()},false,1.3,true", description(scene), "a change reaches every reader without a restart")
        } finally { scene.close() }
    }

    @Composable
    private fun AccentSwatch() {
        Box(Modifier.size(20.dp).background(Palette.accent))
    }

    @Composable
    private fun DisplayValuesProbe() {
        val accent = LocalAccent.current.toArgb()
        val ratings = LocalShowRatings.current
        val text = LocalTvTextScale.current
        val fit = LocalFitToScreen.current
        Box(Modifier.size(10.dp).semantics { stateDescription = "$accent,$ratings,$text,$fit" })
    }

    private fun textHeight(scale: Float): Float {
        val scene = ImageComposeScene(200, 200, Density(1f)) {
            CompositionLocalProvider(LocalTvTextScale provides scale) {
                ViptvTheme(true) { Text("Measure", fontSize = 100.sp) }
            }
        }
        try {
            render(scene)
            return nodes(scene).first { it.config.contains(SemanticsProperties.Text) }.boundsInRoot.height
        } finally { scene.close() }
    }

    private fun pixel(scene: ImageComposeScene, at: Offset): Int =
        scene.render(clocks[scene] ?: 0L).use { Bitmap.makeFromImage(it) }.getColor(at.x.toInt(), at.y.toInt())

    private fun render(scene: ImageComposeScene, frames: Int = 3) {
        var time = clocks[scene] ?: 0L
        repeat(frames) { time += 16_666_667L; scene.render(time).close() }
        clocks[scene] = time
    }
    private val clocks = mutableMapOf<ImageComposeScene, Long>()

    private fun description(scene: ImageComposeScene): String =
        nodes(scene).first { it.config.contains(SemanticsProperties.StateDescription) }
            .config[SemanticsProperties.StateDescription]

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}