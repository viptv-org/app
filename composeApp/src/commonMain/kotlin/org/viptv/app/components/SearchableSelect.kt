package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import org.viptv.app.theme.Palette

/**
 * One choice of a [SearchableSelect]: a stable [id], the visible [label], optional
 * [trailing] text such as a count ("5"), and whether it can be picked.
 */
@Immutable
internal data class SelectOption(val id: String, val label: String, val trailing: String? = null, val enabled: Boolean = true)

/** More options than this never fit a segmented control or a chip row. */
internal const val InlineChoiceLimit = 5

/** Segments share the width equally, so `count × longest label` (in characters) bounds what still reads. */
internal const val InlineChoiceBudget = 30

/**
 * The single "segments or chips vs. dropdown" rule (app#57), shared by every caller
 * so it lives in one place: more than [limit] options ([InlineChoiceLimit] by default),
 * or labels too long to share a 220 dp track ([budget] characters across equal
 * segments), use a [SearchableSelect]. Pass the options exactly as the inline control
 * would show them, including an added "Any". A caller whose inline control is a
 * scrolling chip row rather than equal segments passes its own [limit] and no
 * [budget] (the source picker, app#59).
 */
internal fun useSelect(options: List<SelectOption>, limit: Int = InlineChoiceLimit, budget: Int? = InlineChoiceBudget): Boolean {
    val longest = options.maxOfOrNull { it.label.length + (it.trailing?.let { t -> t.length + 1 } ?: 0) } ?: 0
    return options.size > limit || budget != null && options.size * longest > budget
}

/**
 * The dropdown chip's text (components.md section 2): "Genre: Any", "Genre: Comedy",
 * "Genre: Comedy, Drama", then "Genre: 3 selected". An id the options no longer list
 * shows as itself so a stale value is never hidden.
 */
internal fun selectTriggerLabel(title: String, options: List<SelectOption>, selected: List<String>, placeholder: String): String =
    "$title: ${selectTriggerValue(options, selected, placeholder)}"

private fun selectTriggerValue(options: List<SelectOption>, selected: List<String>, placeholder: String): String {
    val labels = selected.map { id -> options.firstOrNull { it.id == id }?.label ?: id }
    return when {
        labels.isEmpty() -> placeholder
        labels.size == 1 -> labels.single()
        labels.size == 2 && labels.sumOf { it.length } <= 22 -> labels.joinToString(", ")
        else -> "${labels.size} selected"
    }
}

/** Options whose label contains [query], ignoring case and accents ("cafe" finds "Café"). */
internal fun filterOptions(options: List<SelectOption>, query: String): List<SelectOption> {
    val needle = foldForSearch(query.trim())
    return options.filter { foldForSearch(it.label).contains(needle) }
}

private val accentFolds: Map<Char, String> = buildMap {
    listOf("aàáâãäåāăą", "cçćĉċč", "dďđ", "eèéêëēĕėęě", "gĝğġģ", "hĥħ", "iìíîïĩīĭįı", "jĵ", "kķ", "lĺļľŀł",
        "nñńņňŉ", "oòóôõöøōŏő", "rŕŗř", "sśŝşšș", "tţťŧț", "uùúûüũūŭůűų", "wŵ", "yýÿŷ", "zźżž")
        .forEach { group -> group.drop(1).forEach { put(it, group.take(1)) } }
    put('ß', "ss"); put('æ', "ae"); put('œ', "oe"); put('þ', "th")
}

/** Lower-cases and strips Latin accents and combining marks; commonMain has no `Normalizer`. */
internal fun foldForSearch(text: String): String = buildString(text.length) {
    text.lowercase().forEach { char ->
        if (char in '̀'..'ͯ') return@forEach
        append(accentFolds[char] ?: char)
    }
}

/** Design `surface-3`: the selected chip fill. */
private val Surface3 = Color(0xFF2A2A2E)
private val RowShape = RoundedCornerShape(10.dp)
private val RowHeight = 40.dp
private val ListMaxHeight = 320.dp

/**
 * The shared searchable, virtualised dropdown (app#57). A dropdown chip
 * ("[title]: [value] ⌄") opens the app's [PopoverMenu] with a search field, which
 * takes focus and filters as you type, over a [LazyColumn] of choices in the
 * design's choice-list look: a check and "Current" on the current value, and
 * "unavailable" rows that cannot be picked.
 *
 * Single mode picks one id and closes; [multiple] mode ticks checkboxes, stays
 * open and respects [maximumSelections]. [anyLabel] adds a first row that clears
 * the value; pass `null` when the control is required. Arrow keys move, Enter
 * picks, Escape and a click outside close. Decide whether to use it with [useSelect].
 * It is built for pointer and keyboard screens (desktop, web): the search field
 * takes focus on open, so TV keeps its own pickers. Options with a repeated id
 * are shown once.
 */
@Composable
internal fun SearchableSelect(
    title: String,
    options: List<SelectOption>,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    multiple: Boolean = false,
    maximumSelections: Int? = null,
    anyLabel: String? = "Any",
    placeholder: String = anyLabel ?: "Choose",
    searchPlaceholder: String = "Search ${title.lowercase()}",
) {
    var open by remember { mutableStateOf(false) }
    val distinct = remember(options) { options.distinctBy { it.id } }
    Box(modifier) {
        SelectTrigger(title, selectTriggerValue(distinct, selected, placeholder),
            set = selected.isNotEmpty(), Modifier.fillMaxWidth()) { open = true }
        PopoverMenu(open, { open = false }) {
            SelectPanel(distinct, selected, multiple, maximumSelections, anyLabel, searchPlaceholder, onChange) { open = false }
        }
    }
}

/** The design's dropdown chip, D size: 40 tall; set = surface-3 with a 0.22 border and primary text. */
@Composable
private fun SelectTrigger(title: String, value: String, set: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(modifier.height(40.dp).clip(shape).background(if (set) Surface3 else Color.Transparent)
        .border(1.dp, Color.White.copy(alpha = if (set) .22f else .10f), shape)
        .pointerClickable(role = Role.DropdownList, shape = shape, onClick = onClick).padding(start = 14.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = Palette.secondary)) { append("$title: ") }
            withStyle(SpanStyle(color = if (set) Palette.text else Palette.secondary)) { append(value) }
        }, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Lucide.ChevronDown, null, Modifier.size(14.dp), tint = Palette.secondary)
    }
}

@Composable
private fun SelectPanel(options: List<SelectOption>, selected: List<String>, multiple: Boolean, cap: Int?,
    anyLabel: String?, searchPlaceholder: String, onChange: (List<String>) -> Unit, close: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val folded = remember(options) { options.map { foldForSearch(it.label) } }
    // A null row is the "Any" row; it is only offered before a search narrows the list.
    val rows: List<SelectOption?> = remember(options, folded, query, anyLabel) {
        val needle = foldForSearch(query.trim())
        buildList {
            if (anyLabel != null && needle.isEmpty()) add(null)
            options.forEachIndexed { index, option -> if (folded[index].contains(needle)) add(option) }
        }
    }
    var active by remember { mutableIntStateOf(rows.indexOfFirst { it != null && it.id in selected }.coerceAtLeast(0)) }
    // Bumped by the arrow keys only: hovering a row moves the highlight but never scrolls the list under the mouse.
    var keyboardMoves by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (active - 3).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val search = remember { FocusRequester() }
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }

    fun enabled(row: SelectOption?) = row == null ||
        row.enabled && (!multiple || cap == null || row.id in selected || selected.size < cap)
    fun pick(row: SelectOption?) {
        if (!enabled(row)) return
        if (!multiple) { onChange(listOfNotNull(row?.id)); close(); return }
        onChange(when {
            row == null -> emptyList()
            row.id in selected -> selected - row.id
            else -> selected + row.id
        })
    }
    /** The next row in [step]'s direction that can be picked; unavailable and over-cap rows are skipped. */
    fun move(step: Int) {
        var index = active + step
        while (index in rows.indices && !enabled(rows[index])) index += step
        if (index in rows.indices) { active = index; keyboardMoves++ }
    }

    LaunchedEffect(Unit) { search.requestFocus() }
    // Keep the keyboard row in view: scroll by the overflow, or jump when it is off screen.
    LaunchedEffect(keyboardMoves) {
        val info = list.layoutInfo
        if (keyboardMoves == 0 || active !in rows.indices || info.visibleItemsInfo.isEmpty()) return@LaunchedEffect
        val item = info.visibleItemsInfo.firstOrNull { it.index == active }
        when {
            item == null && active < list.firstVisibleItemIndex -> list.scrollToItem(active)
            item == null -> { list.scrollToItem(active); list.scrollBy(-(info.viewportEndOffset - rowPx)) }
            item.offset < info.viewportStartOffset -> list.scrollBy((item.offset - info.viewportStartOffset).toFloat())
            item.offset + item.size > info.viewportEndOffset -> list.scrollBy((item.offset + item.size - info.viewportEndOffset).toFloat())
        }
    }

    Column(Modifier.width(PopoverMenuWidth).onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent event.key in listOf(Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.NumPadEnter)
        when (event.key) {
            Key.DirectionDown -> { move(1); true }
            Key.DirectionUp -> { move(-1); true }
            Key.Enter, Key.NumPadEnter -> { if (active in rows.indices) pick(rows[active]); true }
            Key.Escape -> { close(); true }
            else -> false
        }
    }) {
        SelectSearchField(query, searchPlaceholder, Modifier.focusRequester(search)) {
            query = it; active = 0
            scope.launch { list.scrollToItem(0) }
        }
        if (rows.isEmpty()) Text("No matches for “${query.trim()}”", Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 13.sp, color = Palette.secondary)
        // A fixed width: the popover measures its column's intrinsic width, which a lazy list cannot answer.
        LazyColumn(Modifier.width(PopoverMenuWidth).heightIn(max = ListMaxHeight), state = list,
            contentPadding = PaddingValues(top = 4.dp)) {
            itemsIndexed(rows) { index, row ->
                val checked = if (row == null) selected.isEmpty() else row.id in selected
                SelectRow(row?.label ?: anyLabel.orEmpty(), unavailable = row != null && !row.enabled,
                    trailing = listOfNotNull(row?.trailing, "Current".takeIf { !multiple && checked }).joinToString(" · ").ifEmpty { null },
                    checked, multiple, enabled(row), active = index == active, hovered = { active = index }) { pick(row) }
            }
        }
    }
}

/**
 * The design's D text field (radius 12, hairline border, focus raises it to 1.5 px at 0.22,
 * accent caret), 40 tall to fit the 260 wide popover. It is filled with the page background
 * because a surface-1 field would vanish into the surface-1 popover.
 */
@Composable
private fun SelectSearchField(query: String, placeholder: String, modifier: Modifier, change: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp).fillMaxWidth().height(40.dp).clip(shape)
        .background(Palette.background).border(if (focused) 1.5.dp else 1.dp, if (focused) Color.White.copy(alpha = .22f) else Hairline, shape)
        .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Lucide.Search, null, Modifier.size(15.dp), tint = Palette.tertiary)
        BasicTextField(query, change, modifier.weight(1f), singleLine = true, interactionSource = interaction,
            textStyle = TextStyle(color = Palette.text, fontSize = 14.sp), cursorBrush = SolidColor(Palette.accent),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(placeholder, fontSize = 14.sp, color = Palette.tertiary, maxLines = 1)
                    inner()
                }
            })
        if (query.isNotEmpty()) Icon(Lucide.X, "Clear search", Modifier.size(16.dp)
            .pointerClickable(shape = CircleShape) { change("") }, tint = Palette.secondary)
    }
}

/**
 * A choice row, 40 tall: a check (single) or checkbox (multiple), the label (with
 * "· unavailable" when it cannot be picked) and tertiary trailing text. Mouse hover and the keyboard share one highlight, the
 * popover row's surface-2 fill.
 */
@Composable
private fun SelectRow(label: String, unavailable: Boolean, trailing: String?, checked: Boolean, multiple: Boolean, enabled: Boolean,
    active: Boolean, hovered: () -> Unit, pick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pointer = interaction.pointerHovered()
    LaunchedEffect(pointer) { if (pointer) hovered() }
    val click = if (multiple) Modifier.pointerToggleable(checked, interaction, null, enabled, Role.Checkbox, RowShape, hover = null) { pick() }
    else Modifier.pointerSelectable(checked, interaction, null, enabled, Role.RadioButton, RowShape, hover = null, onClick = pick)
    Row(Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(RowHeight).clip(RowShape)
        .background(if (active && enabled) Palette.raised else Color.Transparent).then(click).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (multiple) Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
                .background(if (checked) Palette.accent else Color.Transparent)
                .then(if (checked) Modifier else Modifier.border(1.5.dp, if (enabled) Palette.secondary else Palette.tertiary, RoundedCornerShape(4.dp))),
                contentAlignment = Alignment.Center) {
                if (checked) Icon(Lucide.Check, null, Modifier.size(12.dp), tint = Palette.background)
            } else if (checked) Icon(Lucide.Check, null, Modifier.size(16.dp), tint = Palette.accent)
        }
        Text(buildAnnotatedString {
            append(label)
            if (unavailable) withStyle(SpanStyle(color = Palette.tertiary, fontWeight = FontWeight.Normal)) { append(" · unavailable") }
        }, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (enabled) Palette.text else Palette.tertiary)
        trailing?.let { Text(it, fontSize = 12.sp, color = Palette.tertiary, maxLines = 1) }
    }
}
