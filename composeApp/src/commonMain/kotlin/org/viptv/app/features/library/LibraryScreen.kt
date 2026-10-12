package org.viptv.app.features.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.features.home.MediaCard
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
fun LibraryScreen(cards: List<Card>, available: Boolean, television: Boolean, open: (Card) -> Unit,
    history: () -> Unit, home: () -> Unit) {
    if (television) { TvLibraryScreen(cards, available, open, home); return }
    var filter by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf("Date added") }
    val filtered = cards.filter { filter == "All" || it.identity.kind == if (filter == "Movies") "movie" else "series" }
    val visible = when (sort) { "A–Z" -> filtered.sortedBy { it.title.lowercase() }; "Z–A" -> filtered.sortedByDescending { it.title.lowercase() }; else -> filtered }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp && !television
        Column(Modifier.fillMaxSize().padding(horizontal = if (phone) 16.dp else if (television) 70.dp else 40.dp,
            vertical = if (phone) 8.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (!phone) Text("My List", fontSize = if (television) 36.sp else 28.sp, letterSpacing = (-0.56).sp, fontWeight = FontWeight.Bold)
            LibraryControls(filter, { filter = it }, sort, { sort = it })
            Surface(onClick = history, shape = RoundedCornerShape(16.dp), color = Palette.surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Lucide.History, null, Modifier.size(22.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("History", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Everything you've played", fontSize = 12.sp, color = Palette.secondary)
                    }
                    Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = Palette.secondary)
                }
            }
            if (visible.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = if (phone) 96.dp else 0.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Palette.raised) {
                        Icon(Lucide.Bookmark, null, Modifier.padding(20.dp).size(32.dp), tint = Palette.secondary)
                    }
                    Text(if (available) "Your list is empty." else "My List is not available yet.", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(if (available) "Tap + on a movie or series to save it here." else "Saving titles is not available for this connection yet.", fontSize = 14.sp, color = Palette.secondary)
                    Button(onClick = home, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) { Text("Browse Home") }
                }
            } else LazyVerticalGrid(if (phone) GridCells.Fixed(3) else GridCells.Adaptive(if (television) 220.dp else 176.dp),
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = if (phone) 96.dp else 24.dp),
                horizontalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible.distinctBy { Triple(it.provider_id, it.identity.kind, it.identity.id) }, key = { it.provider_id + ":" + it.identity.kind + ":" + it.identity.id }) {
                    MediaCard(it, if (phone) 112 else 176, portrait = true, showLabels = !phone, open = open)
                }
            }
        }
    }
}
