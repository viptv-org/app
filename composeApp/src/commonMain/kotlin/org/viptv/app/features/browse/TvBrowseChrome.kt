package org.viptv.app.features.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.X
import org.viptv.app.components.TvAction
import org.viptv.app.features.entry.TvEntryKeyboard
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.components.tvRingReach
import org.viptv.app.components.tvRingRoom
import org.viptv.app.connection.*
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.ControlKind
import org.viptv.engine.wire.QueryControl
import org.viptv.engine.wire.QueryValue

/**
 * The TV browse chrome from `Tv3Movies` / `Tv3MoviesCatalogs` / `Tv3MoviesFilters`:
 * a catalog chip, a Filters chip with the active count, and the applied-filter
 * chips. Facets live on a full-page filter surface instead of inline chips, and
 * "Show results" applies the whole draft in one request.
 */
@Composable
internal fun TvBrowseChrome(controls: DiscoveryControls?, query: BrowseQuery, scale: Float,
    requestFocus: FocusRequester?, onQuery: (BrowseQuery) -> Unit, onClear: () -> Unit) {
    var catalogsOpen by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    val filtersFocus = remember { FocusRequester() }
    val catalog = controls?.selectedCatalog(query)
    val facets = remember(controls, query, catalog) { tvFacets(controls, query, catalog) }
    val applied = remember(facets, query) { tvAppliedChips(facets, query) }
    val catalogLabel = catalog?.let { it.name + (it.connection_name?.takeIf(String::isNotBlank)?.let { name -> " · $name" } ?: "") }
        ?: "Catalog"
    Row(Modifier.fillMaxWidth().height(64.dp * scale), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
        TvAction(Modifier.height(64.dp * scale).widthIn(min = 300.dp * scale).focusProperties { right = filtersFocus },
            scale, requester = requestFocus, cornerRadius = 32.dp * scale, activate = { catalogsOpen = true },
            contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
            Text(catalogLabel, color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(12.dp * scale))
            Icon(Lucide.ChevronDown, null, Modifier.size(24.dp * scale), tint = it)
        }
        TvAction(Modifier.height(64.dp * scale).focusRequester(filtersFocus), scale, cornerRadius = 32.dp * scale, activate = { filtersOpen = true },
            contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
            Icon(Lucide.SlidersHorizontal, null, Modifier.size(24.dp * scale), tint = it)
            Spacer(Modifier.width(12.dp * scale))
            Text("Filters", color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
            if (applied.isNotEmpty()) {
                Spacer(Modifier.width(10.dp * scale))
                Box(Modifier.height(32.dp * scale).widthIn(min = 32.dp * scale)
                    .background(if (it == Palette.background) Palette.text.copy(alpha = .18f) else Palette.text.copy(alpha = .18f), RoundedCornerShape(16.dp * scale))
                    .padding(horizontal = 8.dp * scale), contentAlignment = Alignment.Center) {
                    Text("${applied.size}", color = it, fontSize = (18 * scale).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (applied.isNotEmpty()) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp * scale),
                contentPadding = PaddingValues(end = 96.dp * scale)) {
                items(applied, key = { it }) { chip ->
                    Text(chip, Modifier.background(Color.White.copy(alpha = .10f), RoundedCornerShape(20.dp * scale))
                        .padding(horizontal = 16.dp * scale, vertical = 8.dp * scale), color = Palette.body,
                        fontSize = (20 * scale).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        } else Spacer(Modifier.weight(1f))
    }
    if (catalogsOpen && controls != null) {
        TvCatalogsSheet(controls, query, scale, { catalogsOpen = false }) { selected ->
            catalogsOpen = false
            onQuery(query.forCatalog(selected))
        }
    }
    if (filtersOpen && controls != null) {
        TvFiltersPage(controls, facets, query, catalogLabel, scale, { filtersOpen = false }, onQuery = { draft ->
            filtersOpen = false
            onQuery(draft)
        })
    }
}

/** The facets the board shows: the global set plus the selected catalog's own. */
private fun tvFacets(controls: DiscoveryControls?, query: BrowseQuery, catalog: DiscoveryCatalog?): List<QueryControl> {
    if (controls == null) return emptyList()
    val base = if (catalog == null) controls.controls
    else controls.controls.filter { it.id in listOf("type", "catalog") } + catalog.controls.filter { it.id !in listOf("type", "catalog") }
    return base.filter { it.id != "type" && it.maximum_selections != 0L }
}

private fun tvValue(control: QueryControl, query: BrowseQuery): String = when (control.id) {
    "catalog" -> query.catalog.orEmpty()
    "search" -> query.search.orEmpty()
    else -> query.selections(control.id).joinToString(", ").ifBlank { "" }
}

private fun tvAppliedChips(facets: List<QueryControl>, query: BrowseQuery): List<String> = facets.mapNotNull { control ->
    val values = query.selections(control.id).filter { it.isNotBlank() }
    if (values.isEmpty()) null
    else values.joinToString(", ") { value ->
        control.choices.find { it.value == value }?.label ?: value
    }
}

@Composable
private fun TvCatalogsSheet(controls: DiscoveryControls, query: BrowseQuery, scale: Float,
    dismiss: () -> Unit, select: (DiscoveryCatalog) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(30) { if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect; withFrameNanos { } } }
    Popup(onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f))
                .focusProperties { canFocus = false }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss))
            Column(Modifier.align(Alignment.CenterEnd).width(720.dp * scale).fillMaxHeight()
                .background(Color(0xFF161618)).padding(start = 64.dp * scale, end = 96.dp * scale, top = 72.dp * scale, bottom = 54.dp * scale),
                verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                Text("Catalog", fontSize = (44 * scale).sp, lineHeight = (53 * scale).sp,
                    letterSpacing = (-.88f * scale).sp, fontWeight = FontWeight.Bold)
                Text("Movies from your sources and add-ons", color = Palette.secondary, fontSize = (24 * scale).sp)
                Spacer(Modifier.height(14.dp * scale))
                val peers = controls.catalogs.filter { it.type == query.type }
                val groups = peers.groupBy { it.connection_name?.takeIf(String::isNotBlank) ?: "VIPTV Sources" }
                            LazyColumnLike(Modifier.weight(1f), tvRingReach(scale)) {
                    groups.forEach { (group, entries) ->
                        item(key = "group:$group") {
                            Text(group.uppercase(), color = Palette.tertiary, fontSize = (22 * scale).sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp * scale, bottom = 6.dp * scale))
                        }
                        itemsIndexed(entries, key = { _, entry -> entry.selectionKey() }) { index, entry ->
                            val selected = controls.selectedCatalog(query)?.selectionKey() == entry.selectionKey()
                            var focused by remember { mutableStateOf(false) }
                            Row(Modifier.fillMaxWidth().height(76.dp * scale)
                                .focusRequester(if (group == groups.keys.first() && index == 0) first else remember { FocusRequester() })
                                .onFocusChanged { focused = it.isFocused }
                                .tvFocusOutline(focused, 14.dp * scale, scale)
                                .background(if (focused) Palette.text else if (selected) Color.White.copy(alpha = .1f) else Color.White.copy(alpha = .04f),
                                    RoundedCornerShape(14.dp * scale))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { select(entry) }
                                .padding(horizontal = 26.dp * scale), verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.displayName(peers), Modifier.weight(1f),
                                    color = if (focused) Palette.background else Palette.text,
                                    fontSize = (26 * scale).sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (selected) Icon(Lucide.Check, "Selected", Modifier.size(26.dp * scale),
                                    tint = if (focused) Palette.background else Palette.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** LazyColumn without the lazy-state import noise in this file. */
@Composable
private fun LazyColumnLike(modifier: Modifier, reach: androidx.compose.ui.unit.Dp, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    // Room for the rows' outside ring, which the viewport would clip at the edges.
    androidx.compose.foundation.lazy.LazyColumn(modifier.tvRingRoom(reach, horizontal = false), contentPadding = PaddingValues(vertical = reach),
        verticalArrangement = Arrangement.spacedBy(6.dp * 1f)) { content() }
}

@Composable
private fun TvFiltersPage(controls: DiscoveryControls, facets: List<QueryControl>, query: BrowseQuery,
    catalogLabel: String, scale: Float, dismiss: () -> Unit, onQuery: (BrowseQuery) -> Unit) {
    var draft by remember(query.catalog, query.type) { mutableStateOf(query) }
    var active by remember { mutableStateOf(facets.firstOrNull()?.id) }
    val activeControl = facets.find { it.id == active } ?: facets.firstOrNull()
    val firstFacet = remember { FocusRequester() }
    val firstChoice = remember { FocusRequester() }
    // Claim the first facet once when the page opens; keying this on the active
    // control re-stole focus to the first facet on every selection.
    LaunchedEffect(Unit) {
        repeat(30) {
            if (runCatching { firstFacet.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    Popup(onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background)) {
            Column(Modifier.fillMaxSize().padding(start = 96.dp * scale, end = 96.dp * scale, top = 96.dp * scale, bottom = 54.dp * scale)) {
                Text("Filters", fontSize = (52 * scale).sp, lineHeight = (60 * scale).sp,
                    letterSpacing = (-1.04f * scale).sp, fontWeight = FontWeight.Bold)
                Text(catalogLabel, color = Palette.secondary, fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp)
                Spacer(Modifier.height(28.dp * scale))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(64.dp * scale)) {
                    Column(Modifier.width(640.dp * scale).tvRingRoom(tvRingReach(scale), horizontal = false)
                        .verticalScroll(rememberScrollState()).padding(vertical = tvRingReach(scale)),
                        verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                        facets.forEachIndexed { index, facet ->
                            var focused by remember { mutableStateOf(false) }
                            val value = tvFacetValue(facet, draft)
                            Row(Modifier.fillMaxWidth().height(80.dp * scale)
                                .focusRequester(if (index == 0) firstFacet else remember { FocusRequester() })
                                .onFocusChanged { focused = it.isFocused }
                                .tvFocusOutline(focused, 14.dp * scale, scale)
                                .background(if (focused) Palette.text else if (facet.id == active) Color.White.copy(alpha = .16f) else Color.White.copy(alpha = .04f),
                                    RoundedCornerShape(14.dp * scale))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { active = facet.id }
                                .padding(horizontal = 26.dp * scale), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
                                        Text(facet.label, color = if (focused) Palette.background else Palette.text,
                                            fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                                        if (facet.required) Text("Required", color = if (focused) Color(0xFF4A4945) else Palette.tertiary,
                                            fontSize = (18 * scale).sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Text(value.ifBlank { "Any" }, color = if (focused) Color(0xFF4A4945) else Palette.secondary,
                                    fontSize = (22 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 300.dp * scale))
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        if (activeControl == null) {
                            Text("No filters are available for this catalog.", color = Palette.secondary, fontSize = (24 * scale).sp)
                        } else {
                            Text(activeControl.label, fontSize = (36 * scale).sp, fontWeight = FontWeight.Bold)
                            val selected = draft.selections(activeControl.id)
                            val cap = activeControl.maximum_selections
                            Text(if (cap != null) "${selected.size} of $cap" else if (activeControl.choices.isNotEmpty()) "${selected.size} selected" else "Enter a value",
                                color = Palette.secondary, fontSize = (24 * scale).sp)
                            if (cap != null) Text("Pick up to $cap.", color = Palette.secondary, fontSize = (24 * scale).sp)
                            Spacer(Modifier.height(20.dp * scale))
                            when (activeControl.kind) {
                                ControlKind.MULTICHOICE, ControlKind.SINGLECHOICE -> TvFacetChoices(
                                    activeControl, draft, scale, firstChoice,
                                    single = activeControl.kind == ControlKind.SINGLECHOICE,
                                ) { values ->
                                    draft = draft.withSelections(activeControl.id, values)
                                }
                                ControlKind.TOGGLE -> TvFacetChoices(
                                    activeControl.copy(choices = listOf(
                                        org.viptv.engine.wire.Choice("true", "On"),
                                        org.viptv.engine.wire.Choice("false", "Off"))),
                                    draft, scale, firstChoice, single = true,
                                ) { values -> draft = draft.withSelections(activeControl.id, values) }
                                else -> TvFacetText(activeControl, draft, scale, firstChoice) { value ->
                                    draft = draft.withSelections(activeControl.id, if (value.isBlank()) emptyList() else listOf(value))
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 24.dp * scale), horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    TvAction(Modifier.height(72.dp * scale).width(340.dp * scale), scale, primary = true,
                        activate = { onQuery(draft) }, contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                        Text("Show results", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                    TvAction(Modifier.height(72.dp * scale).width(220.dp * scale), scale,
                        activate = { draft = BrowseQuery(draft.type) }, contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                        Text("Clear all", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                    TvAction(Modifier.height(72.dp * scale).width(220.dp * scale), scale, activate = dismiss,
                        contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                        Text("Cancel", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun tvFacetValue(control: QueryControl, query: BrowseQuery): String =
    query.selections(control.id).joinToString(", ") { value ->
        control.choices.find { it.value == value }?.label ?: value
    }

@Composable
private fun TvFacetChoices(control: QueryControl, draft: BrowseQuery, scale: Float, first: FocusRequester,
    single: Boolean, change: (List<String>) -> Unit) {
    val selected = draft.selections(control.id)
    val cap = control.maximum_selections ?: Int.MAX_VALUE.toLong()
    Column(Modifier.fillMaxWidth().tvRingRoom(tvRingReach(scale), horizontal = false)
        .verticalScroll(rememberScrollState()).padding(vertical = tvRingReach(scale)),
        verticalArrangement = Arrangement.spacedBy(14.dp * scale)) {
        control.choices.chunked(3).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
                row.forEachIndexed { index, choice ->
                    val checked = choice.value in selected
                    val enabled = checked || selected.size < cap
                    var focused by remember { mutableStateOf(false) }
                    val shape = RoundedCornerShape(32.dp * scale)
                    Row(Modifier.height(64.dp * scale).widthIn(min = 200.dp * scale)
                        .focusRequester(if (rowIndex == 0 && index == 0) first else remember { FocusRequester() })
                        .onFocusChanged { focused = it.isFocused }
                        .tvFocusOutline(focused, 32.dp * scale, scale)
                        .background(if (focused) Palette.text else if (checked) Palette.text else Color.White.copy(alpha = if (enabled) .08f else .04f), shape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled) {
                            change(if (single) listOf(choice.value) else if (checked) selected - choice.value else selected + choice.value)
                        }
                        .padding(horizontal = 24.dp * scale), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                        Text(choice.label, color = if (focused || checked) Palette.background else Palette.text,
                            fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (checked) Icon(Lucide.Check, "Selected", Modifier.size(22.dp * scale), tint = Palette.background)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvFacetText(control: QueryControl, draft: BrowseQuery, scale: Float, first: FocusRequester, apply: (String) -> Unit) {
    var value by remember(control.id, draft) { mutableStateOf(draft.selections(control.id).firstOrNull().orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
        Row(Modifier.fillMaxWidth(0.8f).height(84.dp * scale)
            .focusRequester(first)
            .background(Color.White.copy(alpha = .08f), RoundedCornerShape(14.dp * scale))
            .padding(horizontal = 28.dp * scale), verticalAlignment = Alignment.CenterVertically) {
            Text(value.ifBlank { control.label }, color = if (value.isBlank()) Palette.secondary else Palette.text,
                fontSize = (30 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TvEntryKeyboard(scale) { typed ->
            value = (value + typed).take(64)
            apply(value)
        }
        if (value.isNotEmpty()) TvAction(Modifier.height(64.dp * scale).width(200.dp * scale), scale, activate = {
            value = ""; apply("")
        }) { Text("Clear", color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold) }
    }
}
