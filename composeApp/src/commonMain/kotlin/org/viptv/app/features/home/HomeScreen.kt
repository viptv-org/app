package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.*

@Composable
fun HomeScreen(home: HomeSnapshot?, television: Boolean, open: (Card) -> Unit, retry: () -> Unit, desktopChrome: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 640.dp && !television
        val gutter = if (compact) 16.dp else if (television) 70.dp else 40.dp
        var focused by remember(home?.revision) { mutableStateOf<Card?>(null) }
        LazyColumn(contentPadding = PaddingValues(start = if (television) 0.dp else gutter, end = if (television) 0.dp else gutter,
            top = 12.dp, bottom = if (compact) 96.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item(key = "hero") {
                val hero = if (television) focused ?: home?.hero else home?.hero
                if (hero != null) Hero(hero, compact && !desktopChrome, television, open)
                else if (home == null) Box(Modifier.fillMaxWidth().height(350.dp).clip(RoundedCornerShape(12.dp)).background(Palette.surface))
            }
            if (home != null) {
                if (home.complete && home.rows.isEmpty()) item(key = "empty") {
                    Text("No catalogs are configured for this profile.", color = Palette.secondary)
                }
                items(home.rows, key = { it.id }) { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = if (television) Modifier.padding(horizontal = gutter) else Modifier) {
                        Text(row.title, style = MaterialTheme.typography.titleMedium)
                        when (row.state) {
                            RowState.READY -> LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(row.cards, key = { it.provider_id + ":" + it.identity.id }) { card ->
                                    MediaCard(card, if (compact) 240 else if (television) 290 else 280, onFocus = { focused = it }, open = open)
                                }
                            }
                            RowState.EMPTY -> Text("Nothing here yet.", color = Palette.secondary)
                            RowState.FAILED -> TextButton(onClick = retry) { Text(row.message ?: "Try again") }
                            RowState.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                repeat(3) { Box(Modifier.width(if (compact) 240.dp else 290.dp).aspectRatio(16f / 9).clip(RoundedCornerShape(9.dp)).background(Palette.surface)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
