package org.viptv.app.features.browse

import org.viptv.app.components.FocusLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.viptv.app.components.AutomaticPage
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryCatalog
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.cardLookupKey
import org.viptv.app.connection.displayName
import org.viptv.app.connection.selectionKey
import org.viptv.app.connection.selections
import org.viptv.app.connection.selectedCatalog
import org.viptv.app.features.home.MediaCard
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.state.applyBrowse
import org.viptv.app.state.browseFilter
import org.viptv.app.state.browseMore
import org.viptv.app.state.browseSelections
import org.viptv.app.state.open
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.Choice
import org.viptv.engine.wire.ControlKind
import org.viptv.engine.wire.QueryControl
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.DropdownMenuItem
import androidx.compose.foundation.shape.CircleShape
import org.viptv.app.components.HoverFill
import org.viptv.app.components.pointerHovered
import androidx.compose.foundation.interaction.MutableInteractionSource

/**
 * W3Browse entry point: the browser gets the board's rail-and-content page
 * instead of the shared inline chip row. The card selection mirrors the shared
 * browse screen (browse cards, de-duplicated, filtered to the page's kind).
 */
@Composable
internal fun BrowserBrowseRoute(state: ApplicationState, controller: ApplicationController) {
    val cards = remember(state.browseCards, state.screen) {
        state.browseCards.distinctBy(::cardLookupKey).filter { card ->
            when (state.screen) {
                Screen.Movies -> card.identity.kind == "movie"
                Screen.Series -> card.identity.kind == "series"
                Screen.Anime -> card.category == "anime" || card.identity.kind == "anime"
                else -> true
            }
        }
    }
    BrowserBrowseScreen(state.screen, cards, state.browseControls, state.browseQuery, state.busy, state.error,
        more = if (state.browseNext != null) controller::browseMore else null,
        open = controller::open, changed = controller::browseFilter,
        multipleChanged = controller::browseSelections, applyQuery = controller::applyBrowse)
}

/**
 * W3Browse / W3BrowseEmpty / W3BrowseError: a persistent 268dp filter rail, an
 * applied-filter pill row and a fixed grid in a scrolling content column. Every
 * rail group is a [QueryControl]; nothing is hard-coded here, so a provider that
 * drops or adds a facet just changes the rail.
 */
@Composable
internal fun BrowserBrowseScreen(screen: Screen, cards: List<Card>, controls: DiscoveryControls?, query: BrowseQuery,
    busy: Boolean, error: String?, more: (() -> Unit)?, open: (Card) -> Unit,
    changed: (String, String) -> Unit, multipleChanged: (String, List<String>) -> Unit,
    applyQuery: (BrowseQuery) -> Unit) {
    val grid = rememberLazyGridState()
    AutomaticPage(cards.size, busy, more) { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    val facets = remember(controls, query) { browserFacets(controls, query) }
    val applied = remember(facets, query) { browserApplied(facets, query) }
    val clearAll = { applyQuery(BrowseQuery(query.type, catalog = query.catalog, connection_id = query.connection_id)) }
    var catalogMenu by remember { mutableStateOf(false) }
    val catalog = controls?.selectedCatalog(query)
    val peers = remember(controls, query.type) { controls?.catalogs?.filter { it.type == query.type }.orEmpty() }
    Row(Modifier.fillMaxSize()) {
        BrowserFilterRail(facets, query, changed, multipleChanged, clearAll)
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = .08f)))
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 32.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(screen.name, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-.52).sp, fontWeight = FontWeight.Bold)
                if (peers.isNotEmpty()) Box {
                    Row(Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha = .12f))
                        .pointerClickable (shape = RoundedCornerShape(17.dp)) { catalogMenu = true }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(catalog?.displayName(peers) ?: "Catalog", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        catalog?.connection_name?.takeIf(String::isNotBlank)?.let {
                            Text("· $it", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Palette.secondary)
                        }
                        Icon(Lucide.ChevronDown, "Choose another catalog", Modifier.size(14.dp), tint = Palette.secondary)
                    }
                    DropdownMenu(catalogMenu, { catalogMenu = false }) {
                        peers.forEach { peer ->
                            DropdownMenuItem(text = { Text(peer.displayName(peers)) }, onClick = {
                                catalogMenu = false
                                changed("catalog", peer.selectionKey())
                            })
                        }
                    }
                }
            }
            BrowserAppliedRow(applied, clearAll, changed, multipleChanged, query)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
            else if (error != null) BrowserBrowseFailure(error, catalog) { catalogMenu = true }
            else if (cards.isEmpty()) BrowserBrowseEmpty(screen, catalog, applied.size, applied.lastOrNull(), query, clearAll, changed, multipleChanged) {
                catalogMenu = true
            }
            LazyVerticalGrid(GridCells.Adaptive(172.dp), state = grid,
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 24.dp)) {
                items(cards, key = ::cardLookupKey) {
                    MediaCard(it, width = 172, portrait = true, showLabels = true, open = open, showFocusOutline = false)
                }
            }
        }
    }
}

/** Facets for the rail: the page type and the catalog are rendered outside it on this board. */
private fun browserFacets(controls: DiscoveryControls?, query: BrowseQuery): List<QueryControl> {
    if (controls == null) return emptyList()
    val catalog = controls.selectedCatalog(query)
    val base = if (catalog == null) controls.controls
    else controls.controls.filter { it.id in listOf("type", "catalog") } + catalog.controls.filter { it.id !in listOf("type", "catalog") }
    return base.filter { it.id !in listOf("type", "catalog") && it.maximum_selections != 0L }
}

internal data class BrowserAppliedChip(val id: String, val value: String, val label: String, val multiple: Boolean)

private fun browserApplied(facets: List<QueryControl>, query: BrowseQuery): List<BrowserAppliedChip> = facets.flatMap { control ->
    query.selections(control.id).filter(String::isNotBlank).map { value ->
        BrowserAppliedChip(control.id, value, control.choices.find { it.value == value }?.label ?: value,
            control.kind != ControlKind.SINGLECHOICE)
    }
}

@Composable
private fun BrowserFilterRail(facets: List<QueryControl>, query: BrowseQuery, changed: (String, String) -> Unit,
    multipleChanged: (String, List<String>) -> Unit, clearAll: () -> Unit) {
    Column(Modifier.width(268.dp).fillMaxHeight().padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Filters", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Clear all", Modifier.pointerClickable(onClick = clearAll), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.body)
        }
        org.viptv.app.components.TvFocusScroll(horizontal = false) { Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (facets.isEmpty()) Text("No browsing filters", fontSize = 13.sp, color = Palette.secondary)
            facets.forEach { control -> BrowserFacet(control, query, changed, multipleChanged) }
        } }
    }
}

@Composable
private fun BrowserFacet(control: QueryControl, query: BrowseQuery, changed: (String, String) -> Unit,
    multipleChanged: (String, List<String>) -> Unit) {
    val selected = query.selections(control.id).filter(String::isNotBlank)
    val cap = control.maximum_selections
    var expanded by remember(control.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            Text(control.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            when {
                control.required -> Text("Required", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Palette.tertiary)
                cap != null && selected.isNotEmpty() -> Text("${selected.size} of $cap", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Palette.secondary)
            }
        }
        when (control.kind) {
            ControlKind.SINGLECHOICE -> BrowserSegments(control, selected.firstOrNull().orEmpty()) { changed(control.id, it) }
            ControlKind.MULTICHOICE -> {
                val choices = if (expanded) control.choices else control.choices.take(16)
                BrowserWrap(horizontalGap = 6.dp, verticalGap = 6.dp) {
                    choices.forEach { choice ->
                        val chosen = choice.value in selected
                        BrowserChip(choice.label, chosen, chosen || cap == null || selected.size < cap) {
                            multipleChanged(control.id, if (chosen) selected - choice.value else selected + choice.value)
                        }
                    }
                    if (!expanded && control.choices.size > 16) BrowserChip("+${control.choices.size - 16} more", false, true) { expanded = true }
                }
            }
            ControlKind.TOGGLE -> BrowserToggle(control.label, selected.lastOrNull() == "true") { changed(control.id, it.toString()) }
            else -> BrowserFacetField(control, selected.firstOrNull().orEmpty()) { changed(control.id, it) }
        }
    }
}

/** The board renders a required single choice (Sort) and Rating as a segmented track. */
@Composable
private fun BrowserSegments(control: QueryControl, selected: String, select: (String) -> Unit) {
    val choices = buildList {
        if (!control.required && control.choices.none { it.value.isEmpty() || it.label.equals("Any", true) }) add(Choice("", "Any"))
        addAll(control.choices)
    }
    Row(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .12f)).padding(4.dp)) {
        choices.forEach { choice ->
            val active = choice.value == selected
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                .then(if (active) Modifier.background(Palette.background).border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(12.dp)) else Modifier)
                .pointerClickable (shape = RoundedCornerShape(12.dp)) { select(choice.value) }, contentAlignment = Alignment.Center) {
                Text(choice.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (active) Palette.text else Palette.secondary)
            }
        }
    }
}

@Composable
private fun BrowserChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.height(28.dp).clip(RoundedCornerShape(14.dp))
        .background(if (selected) Palette.text else Color.White.copy(alpha = if (enabled) .08f else .04f))
        .then(if (selected) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(14.dp)))
        .pointerClickable(enabled = enabled, onClick = onClick, shape = RoundedCornerShape(14.dp)).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (selected) Icon(Lucide.Check, "Selected", Modifier.size(14.dp), tint = Palette.background)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = if (selected) Palette.background else if (enabled) Palette.body else Palette.tertiary)
    }
}

@Composable
private fun BrowserToggle(label: String, on: Boolean, toggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().pointerClickable { toggle(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.width(38.dp).height(22.dp).clip(RoundedCornerShape(11.dp))
            .background(if (on) Palette.accent else Color.White.copy(alpha = .16f)),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(horizontal = 2.dp).size(18.dp).clip(RoundedCornerShape(9.dp))
                .background(if (on) Color(0xFF15130F) else Palette.body))
        }
    }
}

/** Board fields are 34dp pills, not Material text fields. */
@Composable
private fun BrowserFacetField(control: QueryControl, value: String, apply: (String) -> Unit) {
    var draft by remember(control.id, value) { mutableStateOf(value) }
    val commit = { apply(draft.trim()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = .06f))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp)) {
            BasicTextField(draft, { draft = it }, Modifier.fillMaxSize().wrapContentHeight(Alignment.CenterVertically),
                textStyle = TextStyle(color = Palette.text, fontSize = 13.sp), singleLine = true,
                cursorBrush = SolidColor(Palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                decorationBox = { inner ->
                    if (draft.isEmpty()) Text(control.label, fontSize = 13.sp, color = Palette.secondary)
                    inner()
                })
        }
        if (draft.trim() != value) Text("Apply", Modifier.pointerClickable { commit() }, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, color = Palette.body)
        control.maximum?.let { Text("max ${it.toLong()}", fontSize = 11.sp, color = Palette.tertiary) }
    }
}

@Composable
private fun BrowserAppliedRow(applied: List<BrowserAppliedChip>, clearAll: () -> Unit,
    changed: (String, String) -> Unit, multipleChanged: (String, List<String>) -> Unit, query: BrowseQuery) {
    if (applied.isEmpty()) return
    BrowserWrap(horizontalGap = 6.dp, verticalGap = 6.dp) {
        applied.forEach { chip ->
            Row(Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .1f))
                .padding(start = 11.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(chip.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Icon(Lucide.X, "Remove ${chip.label}", Modifier.size(14.dp).pointerClickable(shape = CircleShape) {
                    if (chip.multiple) multipleChanged(chip.id, query.selections(chip.id) - chip.value) else changed(chip.id, "")
                })
            }
        }
        Text("Clear all", Modifier.pointerClickable(onClick = clearAll).padding(horizontal = 6.dp), fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, color = Palette.secondary)
    }
}

@Composable
private fun BrowserBrowseEmpty(screen: Screen, catalog: DiscoveryCatalog?, count: Int, newest: BrowserAppliedChip?,
    query: BrowseQuery, clearAll: () -> Unit, changed: (String, String) -> Unit,
    multipleChanged: (String, List<String>) -> Unit, openCatalog: () -> Unit) {
    val noun = when (screen) {
        Screen.Movies -> "movies"
        Screen.Series -> "series"
        Screen.Anime -> "anime"
        else -> "titles"
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Nothing matches these filters", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(catalog?.let { "${it.name} from ${it.connection_name ?: "your sources"} has no $noun with all $count filters. Removing the newest one usually helps." }
            ?: "No $noun are available for these filters.", fontSize = 14.sp, color = Palette.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (newest != null) BrowserStateAction(if (newest.label.length > 24) "Remove filter" else "Remove “${newest.label}”", primary = true) {
                if (newest.multiple) multipleChanged(newest.id, query.selections(newest.id) - newest.value) else changed(newest.id, "")
            }
            BrowserStateAction("Clear all filters", primary = false, onClick = clearAll)
            if (catalog != null) BrowserStateAction("Choose another catalog", primary = false, onClick = openCatalog)
        }
    }
}

@Composable
private fun BrowserBrowseFailure(error: String, catalog: DiscoveryCatalog?, openCatalog: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(catalog?.let { "Couldn’t load ${it.name}" } ?: "Couldn’t load this page", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${catalog?.connection_name ?: "The source"} didn’t answer. Your filters are kept.", fontSize = 14.sp, color = Palette.secondary)
        Text(error, fontSize = 13.sp, color = Palette.danger)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (catalog != null) BrowserStateAction("Choose another catalog", primary = true, onClick = openCatalog)
        }
    }
}

@Composable
private fun BrowserStateAction(label: String, primary: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val rest = if (primary) Palette.text else Color.Transparent
    Row(Modifier.height(38.dp).clip(RoundedCornerShape(12.dp))
        .background(if (interaction.pointerHovered()) HoverFill.container(rest) else rest)
        .then(if (primary) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(12.dp)))
        .pointerClickable(interactionSource = interaction, onClick = onClick, shape = RoundedCornerShape(12.dp), hover = null).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (primary) Palette.background else Palette.text)
    }
}

/**
 * Wrapping chip row. The board's rails wrap on width, and a plain `Row` would
 * clip; this measures children and breaks lines itself, so nothing here depends
 * on an experimental layout API.
 */
@Composable
private fun BrowserWrap(horizontalGap: Dp, verticalGap: Dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val gapX = horizontalGap.roundToPx()
        val gapY = verticalGap.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        var x = 0
        var y = 0
        var rowHeight = 0
        val offsets = ArrayList<IntArray>(placeables.size)
        placeables.forEach { placeable ->
            if (x > 0 && x + placeable.width > constraints.maxWidth) {
                x = 0
                y += rowHeight + gapY
                rowHeight = 0
            }
            offsets += intArrayOf(x, y)
            x += placeable.width + gapX
            rowHeight = maxOf(rowHeight, placeable.height)
        }
        val height = placeables.indices.maxOfOrNull { offsets[it][1] + placeables[it].height } ?: 0
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable -> placeable.placeRelative(offsets[index][0], offsets[index][1]) }
        }
    }
}
