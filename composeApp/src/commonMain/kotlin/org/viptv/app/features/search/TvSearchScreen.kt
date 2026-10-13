package org.viptv.app.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.features.home.MediaCard
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.IntegrationConnection

@Composable
internal fun TvSearchScreen(term: String, results: List<Card>, addons: List<IntegrationConnection>, busy: Boolean,
    error: String?, query: (String) -> Unit, open: (Card) -> Unit, editQuery: ((String) -> String) -> Unit,
    suggestions: List<String> = emptyList()) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        val unique = results.distinctBy { Triple(it.provider_id, it.identity.kind, it.identity.id) }
        val titles = unique.filter { it.identity.kind !in listOf("channel", "tv") }
        val channels = unique.filter { it.identity.kind in listOf("channel", "tv") }
        TvFocusScroll {
        Row(Modifier.fillMaxSize().padding(start = 96.dp * scale, end = 96.dp * scale, top = 8.dp * scale), horizontalArrangement = Arrangement.spacedBy(84.dp * scale)) {
            Column(Modifier.width(520.dp * scale)) {
                Row(Modifier.fillMaxWidth().height(72.dp * scale).background(Palette.raised, RoundedCornerShape(12.dp * scale)).padding(horizontal = 20.dp * scale),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    Icon(Lucide.Search, null, Modifier.size(28.dp * scale))
                    Text(term.ifBlank { "Search" }, Modifier.widthIn(max = 418.dp * scale), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = (30 * scale).sp, lineHeight = (38 * scale).sp, fontWeight = FontWeight.SemiBold,
                        color = if (term.isBlank()) Palette.secondary else Palette.text)
                    Box(Modifier.width(2.dp * scale).height(36.dp * scale).background(Palette.accent))
                }
                Spacer(Modifier.height(24.dp * scale))
                TvSearchKeyboard(editQuery, scale)
                Spacer(Modifier.height(68.dp * scale))
                suggestions.forEach { suggestion ->
                    Row(Modifier.fillMaxWidth().height(56.dp * scale).clickable { query(suggestion) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                        Icon(Lucide.Search, null, Modifier.size(20.dp * scale), tint = Palette.body)
                        Text(suggestion, fontSize = (26 * scale).sp, color = Palette.body)
                    }
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(66.dp * scale), contentPadding = PaddingValues(bottom = 96.dp * scale)) {
                if (busy && unique.isEmpty()) item { TvSearchLoading(scale) }
                error?.let { item { Text(it, color = Palette.danger) } }
                val groups = titles.groupBy { it.provider_id }
                groups.forEach { (provider, cards) -> item(key = provider) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                        Text(if (groups.size == 1) "Top results" else addons.find { it.id == provider }?.name ?: "Results",
                            Modifier.height(40.dp * scale), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                            items(cards, key = { it.identity.kind + ":" + it.identity.id }) { MediaCard(it, (212 * scale).toInt(), portrait = true, showLabels = false,
                                open = open, cornerRadius = 12.dp * scale, focusWidth = 3.dp * scale) }
                        }
                    }
                } }
                if (channels.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                        Text("Channels", Modifier.height(36.dp * scale), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) { items(channels, key = { it.provider_id + ":" + it.identity.id }) { TvSearchChannel(it, scale, open) } }
                    }
                }
                if (!busy && error == null && unique.isEmpty()) item {
                    Text(if (term.isBlank()) "Search movies, series and channels" else "No matches. Try another search.", fontSize = (24 * scale).sp, color = Palette.secondary)
                }
            }
        }
        }
    }
}
