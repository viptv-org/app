package org.viptv.app

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.details.DetailResume
import org.viptv.app.features.details.ShelfFocusHandle
import org.viptv.app.features.details.TvEpisodeShelf
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.Test
import kotlin.test.assertEquals

class TvLandingScrollSafetyTest {
    @get:Rule val uiThread = UiThreadRule()

    private val show = Card(MediaIdentity("show", "series", emptyList()), provider_id = "fixture", title = "Show", badges = emptyList())
    private fun episodes(count: Int) = (1..count).map { number ->
        show.copy(title = "Episode $number", identity = MediaIdentity("episode-$number", "series", emptyList(),
            EpisodeCoordinates("aired", 1, number.toLong())))
    }

    @Test fun landingOnALateEpisodeScrollsWithoutAnUncaughtException() {
        val all = episodes(30)
        val resume = DetailResume(1L, 30L, 0.4, all.last())
        val leaks = mutableListOf<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> leaks += e; previous?.uncaughtException(Thread.currentThread(), e) }
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) {
                TvEpisodeShelf(show, all, scale = 1f, play = {}, focused = {}, expanded = true, resume = resume,
                    returnToHero = {}, focus = ShelfFocusHandle())
            }
        }
        try {
            var frame = 0L
            repeat(40) { scene.render(frame++ * 16_666_667L).close() }
        } finally {
            scene.close()
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        assertEquals(emptyList(), leaks.map { it.toString() }, "the landing scroll leaked an uncaught exception")
    }
}