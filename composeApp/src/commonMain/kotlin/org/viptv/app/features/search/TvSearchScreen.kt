package org.viptv.app.features.search

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.ListEndFooter
import org.viptv.app.components.LIST_TITLES
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvTextField
import org.viptv.app.components.bleedHorizontally
import org.viptv.app.components.requestPlacedFocus
import org.viptv.app.features.home.MediaCard
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.app.connection.SearchResultRow

private const val SEARCH_FOCUS_RETRY_FRAMES = 32

@Composable
internal fun TvSearchScreen(term: String, results: List<Card>, addons: List<IntegrationConnection>, busy: Boolean,
    error: String?, query: (String) -> Unit, open: (Card) -> Unit, editQuery: ((String) -> String) -> Unit,
    suggestions: List<String> = emptyList(), searchRows: List<SearchResultRow> = emptyList(), focusResults: Boolean = false,
    retry: (() -> Unit)? = null) {
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val scale = maxWidth / 1920.dp
        val gutter = 96.dp * scale
        var focusedId by rememberSaveable(term) { mutableStateOf<String?>(null) }
        val restoreId = remember(term) { focusedId }
        // Entry decides once: a later focus change inside this screen must not pull the remote back into the field.
        val enterWithInput = remember { restoreId == null && !focusResults }
        val field = remember { FocusRequester() }
        val resultsAction = remember { FocusRequester() }
        val firstResultFocus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        var resultsHaveFocus by remember { mutableStateOf(false) }
        var fieldFocused by remember { mutableStateOf(false) }
        var inputShown by remember { mutableStateOf(false) }
        var resultsRequested by remember { mutableStateOf(false) }
        val rows = rememberLazyListState()
        val sectionScroll = org.viptv.app.components.rememberTvSectionScroll(rows)
        val unique = results.distinctBy(::searchCardKey)
        val titles = unique.filter { it.identity.kind !in listOf("channel", "tv") }
        val channels = if (searchRows.isEmpty()) unique.filter { it.identity.kind in listOf("channel", "tv") } else emptyList()
        val groups = if (searchRows.isNotEmpty()) searchResultGroups(searchRows) else searchGroups(titles, addons)
        val hasResults = if (searchRows.isNotEmpty()) searchRows.any { it.cards.isNotEmpty() } else unique.isNotEmpty()
        val firstResultGroup = groups.indexOfFirst { it.cards.isNotEmpty() }
        val showResults: () -> Unit = { keyboard?.hide(); resultsRequested = true }
        LaunchedEffect(focusResults, resultsRequested, hasResults) {
            if (!focusResults && !resultsRequested) return@LaunchedEffect
            if (!hasResults) { resultsRequested = false; return@LaunchedEffect }
            repeat(SEARCH_FOCUS_RETRY_FRAMES) {
                withFrameNanos { }
                if (firstResultFocus.requestPlacedFocus()) { resultsRequested = false; return@LaunchedEffect }
            }
        }
        // Focus alone opens the device IME again, so a field that regains focus from a shelf keeps input closed until Select asks for it.
        LaunchedEffect(fieldFocused) {
            if (!fieldFocused) return@LaunchedEffect
            if (!inputShown) { inputShown = true; return@LaunchedEffect }
            withFrameNanos { }
            keyboard?.hide()
        }
        LaunchedEffect(restoreId) {
            if (restoreId == null) return@LaunchedEffect
            withFrameNanos { }; withFrameNanos { }
            if (resultsHaveFocus) return@LaunchedEffect
            runCatching { field.requestFocus() }
            withFrameNanos { }
            keyboard?.hide()
        }

        Column(Modifier.fillMaxSize().padding(start = gutter, end = gutter, top = 8.dp * scale)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                TvTextField(term, { next -> editQuery { next } }, scale, Modifier.width(960.dp * scale),
                    placeholder = SEARCH_PLACEHOLDER, imeAction = ImeAction.Search, submit = showResults,
                    requester = field, focusOnEnter = enterWithInput,
                    onFocusChanged = { fieldFocused = it })
                TvVoiceSearchAction(scale) { recognized -> editQuery { recognized } }
                TvAction(Modifier.heightIn(min = 80.dp * scale).widthIn(min = 240.dp * scale), scale, primary = true,
                    requester = resultsAction, enabled = hasResults, contentPadding = PaddingValues(horizontal = 44.dp * scale),
                    activate = showResults) { tint ->
                    Text("Results", color = tint, fontSize = (28 * scale).sp, lineHeight = (34 * scale).sp, fontWeight = FontWeight.Bold)
                }
            }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(20.dp * scale))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    suggestions.forEach { suggestion ->
                        TvAction(Modifier.heightIn(min = 56.dp * scale), scale, ring = false, cornerRadius = 28.dp * scale,
                            background = Color.White.copy(alpha = .09f), contentPadding = PaddingValues(horizontal = 22.dp * scale),
                            activate = { query(suggestion) }) { tint ->
                            Icon(Lucide.Search, null, Modifier.size(22.dp * scale), tint)
                            Spacer(Modifier.width(10.dp * scale))
                            Text(suggestion, color = tint, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(36.dp * scale))
            LazyColumn(Modifier.weight(1f).bleedHorizontally(left = 0.dp, right = gutter)
                .onFocusChanged { resultsHaveFocus = it.hasFocus }, state = rows, sectionControlled = true,
                verticalArrangement = Arrangement.spacedBy(36.dp * scale), contentPadding = PaddingValues(bottom = gutter)) {
                if (busy && !hasResults && groups.none { it.message != null }) item { TvSearchLoading(scale) }
                error?.let { message -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                        Text(message, fontSize = (24 * scale).sp, color = Palette.danger)
                        if (retry != null) TvSearchRetry(retry, scale)
                    }
                } }
                groups.forEachIndexed { rowIndex, group -> item(key = group.id) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                        Text(group.label,
                            Modifier.heightIn(min = 40.dp * scale), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp,
                            letterSpacing = (-.3f * scale).sp, fontWeight = FontWeight.SemiBold)
                        group.message?.let { message ->
                            if (group.failed) Column(verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                                Text(message, fontSize = (22 * scale).sp, color = Palette.danger)
                                retry?.let { again -> TvSearchRetry(again, scale) }
                            } else Text(message, fontSize = (22 * scale).sp, color = Palette.secondary)
                        }

                        LazyRow(modifier = Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                            itemsIndexed(group.cards, key = { _, card -> searchCardKey(card) }) { index, card ->
                                val key = searchFocusKey(group, card)
                                val own = remember { FocusRequester() }
                                val requester = if (rowIndex == firstResultGroup && index == 0) firstResultFocus else own
                                if (key == restoreId) LaunchedEffect(Unit) { withFrameNanos { }; requester.requestFocus() }
                                MediaCard(card, (212 * scale).toInt(), portrait = true, showLabels = true, labelScale = scale * 1.7f,
                                searchQuery = term,
                                open = open, onFocus = { focusedId = key; sectionScroll.focus(rowIndex + if (error != null) 1 else 0) },
                                    modifier = Modifier.focusRequester(requester).then(
                                        if (index == 0) Modifier.focusProperties { left = field } else Modifier),
                                    cornerRadius = 12.dp * scale, focusWidth = 3.dp * scale,
                                    watchedBadgeSize = 34.dp * scale, scale = scale) }
                        }

                    }
                } }
                if (channels.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                        Text("Channels", Modifier.heightIn(min = 36.dp * scale), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp,
                            letterSpacing = (-.3f * scale).sp, fontWeight = FontWeight.SemiBold)

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                                itemsIndexed(channels, key = { _, it -> it.provider_id + ":" + it.identity.id }) { index, channel ->
                                    TvSearchChannel(channel, scale, open,
                                        onFocus = { sectionScroll.focus(groups.size + if (error != null) 1 else 0) },
                                        last = index == channels.lastIndex,
                                        modifier = if (index == 0) Modifier.focusProperties { left = field } else Modifier)
                                }
                            }

                    }
                }
                if (busy && hasResults) item(key = "footer") {
                    ListEndFooter(loading = true, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = true, scale = scale)
                } else if (!busy && error == null && hasResults) item(key = "footer") {
                    ListEndFooter(ended = true, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = true, scale = scale)
                }
                if (!busy && term.isNotBlank() && error == null && !hasResults) item {
                    val copy = searchNoResultsCopy(searchRows)
                    if (searchFailed(searchRows)) Column(verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                        Text(copy, fontSize = (24 * scale).sp, color = Palette.danger)
                        retry?.let { again -> TvSearchRetry(again, scale) }
                    } else Text(copy, fontSize = (24 * scale).sp, color = Palette.secondary)
                }
            }
        }
    }
}

@Composable
private fun TvSearchRetry(retry: () -> Unit, scale: Float) {
    TvAction(Modifier.heightIn(min = 52.dp * scale).width(200.dp * scale), scale, primary = true,
        activate = retry) { tint -> Text("Try again", color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold) }
}