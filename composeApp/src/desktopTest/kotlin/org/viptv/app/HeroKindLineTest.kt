package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.components.heroMetadataParts
import org.viptv.app.components.kindLabel
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.CardMetadata
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class HeroKindLineTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val movie = Card(MediaIdentity("tt473", "movie", emptyList()), provider_id = "addon:21", title = "Fixture film",
        badges = emptyList(),
        metadata = CardMetadata(year = 2026, runtime_minutes = 145, genres = listOf("Action", "Adventure"), rating = 8.0))
    private val series = Card(MediaIdentity("tt474", "series", emptyList()), provider_id = "addon:21", title = "Fixture show",
        badges = emptyList(),
        metadata = CardMetadata(year = 2025, runtime_minutes = 24, genres = listOf("Animation", "Action", "Fantasy"), rating = 6.5))
    private val anime = series.copy(identity = series.identity.copy(kind = "anime"), title = "Fixture anime")
    private val titles = listOf(movie, series, anime)

    @Test fun theTitleKindIsWordedOnceForEveryHero() {
        assertEquals(listOf("Movie", "Series", "Anime"), titles.map(Card::kindLabel))
        assertEquals(listOf("Movie", "2026", "2h 25m", "Action / Adventure", "★ 8.0"), movie.heroMetadataParts())
        assertEquals(listOf("Series", "2025", "24m", "Animation / Action / Fantasy", "★ 6.5"), series.heroMetadataParts())
    }

    @Test fun missingValuesLeaveNoDanglingSeparator() {
        val sparse = movie.copy(metadata = CardMetadata(year = 2026))
        assertEquals(listOf("Movie", "2026"), sparse.heroMetadataParts())
        assertEquals(listOf("Series", "2025", "Drama"), series.copy(metadata = null, subtitle = "2025 · Drama").heroMetadataParts())
    }

    @Test fun aTitleWithNeitherKindNorMetadataDrawsNoLine() = onEdt {
        val bare = movie.copy(identity = movie.identity.copy(kind = ""), metadata = null, subtitle = null)
        assertEquals(emptyList(), bare.heroMetadataParts())
        Details(390, 844, DetailSnapshot(bare, episodes = emptyList())).use { page ->
            page.settle()
            assertTrue(page.texts().none { it.contains(" · ") }, "no metadata line at all: ${page.texts()}")
        }
    }

    @Test fun thePhoneHeroLineOpensWithTheKind() = onEdt {
        for (width in listOf(390, 360)) for (card in titles) Details(width, 844, DetailSnapshot(card, episodes = emptyList())).use { page ->
            page.settle()
            assertTrue(page.texts().any { it.startsWith("${card.kindLabel()} · ") },
                "${card.title} at ${width}dp opens its hero line with ${card.kindLabel()}: ${page.texts()}")
        }
    }

    @Test fun thePhoneHeroLineKeepsTheRestOfTheMetadataAfterTheKind() = onEdt {
        Details(390, 844, DetailSnapshot(movie, episodes = emptyList())).use { page ->
            page.settle()
            assertTrue(page.texts().contains("Movie · 2026 · 2h 25m · Action / Adventure · ★ 8.0"),
                "the whole hero line: ${page.texts()}")
        }
    }

    @Test fun theTabletHeroLineOpensWithTheKind() = onEdt {
        for (card in titles) Details(1280, 800, DetailSnapshot(card, episodes = emptyList()), tablet = true).use { page ->
            page.settle()
            assertTrue(page.texts().any { it.startsWith("${card.kindLabel()} · ") },
                "${card.title} opens the tablet hero line with ${card.kindLabel()}: ${page.texts()}")
        }
    }

    @Test fun theDesktopHeroKeepsItsOwnKindLabel() = onEdt {
        for (card in titles) Details(1280, 800, DetailSnapshot(card, episodes = emptyList())).use { page ->
            page.settle()
            assertTrue(page.texts().contains(card.kindLabel()),
                "${card.title} names its kind on desktop: ${page.texts()}")
        }
    }

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Details(val width: Int, val height: Int, initial: DetailSnapshot,
        val tablet: Boolean = false) : AutoCloseable {
        var detail by mutableStateOf(initial)
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(false) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, tablet = tablet)
            }
        } }
        private var frame = 0L
        suspend fun frames(count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); yield() }
        suspend fun settle() { frames(); delay(200); frames() }
        fun texts() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        override fun close() = scene.close()
    }
}
