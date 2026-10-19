package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.features.home.TvCatalogCard
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import java.awt.EventQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Deterministic pixel capture of the TV catalog card. The live TV catalog is
 * randomised per launch, so a card rendered from a fixed fixture is the only
 * repeatable way to prove a rendering change is pixel-identical.
 * Set VIPTV_CARD_CAPTURE_DIR to write PNGs.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvCardVisualTest {
    private val edt = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = !EventQueue.isDispatchThread()
        override fun dispatch(context: CoroutineContext, block: Runnable) = EventQueue.invokeLater(block)
    }
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(edt, block)

    private fun card(poster: String?, progress: Double? = null) = Card(
        MediaIdentity("tt1", "movie", emptyList()), provider_id = "simkl", title = "Cached title",
        subtitle = "1h 52m left", badges = listOf("Simkl", "4K"), progress = progress, poster = poster,
        metadata = CardMetadata(year = 2025, runtime_minutes = 108, rating = 7.4, certification = "R"),
    )

    @Test fun captureFocusedCards() = onEdt {
        for ((name, portrait) in listOf("tv-card-focus-portrait" to true, "tv-card-focus-wide" to false)) {
            val model = card(null, 0.42)
            val scene = ImageComposeScene(720, 560, Density(1f)) { ViptvTheme(true) {
                val requester = remember { FocusRequester() }
                LaunchedEffect(Unit) { requester.requestFocus() }
                Row(Modifier.fillMaxSize().background(Palette.background).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvCatalogCard(model, 1f, portrait, Modifier.focusRequester(requester), focused = {}, open = {}, focusedCaptions = portrait)
                    TvCatalogCard(model, 1f, portrait, focused = {}, open = {}, focusedCaptions = portrait)
                }
            } }
            try {
                repeat(12) { scene.render(it * 16_666_667L).close(); delay(4) }
                System.getenv("VIPTV_CARD_CAPTURE_DIR")?.let { path ->
                    File(path).mkdirs()
                    scene.render(4_000_000_000L).use { image ->
                        image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) }
                    }
                }
            } finally { scene.close() }
        }
    }

    /**
     * The canvas dictionary §A1 matrix from Tv3CardStates: every state the wire
     * can produce, rendered from a fixed fixture so a regression is a pixel diff
     * instead of a hunt through the live catalog.
     */
    @Test fun captureEveryCardState() = onEdt {
        val states = listOf(
            "full" to card(null),
            "watched" to card(null).copy(badges = listOf("Watched", "4K")),
            "new-episode" to card(null).copy(badges = listOf("New episode", "Returning"),
                identity = MediaIdentity("tt2", "series", emptyList())),
            "ended" to card(null).copy(badges = listOf("Ended"), identity = MediaIdentity("tt3", "series", emptyList())),
            "title-only" to card(null).copy(metadata = null),
            "episode" to card(null).copy(subtitle = "S1 E2 · 52m",
                identity = MediaIdentity("tt4", "series", emptyList(), episode = EpisodeCoordinates("aired", 1, 2))),
            "live" to card(null).copy(badges = listOf("LIVE"), identity = MediaIdentity("ch1", "channel", emptyList())),
            "progress" to card(null, 0.42),
        )
        val scene = ImageComposeScene(1100, 1000, Density(1f)) { ViptvTheme(true) {
            Column(Modifier.fillMaxSize().background(Palette.background).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                states.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        row.forEach { (_, model) ->
                            TvCatalogCard(model, 1f, true, focused = {}, open = {}, focusedCaptions = true)
                        }
                    }
                }
            }
        } }
        try {
            repeat(10) { scene.render(it * 16_666_667L).close(); delay(4) }
            System.getenv("VIPTV_CARD_CAPTURE_DIR")?.let { path ->
                File(path).mkdirs()
                scene.render(4_000_000_000L).use { image ->
                    image.encodeToData()!!.use { File(path, "tv-card-states.png").writeBytes(it.bytes) }
                }
            }
        } finally { scene.close() }
    }

    @Test fun captureCardsWithAndWithoutArtwork() = onEdt {
        for ((name, model) in listOf(
            "tv-card-no-art" to card(null),
            "tv-card-progress" to card(null, 0.42),
        )) {
            val scene = ImageComposeScene(560, 480, Density(1f)) { ViptvTheme(true) {
                Row(Modifier.fillMaxSize().background(Palette.background).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvCatalogCard(model, 1f, true, focused = {}, open = {})
                    TvCatalogCard(model, 1f, true, focused = {}, open = {})
                }
            } }
            try {
                repeat(8) { scene.render(it * 16_666_667L).close(); delay(4) }
                System.getenv("VIPTV_CARD_CAPTURE_DIR")?.let { path ->
                    File(path).mkdirs()
                    scene.render(4_000_000_000L).use { image ->
                        image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) }
                    }
                }
            } finally { scene.close() }
        }
    }
}
