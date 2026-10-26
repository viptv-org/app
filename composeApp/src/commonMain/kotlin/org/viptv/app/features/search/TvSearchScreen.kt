package org.viptv.app.features.search

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.features.home.MediaCard
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.app.connection.SearchResultRow

@Composable
internal fun TvSearchScreen(term: String, results: List<Card>, addons: List<IntegrationConnection>, busy: Boolean,
    error: String?, query: (String) -> Unit, open: (Card) -> Unit, editQuery: ((String) -> String) -> Unit,
    suggestions: List<String> = emptyList(), searchRows: List<SearchResultRow> = emptyList()) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        var focusedId by rememberSaveable(term) { mutableStateOf<String?>(null) }
        val restoreId = remember(term) { focusedId }
        val keyboardFocus = remember { FocusRequester() }
        var resultsHaveFocus by remember { mutableStateOf(false) }
        val rows = rememberLazyListState()
        val sectionScroll = org.viptv.app.components.rememberTvSectionScroll(rows)
        val unique = results.distinctBy(::searchCardKey)
        val titles = unique.filter { it.identity.kind !in listOf("channel", "tv") }
        val channels = if (searchRows.isEmpty()) unique.filter { it.identity.kind in listOf("channel", "tv") } else emptyList()
        val groups = if (searchRows.isNotEmpty()) searchResultGroups(searchRows) else searchGroups(titles, addons)
        val hasResults = if (searchRows.isNotEmpty()) searchRows.any { it.cards.isNotEmpty() } else unique.isNotEmpty()

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
                TvSearchKeyboard(editQuery, scale, requestInitialFocus = restoreId == null, first = keyboardFocus)
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
            // A restore target that vanished (results re-fetched while away) must not
            // leave the page focusless: fall back to the keyboard after two frames.
            LaunchedEffect(restoreId) {
                if (restoreId != null) {
                    withFrameNanos { }; withFrameNanos { }
                    if (!resultsHaveFocus) runCatching { keyboardFocus.requestFocus() }
                }
            }
            LazyColumn(Modifier.weight(1f).onFocusChanged { resultsHaveFocus = it.hasFocus }, state = rows, sectionControlled = true, verticalArrangement = Arrangement.spacedBy(66.dp * scale), contentPadding = PaddingValues(bottom = 96.dp * scale)) {
                if (busy && !hasResults && groups.none { it.message != null }) item { TvSearchLoading(scale) }
                error?.let { item { Text(it, color = Palette.danger) } }
                groups.forEachIndexed { rowIndex, group -> item(key = group.id) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                        Text(group.label,
                            Modifier.height(40.dp * scale), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp,
                            letterSpacing = (-.3f * scale).sp, fontWeight = FontWeight.SemiBold)
                        (group.message ?: "No matches.".takeIf { group.cards.isEmpty() })?.let { Text(it, fontSize = (22 * scale).sp, color = Palette.secondary) }

                        LazyRow(modifier = Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                            items(group.cards, key = ::searchCardKey) { card ->
                                val key = searchFocusKey(group, card)
                                val requester = remember { FocusRequester() }
                                if (key == restoreId) LaunchedEffect(Unit) { withFrameNanos { }; requester.requestFocus() }
                                MediaCard(card, (212 * scale).toInt(), portrait = true, showLabels = true, labelScale = scale * 1.7f,
                                open = open, onFocus = { focusedId = key; sectionScroll.focus(rowIndex + if (error != null) 1 else 0) }, modifier = Modifier.focusRequester(requester), cornerRadius = 12.dp * scale, focusWidth = 3.dp * scale,
                                watchedBadgeSize = 34.dp * scale) }
                        }

                    }
                } }
                if (channels.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                        Text("Channels", Modifier.height(36.dp * scale), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp,
                            letterSpacing = (-.3f * scale).sp, fontWeight = FontWeight.SemiBold)

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                                itemsIndexed(channels, key = { _, it -> it.provider_id + ":" + it.identity.id }) { index, channel ->
                                    TvSearchChannel(channel, scale, open,
                                        onFocus = { sectionScroll.focus(groups.size + if (error != null) 1 else 0) },
                                        last = index == channels.lastIndex)
                                }
                            }

                    }
                }
                if (!busy && error == null && !hasResults && groups.none { it.message != null }) item {
                    Text(if (term.isBlank()) "Search movies, series and channels" else "No matches. Try another search.", fontSize = (24 * scale).sp, color = Palette.secondary)
                }
            }
        }

    }
}
