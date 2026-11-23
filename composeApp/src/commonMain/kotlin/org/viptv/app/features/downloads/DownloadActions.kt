package org.viptv.app.features.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import org.viptv.app.components.frameDelay
import org.viptv.app.connection.epochMillis
import org.viptv.app.downloads.DownloadItem
import org.viptv.app.downloads.Downloads
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal val LocalDownloadActions = compositionLocalOf<DownloadActions?> { null }

internal const val REQUEST_HOLD_MILLIS = 10_000L

@Stable
internal class DownloadActions(
    val downloads: Downloads,
    private val startTitle: (Card, SourceCandidate?) -> Unit,
    private val startSeason: (Card, Long) -> Unit,
    private val startNext: (Card, Int) -> Unit,
    private val startSeries: (Card) -> Unit,
    val chooseSource: (Card) -> Unit,
    val changeSource: (String) -> Unit,
    val play: (String) -> Unit,
    val openPage: () -> Unit,
    val notice: (String) -> Unit,
    val clock: () -> Long = ::epochMillis,
    val detailsOpened: (String) -> Unit = {},
) {
    private val requested = mutableStateMapOf<String, Long>()

    fun requested(card: Card): Boolean = downloadKey(card) in requested

    fun title(card: Card, source: SourceCandidate? = null) { mark(listOf(card)); startTitle(card, source) }

    fun season(series: Card, season: Long, episodes: List<Card>) { mark(episodes); startSeason(series, season) }

    fun next(series: Card, count: Int, episodes: List<Card>) { mark(episodes); startNext(series, count) }

    fun series(series: Card, episodes: List<Card>) { mark(episodes); startSeries(series) }

    fun retry(item: DownloadItem) { mark(listOf(item.card)); downloads.resume(item.id) }

    private fun mark(cards: List<Card>) {
        val now = clock()
        cards.forEach { requested[downloadKey(it)] = now }
    }

    internal fun settle(present: Set<String>) {
        val now = clock()
        requested.keys.filter { it in present || now - (requested[it] ?: now) >= REQUEST_HOLD_MILLIS }.forEach(requested::remove)
    }
}

@Composable
internal fun rememberDownloadIndex(actions: DownloadActions): Map<String, DownloadItem> {
    val items by actions.downloads.items.collectAsState()
    val index = remember(items) { items.associateBy { downloadKey(it.card) } }
    LaunchedEffect(index.keys) {
        actions.settle(index.keys)
        frameDelay(REQUEST_HOLD_MILLIS)
        actions.settle(index.keys)
    }
    return index
}

internal val LocalDownloadIndex = compositionLocalOf<Map<String, DownloadItem>> { emptyMap() }
