package org.viptv.app.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.components.TvAction
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
        var focusedId by rememberSaveable(term) { mutableStateOf<String?>(null) }
        val restoreId = remember { focusedId }
        val rows = rememberLazyListState()
        val scope = rememberCoroutineScope()
        var activeRow by remember { mutableIntStateOf(-1) }
        var rowMotion by remember { mutableStateOf<Job?>(null) }
        fun focusRow(index: Int) {
            if (activeRow != index) {
                activeRow = index; rowMotion?.cancel()
                rowMotion = scope.launch { rows.animateScrollToItem(index) }
            }
        }
        val unique = results.distinctBy { Triple(it.provider_id, it.identity.kind, it.identity.id) }
        val titles = unique.filter { it.identity.kind !in listOf("channel", "tv") }
        val channels = unique.filter { it.identity.kind in listOf("channel", "tv") }
        TvFocusScroll(manual = true) {
        Row(Modifier.fillMaxSize().padding(start = 96.dp * scale, end = 96.dp * scale, top = 8.dp * scale), horizontalArrangement = Arrangement.spacedBy(84.dp * scale)) {
            Column(Modifier.width(520.dp * scale)) {
                Row(Modifier.fillMaxWidth().height(72.dp * scale).background(Palette.raised, RoundedCornerShape(12.dp * scale)).padding(horizontal = 20.dp * scale),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
                    Icon(Lucide.Search, null, Modifier.size(28.dp * scale))
                    Row(Modifier.widthIn(max = 418.dp * scale), verticalAlignment = Alignment.CenterVertically) {
                    Text(term.ifBlank { "Search" }, Modifier.widthIn(max = 402.dp * scale), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = (32 * scale).sp, lineHeight = (38 * scale).sp, fontWeight = FontWeight.Medium,
                        color = if (term.isBlank()) Palette.secondary else Palette.text)
                    Box(Modifier.width(2.dp * scale).height(36.dp * scale).background(Palette.accent))
                    }
                }
                Spacer(Modifier.height(24.dp * scale))
                TvSearchKeyboard(editQuery, scale, requestInitialFocus = restoreId == null)
                Spacer(Modifier.height(68.dp * scale))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
                    suggestions.forEach { suggestion ->
                        TvAction(Modifier.fillMaxWidth().height(52.dp * scale), scale, activate = { query(suggestion) },
                            background = Color.Transparent, arrangement = Arrangement.Start) { tint ->
                            val color = if (tint == Palette.background) tint else Palette.body
                            Icon(Lucide.Search, null, Modifier.size(20.dp * scale), tint = color)
                            Spacer(Modifier.width(14.dp * scale))
                            Text(suggestion, fontSize = (26 * scale).sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            LazyColumn(Modifier.weight(1f), state = rows, verticalArrangement = Arrangement.spacedBy(66.dp * scale), contentPadding = PaddingValues(bottom = 96.dp * scale)) {
                if (busy && unique.isEmpty()) item { TvSearchLoading(scale) }
                error?.let { item { Text(it, color = Palette.danger) } }
                val groups = searchGroups(titles, addons)
                groups.forEachIndexed { rowIndex, group -> item(key = group.id) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                        Text(group.label,
                            Modifier.height(40.dp * scale), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp,
                            letterSpacing = (-.3f * scale).sp, fontWeight = FontWeight.SemiBold)
                        TvFocusScroll {
                        LazyRow(modifier = Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                            items(group.cards, key = { it.identity.kind + ":" + it.identity.id }) { card ->
                                val key = "${card.provider_id}:${card.identity.kind}:${card.identity.id}"
                                val requester = remember { FocusRequester() }
                                if (key == restoreId) LaunchedEffect(Unit) { withFrameNanos { }; requester.requestFocus() }
                                MediaCard(card, (212 * scale).toInt(), portrait = true, showLabels = true, labelScale = scale * 1.7f,
                                open = open, onFocus = { focusedId = key; focusRow(rowIndex + if (error != null) 1 else 0) }, modifier = Modifier.focusRequester(requester), cornerRadius = 12.dp * scale, focusWidth = 3.dp * scale) }
                        }
                        }
                    }
                } }
                if (channels.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                        Text("Channels", Modifier.height(36.dp * scale), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp,
                            letterSpacing = (-.3f * scale).sp, fontWeight = FontWeight.SemiBold)
                        TvFocusScroll {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                                items(channels, key = { it.provider_id + ":" + it.identity.id }) {
                                    TvSearchChannel(it, scale, open) { focusRow(groups.size + if (error != null) 1 else 0) }
                                }
                            }
                        }
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
