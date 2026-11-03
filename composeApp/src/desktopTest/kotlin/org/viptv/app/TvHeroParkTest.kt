package org.viptv.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.viptv.app.features.home.HeroCarousel
import org.viptv.app.features.home.rememberHeroCarousel
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class TvHeroParkTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun card(id: String) = Card(MediaIdentity(id, "movie", emptyList()), provider_id = "fixture", title = "Card $id", badges = emptyList())

    @Test fun heroPresentsOnceThenParksAndResumesOnInput() = runBlocking {
        val cards = listOf(card("a"), card("b"))
        val enabledFlow = MutableStateFlow(true)
        val observed = mutableStateOf<HeroCarousel?>(null)
        val start = System.nanoTime()
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            val enabled by enabledFlow.collectAsState()
            val carousel = rememberHeroCarousel(cards, enabled, scopeKey = Unit, parkAfterPass = true)
            SideEffect { observed.value = carousel }
        }
        fun pump(millis: Long): Int {
            var frames = 0
            val until = System.nanoTime() + millis * 1_000_000
            while (System.nanoTime() < until) {
                if (scene.hasInvalidations()) { scene.render(System.nanoTime() - start).close(); frames++ }
                Thread.sleep(4)
            }
            return frames
        }
        fun flush(count: Int = 8) = repeat(count) { scene.render(System.nanoTime() - start).close(); Thread.sleep(8) }
        try {
            pump(500)
            assertEquals(0, observed.value?.index, "the pass starts at the first featured card")

            pump(9_000)
            assertEquals(1, observed.value?.index, "the hero advances through the featured order")

            pump(9_000)
            assertEquals(1, observed.value?.index, "the hero parks on the last slide")
            assertEquals(1f, observed.value?.progress?.fraction, "the progress fill freezes at the end of the pass")

            pump(500)
            val parkedFrames = pump(4_000)
            assertTrue(parkedFrames < 5, "a parked hero asked for $parkedFrames frames in 4 s")

            enabledFlow.value = false
            flush()
            pump(300)
            enabledFlow.value = true
            flush()
            pump(300)
            assertEquals(0, observed.value?.index, "input starts a fresh pass at the first card")
            assertTrue(pump(1_000) > 2, "the fresh pass schedules frames")
        } finally {
            scene.close()
        }
    }
}