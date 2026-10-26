package org.viptv.app.features.home

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import org.viptv.app.state.owner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.*

@Composable
fun HomeScreen(home: HomeSnapshot?, television: Boolean, open: (Card) -> Unit, retry: () -> Unit, desktopChrome: Boolean = false, play: (Card) -> Unit = open, tablet: Boolean = false, navigate: (org.viptv.app.state.Screen) -> Unit = {}, browser: Boolean = false, profile: org.viptv.app.connection.Profile? = null, save: (Card) -> Unit = {}, saved: (Card) -> Boolean = { false }, backChanged: ((() -> Unit)?) -> Unit = {}, busy: Boolean = home == null, error: String? = null,
    summaryCards: Map<String, Card> = emptyMap(), enrichSummary: (Card) -> Unit = {}) {
    if (television) {
        TvHomeScreen(home, open, play, retry, navigate, profile, summaryCards = summaryCards, save = save, saved = saved, backChanged = backChanged, busy = busy, error = error, enrichSummary = enrichSummary)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val carousel = rememberHeroCarousel(home?.featured.orEmpty().ifEmpty { listOfNotNull(home?.hero) }, true, home?.scope?.owner())
        val compact = maxWidth < 640.dp && !television
        val scale = if (television) maxWidth / 1920.dp else 1f
        val gutter = if (compact) 16.dp else if (television) 96.dp * scale else if (tablet) 32.dp else if (browser) 57.dp else 40.dp
        var focused by remember(home?.scope?.owner()) { mutableStateOf<Card?>(null) }
        LazyColumn(contentPadding = PaddingValues(bottom = if (compact) 96.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
            if (error != null || (home == null && !busy)) item(key = "status") {
                HomeStatus(home, error, busy, retry, navigate, Modifier.padding(horizontal = gutter, vertical = 16.dp))
            }
            if (busy && home != null) item(key = "refreshing") {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = gutter), color = Palette.accent)
            }
            val heroForSlot = carousel.card
            if (heroForSlot != null || (home == null && busy)) item(key = "hero") {
                val hero = heroForSlot
                Box(Modifier.fillMaxWidth()) {
                    if (!television && !tablet && !(compact && !desktopChrome)) hero?.landscape?.let { HomeBackdrop(it) }
                    Box(Modifier.padding(start = if (television || tablet) 0.dp else gutter, end = if (television || tablet) 0.dp else gutter, top = if (television || tablet || browser) 0.dp else 12.dp)) {
                        if (hero != null) Hero(hero, compact && !desktopChrome, television, open, play, tablet, navigate, browser, save, saved(hero), carousel)
                        else if (home == null && busy) Box(Modifier.fillMaxWidth().height(350.dp).clip(RoundedCornerShape(12.dp)).background(Palette.surface))
                    }
                }
            }
            if (home != null) {
                if (home.complete && home.rows.isEmpty() && home.hero == null && home.featured.isEmpty() && error == null) item(key = "empty") {
                    HomeStatus(home, null, busy, retry, navigate, Modifier.padding(horizontal = gutter, vertical = 16.dp))
                }
                items(home.rows, key = { it.id }) { row ->
                    HomeCatalogRow(row, compact, television,
                        Modifier.padding(horizontal = gutter),
                        { focused = it }, open, retry, scale, tablet, browser, play)
                }
            }
        }
    }
}
