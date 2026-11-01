package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import org.viptv.app.components.LocalTvColumnFocus
import org.viptv.app.components.TvAction
import org.viptv.app.components.tvRingReach
import org.viptv.app.components.tvRingRoom
import org.viptv.app.features.entry.TvEntryKeyboard
import org.viptv.app.features.search.TvSearchKeyboard
import org.viptv.app.state.VodMatchForm
import org.viptv.app.state.VodMatchRow
import org.viptv.app.state.VodMatchesState
import org.viptv.app.theme.Palette

@Composable
internal fun TvVodMatchesSettings(state: VodMatchesState, scale: Float, initial: FocusRequester, sidebar: FocusRequester,
    providerChanged: (Long?) -> Unit, typeChanged: (String?) -> Unit, search: (String) -> Unit,
    openForm: (VodMatchRow) -> Unit, loadMore: () -> Unit, retry: () -> Unit,
    closeForm: () -> Unit, changeMetadata: (String) -> Unit, save: () -> Unit) {
    var searching by remember { mutableStateOf(false) }
    var wasSearching by remember { mutableStateOf(false) }
    val searchChip = remember { FocusRequester() }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var openedRow by remember { mutableStateOf<String?>(null) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(state.loaded) {
        if (!state.loaded || entered) return@LaunchedEffect
        val target = if (state.rows.isEmpty()) searchChip else initial
        repeat(3) {
            withFrameNanos { }
            if (target.requestFocus()) { entered = true; return@LaunchedEffect }
        }
    }
    LaunchedEffect(searching) {
        if (searching) wasSearching = true
        else if (wasSearching) { withFrameNanos { }; runCatching { searchChip.requestFocus() } }
    }
    LaunchedEffect(state.form?.vodId) {
        val form = state.form
        if (form != null) openedRow = form.vodId
        else openedRow?.let { id -> withFrameNanos { }; runCatching { requesters[id]?.requestFocus() } }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        TvSettingsHeading("VOD matches", scale, subtitle = "Provider titles that still need a metadata match.")
        val open = state.form
        if (open != null) {
            TvVodMatchForm(open, state.saving, state.error, scale, changeMetadata, save, { if (!state.saving) closeForm() })
            return@Column
        }
        TvVodMatchFilters(state, scale, searchChip, searching, providerChanged, typeChanged, { searching = true },
            { providerChanged(null); typeChanged(null); search("") })
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(60.dp * scale)) {
            // Inside the list its own focus search moves through every row: the page's TvFocusColumn clamp only knows composed rows and stops Down at the last visible one.
            CompositionLocalProvider(LocalTvColumnFocus provides null) {
                LazyColumn(Modifier.weight(1f).fillMaxHeight().tvRingRoom(tvRingReach(scale), horizontal = false),
                    contentPadding = PaddingValues(top = tvRingReach(scale), bottom = 72.dp * scale),
                    verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    state.error?.let { message ->
                        item(key = "error") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                                Text(message, color = Palette.danger, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
                                TvAction(Modifier.height(64.dp * scale), scale, activate = retry,
                                    contentPadding = PaddingValues(horizontal = 24.dp * scale)) { tint ->
                                    Text("Retry", color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (state.loaded && state.rows.isEmpty() && state.error == null) {
                        item(key = "empty") {
                            Text(if (state.filtered) "No matching titles. Change the filters or search." else "No unmatched titles.",
                                Modifier.padding(horizontal = 24.dp * scale), color = Palette.secondary, fontSize = (22 * scale).sp)
                        }
                    }
                    items(state.rows, key = { it.vodId }) { row ->
                        val requester = requesters.getOrPut(row.vodId) {
                            if (row.vodId == state.rows.firstOrNull()?.vodId) initial else FocusRequester()
                        }
                        TvAction(Modifier.fillMaxWidth().height(112.dp * scale).focusProperties { left = sidebar },
                            scale, requester = requester, ring = false, enabled = !state.saving, background = Color(0xFF191919),
                            arrangement = Arrangement.Start, contentPadding = PaddingValues(horizontal = 24.dp * scale),
                            activate = { openForm(row) }) { tint ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
                                Text(row.name, color = tint, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp,
                                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(state.providerName(row.providerId), vodMatchTypeLabel(row.type),
                                    row.year?.toString()).joinToString(" · "), color = tint.copy(alpha = 0.7f),
                                    fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
                                Text(row.metadataId?.let { "Matched · $it" } ?: "No metadata match",
                                    color = tint.copy(alpha = 0.7f), fontSize = (20 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (row.metadataId != null) "Edit match" else "Match title", color = tint,
                                    fontSize = (22 * scale).sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (state.nextCursor != null) {
                        item(key = "more") {
                            TvAction(Modifier.fillMaxWidth().height(80.dp * scale), scale, enabled = !state.loading,
                                background = Color(0xFF191919), activate = loadMore) { tint ->
                                Text("Load more titles", color = tint, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            if (searching) TvVodMatchSearchPanel(state.query, { search(it.take(128)) }, scale, finish = { searching = false })
        }
    }
}

@Composable
private fun TvVodMatchFilters(state: VodMatchesState, scale: Float, searchChip: FocusRequester, searching: Boolean,
    providerChanged: (Long?) -> Unit, typeChanged: (String?) -> Unit, startSearch: () -> Unit, clear: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        item(key = "search") {
            TvVodMatchChip("Search", Lucide.Search, searching || state.query.isNotBlank(), scale, searchChip, startSearch)
        }
        item(key = "all") {
            TvVodMatchChip("All providers", null, state.providerId == null, scale, null) { providerChanged(null) }
        }
        state.providers.forEach { provider ->
            item(key = "provider:${provider.id}") {
                TvVodMatchChip(provider.name, null, state.providerId == provider.id, scale, null) {
                    providerChanged(if (state.providerId == provider.id) null else provider.id)
                }
            }
        }
        item(key = "movies") {
            TvVodMatchChip("Movies", null, state.type == "movie", scale, null) {
                typeChanged(if (state.type == "movie") null else "movie")
            }
        }
        item(key = "series") {
            TvVodMatchChip("Series", null, state.type == "series", scale, null) {
                typeChanged(if (state.type == "series") null else "series")
            }
        }
        if (state.filtered) item(key = "clear") { TvVodMatchChip("Clear", Lucide.X, false, scale, null, clear) }
    }
}

@Composable
private fun TvVodMatchChip(label: String, icon: ImageVector?, selected: Boolean, scale: Float,
    requester: FocusRequester?, activate: () -> Unit) {
    TvAction(Modifier.height(56.dp * scale), scale, requester = requester, ring = false, cornerRadius = 28.dp * scale,
        background = if (selected) Palette.text.copy(alpha = 0.22f) else Color(0xFF191919),
        contentPadding = PaddingValues(horizontal = 22.dp * scale), activate = activate) { tint ->
        if (icon != null) { Icon(icon, null, Modifier.size(22.dp * scale), tint); Spacer(Modifier.width(10.dp * scale)) }
        Text(label, color = tint, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TvVodMatchSearchPanel(query: String, queryChange: (String) -> Unit, scale: Float, finish: () -> Unit) {
    Column(Modifier.width(520.dp * scale).background(Color(0xFF191919), RoundedCornerShape(18.dp * scale))
        .padding(horizontal = 24.dp * scale, vertical = 24.dp * scale), verticalArrangement = Arrangement.spacedBy(14.dp * scale)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Search titles", Modifier.weight(1f), fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            TvAction(Modifier.height(56.dp * scale), scale, contentPadding = PaddingValues(horizontal = 22.dp * scale),
                activate = finish) { tint ->
                Text("Done", color = tint, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(query.ifBlank { "Type part of a title" }, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, color = if (query.isBlank()) Palette.secondary else Palette.text)
        TvSearchKeyboard({ transform -> queryChange(transform(query)) }, scale)
    }
}

@Composable
private fun TvVodMatchForm(form: VodMatchForm, saving: Boolean, error: String?, scale: Float,
    change: (String) -> Unit, save: () -> Unit, cancel: () -> Unit) {
    // Backspace edits only while focus is inside the keyboard area; on Save or Cancel it must not delete metadata characters.
    var keyboardActive by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
            Key.Back, Key.Escape -> { if (!saving) cancel(); true }
            Key.Backspace -> { if (!saving && keyboardActive) change(form.metadataId.dropLast(1)); keyboardActive }
            else -> false
        }
    }, verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
        Text(if (form.metadataId.isBlank()) "Match title" else "Edit match", fontSize = (44 * scale).sp,
            lineHeight = (52 * scale).sp, letterSpacing = (-0.88f * scale).sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(form.name, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(vodMatchTypeLabel(form.type), form.providerName).joinToString(" · "),
            fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp, color = Palette.secondary)
        Spacer(Modifier.height(6.dp * scale))
        Text("Metadata ID", fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold,
            color = Palette.body)
        val shape = RoundedCornerShape(14.dp * scale)
        Row(Modifier.width(1060.dp * scale).height(76.dp * scale)
            .background(Color.White.copy(alpha = .07f), shape).border(2.dp * scale, Palette.text, shape)
            .padding(horizontal = 24.dp * scale), verticalAlignment = Alignment.CenterVertically) {
            Text(form.metadataId.ifEmpty { "tt0111161" }, Modifier.widthIn(max = 998.dp * scale),
                fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Medium,
                color = if (form.metadataId.isEmpty()) Palette.secondary else Palette.text, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Box(Modifier.padding(start = 6.dp * scale).size(3.dp * scale, 32.dp * scale).background(Palette.accent))
        }
        Box(Modifier.width(1060.dp * scale).onFocusChanged { keyboardActive = it.hasFocus }) {
            TvEntryKeyboard(scale, suffixes = false, onDelete = { if (!saving) change(form.metadataId.dropLast(1)) }) { value ->
                if (!saving) change(form.metadataId + value)
            }
        }
        Spacer(Modifier.height(6.dp * scale))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
            TvAction(Modifier.width(300.dp * scale).height(72.dp * scale), scale, primary = true,
                enabled = form.metadataId.isNotBlank() && !saving, activate = save) { tint ->
                Text(if (saving) "Saving…" else "Save match", color = tint, fontSize = (26 * scale).sp,
                    lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
            }
            TvAction(Modifier.width(220.dp * scale).height(72.dp * scale), scale, enabled = !saving, activate = cancel) { tint ->
                Text("Cancel", color = tint, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
        error?.let { Text(it, color = Palette.danger, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp) }
    }
}
