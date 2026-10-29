package org.viptv.app.features.home

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import org.viptv.app.connection.cardLookupKey
import org.viptv.app.state.owner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.*

@Composable
fun HomeScreen(home: HomeSnapshot?, television: Boolean, open: (Card) -> Unit, retry: () -> Unit, desktopChrome: Boolean = false, play: (Card) -> Unit = open, tablet: Boolean = false, navigate: (org.viptv.app.state.Screen) -> Unit = {}, browser: Boolean = false, profile: org.viptv.app.connection.Profile? = null, save: (Card) -> Unit = {}, saved: (Card) -> Boolean = { false }, backChanged: ((() -> Unit)?) -> Unit = {}, busy: Boolean = home == null, error: String? = null,
    summaryCards: Map<String, Card> = emptyMap(), enrichSummary: (Card) -> Unit = {},
    heroRotates: Boolean = true,
    continueMore: org.viptv.app.state.ContinueWatchingMore? = null, seeAll: () -> Unit = {}) {
    if (television) {
        TvHomeScreen(home, open, play, retry, navigate, profile, summaryCards = summaryCards, save = save, saved = saved, backChanged = backChanged, busy = busy, error = error, enrichSummary = enrichSummary,
            continueMore = continueMore, seeAll = seeAll)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val continueRow = home?.rows?.firstOrNull { it.id == CONTINUE_ROW }
        var hoveredContinue by remember(home?.scope?.owner()) { mutableStateOf<String?>(null) }
        val preview = hoveredContinue?.let { key -> continueRow?.cards?.firstOrNull { cardLookupKey(it) == key } }
        var heroFloor by remember(home?.scope?.owner()) { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val carousel = rememberHeroCarousel(home?.featured.orEmpty().ifEmpty { listOfNotNull(home?.hero) }, heroRotates && preview == null, home?.scope?.owner())
        val compact = maxWidth < 640.dp && !television
        val scale = if (television) maxWidth / 1920.dp else 1f
        val gutter = if (compact) 16.dp else if (television) 96.dp * scale else if (tablet) 32.dp else if (browser) 57.dp else 40.dp
        val viewportHeight = maxHeight
        var focused by remember(home?.scope?.owner()) { mutableStateOf<Card?>(null) }
        LazyColumn(contentPadding = PaddingValues(bottom = if (compact) 96.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
            if (error != null || (home == null && !busy)) item(key = "status") {
                HomeStatus(home, error, busy, retry, navigate, Modifier.padding(horizontal = gutter, vertical = 16.dp))
            }
            if (busy && home != null) item(key = "refreshing") {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = gutter), color = Palette.accent)
            }
            // A preview replaces the featured hero and keeps its footprint, so the rows below never move under the pointer.
            val heroForSlot = carousel.card?.let { preview ?: it }
            if (heroForSlot != null || (home == null && busy)) item(key = "hero") {
                val hero = heroForSlot
                Box(Modifier.fillMaxWidth().heightIn(min = if (preview == null) 0.dp else with(density) { heroFloor.toDp() })
                    .onGloballyPositioned { if (preview == null) heroFloor = it.size.height }) {
                    if (!television && !tablet && !(compact && !desktopChrome)) hero?.landscape?.let { HomeBackdrop(it) }
                    Box(Modifier.padding(start = if (television || tablet) 0.dp else gutter, end = if (television || tablet) 0.dp else gutter, top = if (television || tablet || browser) 0.dp else 12.dp)) {
                        if (hero != null) Hero(hero, compact && !desktopChrome, television, open, play, tablet, navigate, browser, save, saved(hero),
                            if (preview == null) carousel else null, viewportHeight = viewportHeight,
                            label = if (preview != null) continueRow?.title ?: "Continue watching" else "Featured")
                        else if (home == null && busy) Box(Modifier.fillMaxWidth().height(350.dp).clip(RoundedCornerShape(12.dp)).background(Palette.surface))
                    }
                }
            }
            if (home != null) {
                val visibleRows = home.rows.filter {
                    it.state == RowState.PENDING || (it.state == RowState.READY && it.cards.isNotEmpty())
                }
                if (home.complete && visibleRows.isEmpty() && home.hero == null && home.featured.isEmpty() && error == null) item(key = "empty") {
                    HomeStatus(home, null, busy, retry, navigate, Modifier.padding(horizontal = gutter, vertical = 16.dp))
                }
                items(visibleRows, key = { it.id }) { row ->
                    HomeCatalogRow(row, compact, television, Modifier,
                        { focused = it }, open, scale, tablet, browser, play, gutter = gutter, continueMore = continueMore, seeAll = seeAll,
                        hover = { card, inside ->
                            val key = cardLookupKey(card)
                            hoveredContinue = if (inside) key else hoveredContinue?.takeIf { it != key }
                        })
                }
            }
        }
    }
}
