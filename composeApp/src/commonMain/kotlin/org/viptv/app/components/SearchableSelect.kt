package org.viptv.app.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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

/**
 * How a filter chip opens its choices (components.md section 8): the anchored popover on
 * desktop, web and tablet, the bottom sheet on a phone. The content and behaviour are the
 * same; only the container changes. [FilterBar] picks it from the layout.
 */
internal enum class SelectPresentation { Popover, Sheet }

/** The presentation for every chip below; [FilterBar] provides it. */
internal val LocalSelectPresentation = staticCompositionLocalOf { SelectPresentation.Popover }

/**
 * The popover shows this many rows before its list scrolls. A longer list ("Any" included)
 * gets the search field; a short one (Sort, Type) is just its rows (app#117).
 */
internal const val SearchRowLimit = 8

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

/** Design `surface-3`: the set chip fill. */
private val Surface3 = Color(0xFF2A2A2E)
private val RowShape = RoundedCornerShape(10.dp)
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
/** A chip never grows past this; a long value ellipsizes. */
private val ChipMaxWidth = 280.dp

/** Row and list sizes of each presentation: 40 dp popover rows, 48 dp touch rows in the sheet. */
private class SelectMetrics(val rowHeight: Dp, val listMaxHeight: Dp, val sheet: Boolean) {
    val width: Modifier get() = if (sheet) Modifier.fillMaxWidth() else Modifier.width(PopoverMenuWidth)
}
private val PopoverMetrics = SelectMetrics(40.dp, 320.dp, sheet = false)

/**
 * The single filter control (app#57, app#117): a dropdown chip ("[title]: [value] ⌄") that
 * opens the shared choice list, in the app's [PopoverMenu] or, on a phone, a bottom sheet
 * ([LocalSelectPresentation]). Lists longer than [SearchRowLimit] rows get a search field
 * that filters as you type; the rows use the design's choice-list look: a check and
 * "Current" on the current value, and "unavailable" rows that cannot be picked.
 *
 * Picking applies at once. Single mode picks one id and closes; [multiple] mode ticks
 * checkboxes, stays open and respects [maximumSelections]. [anyLabel] adds a first row that
 * clears the value; pass `null` when the control always has a value. Arrow keys (and the
 * D-pad) move, Enter or OK picks, Escape, Back and a tap outside close. On pointer layouts
 * the search field takes focus on open; touch layouts never raise the keyboard by themselves.
 * Options with a repeated id are shown once.
 *
 * The chip is filled (surface-3 with a 0.22 border) while [highlighted]: by default while an
 * optional control has a value. [open] lets a caller open the list itself ("Choose another
 * catalog").
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
    /** Trailing text on the [anyLabel] row, such as the total count. */
    anyTrailing: String? = null,
    placeholder: String = anyLabel ?: "Choose",
    searchPlaceholder: String = "Search ${title.lowercase()}",
    highlighted: Boolean = anyLabel != null && selected.isNotEmpty(),
    open: MutableState<Boolean> = remember { mutableStateOf(false) },
) {
    val distinct = remember(options) { options.distinctBy { it.id } }
    SelectChip(title, selectTriggerValue(distinct, selected, placeholder), selected.isNotEmpty(), highlighted, modifier, open) { metrics, close ->
        SelectPanel(distinct, selected, multiple, maximumSelections, anyLabel, anyTrailing, searchPlaceholder, metrics, onChange, close)
    }
}

/**
 * A free-text or number filter as the same dropdown chip: the panel holds one field, and
 * Enter (Done on a touch keyboard) applies the value and closes. There is no Apply button.
 * When a value is set an "Any" row clears it.
 */
@Composable
internal fun TextSelect(
    title: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Any",
    number: Boolean = false,
    highlighted: Boolean = value.isNotBlank(),
) {
    val open = remember { mutableStateOf(false) }
    SelectChip(title, value.ifBlank { placeholder }, value.isNotBlank(), highlighted, modifier, open) { metrics, close ->
        TextPanel(title, value, number, metrics, onChange, close)
    }
}

/** An on/off filter is a chip that toggles: the same chip, filled with a check while on. */
@Composable
internal fun ToggleSelect(title: String, on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val sheet = LocalSelectPresentation.current == SelectPresentation.Sheet
    val shape = RoundedCornerShape(50)
    Row(modifier.chipFrame(on, sheet, shape).pointerToggleable(on, role = Role.Switch, shape = shape, onValueChange = onChange)
        .padding(start = if (on) 12.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (on) Icon(Lucide.Check, null, Modifier.size(14.dp), tint = Palette.text)
        Text(title, fontSize = chipText(sheet), fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = if (on) Palette.text else Palette.secondary)
    }
}

/**
 * A plain chip in the filter bar's geometry, for an action such as "Clear". It has the
 * unset chip's frame without the border, so it reads as an action rather than a filter.
 */
@Composable
internal fun ActionChip(label: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sheet = LocalSelectPresentation.current == SelectPresentation.Sheet
    val shape = RoundedCornerShape(50)
    Row(modifier.height(chipHeight(sheet)).clip(shape).pointerClickable(role = Role.Button, shape = shape, onClick = onClick)
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon?.let { Icon(it, null, Modifier.size(14.dp), tint = Palette.secondary) }
        Text(label, fontSize = chipText(sheet), fontWeight = FontWeight.SemiBold, color = Palette.secondary, maxLines = 1)
    }
}

/** Design filter chip heights: P 44, D 40 (components.md section 2). */
private fun chipHeight(sheet: Boolean) = if (sheet) 44.dp else 40.dp
private fun chipText(sheet: Boolean) = if (sheet) 14.sp else 13.sp

/** Unset: transparent with a 0.10 border. Set: surface-3 with a 0.22 border. */
private fun Modifier.chipFrame(set: Boolean, sheet: Boolean, shape: RoundedCornerShape) =
    height(chipHeight(sheet)).clip(shape).background(if (set) Surface3 else Color.Transparent)
        .border(1.dp, Color.White.copy(alpha = if (set) .22f else .10f), shape)

/** The chip plus its popover or sheet; [panel] gets the presentation's sizes and a close callback. */
@Composable
private fun SelectChip(title: String, value: String, valueSet: Boolean, highlighted: Boolean, modifier: Modifier,
    open: MutableState<Boolean>, panel: @Composable (SelectMetrics, () -> Unit) -> Unit) {
    val sheet = LocalSelectPresentation.current == SelectPresentation.Sheet
    val close = { open.value = false }
    Box(modifier) {
        SelectTrigger(title, value, valueSet, highlighted, sheet) { open.value = true }
        if (sheet) { if (open.value) SelectSheet(title, close) { listMax -> panel(SelectMetrics(48.dp, listMax, sheet = true), close) } }
        else PopoverMenu(open.value, close) { panel(PopoverMetrics, close) }
    }
}

/** The design's dropdown chip: "Genre: " in secondary text, then the value, primary once set. */
@Composable
private fun SelectTrigger(title: String, value: String, valueSet: Boolean, highlighted: Boolean, sheet: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(Modifier.widthIn(max = ChipMaxWidth).chipFrame(highlighted, sheet, shape)
        .pointerClickable(role = Role.DropdownList, shape = shape, onClick = onClick).padding(start = 14.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Not `fill`: inside the scrolling bar the chip is as wide as its text, up to [ChipMaxWidth].
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = Palette.secondary)) { append("$title: ") }
            withStyle(SpanStyle(color = if (valueSet) Palette.text else Palette.secondary)) { append(value) }
        }, Modifier.weight(1f, fill = false), fontSize = chipText(sheet), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Lucide.ChevronDown, null, Modifier.size(14.dp), tint = Palette.secondary)
    }
}

/**
 * The phone presentation (components.md section 8): a surface-1 bottom sheet with a 28 dp
 * top radius and a grabber over a 0.62 scrim. It holds at most 80 % of the screen; a tap on
 * the scrim, the close button or Back dismisses it.
 */
@Composable
private fun SelectSheet(title: String, dismiss: () -> Unit, content: @Composable (Dp) -> Unit) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(220)) }
    Popup(onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val height = maxHeight * .8f
            Box(Modifier.matchParentSize().graphicsLayer { alpha = rise.value }.background(Color.Black.copy(alpha = .62f))
                .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = height)
                .graphicsLayer { translationY = size.height * (1f - rise.value) }
                .shadow(24.dp, SheetShape).clip(SheetShape).background(Palette.surface)
                // A tap on the sheet's own background must not reach the scrim below it.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .padding(bottom = 24.dp)) {
                Box(Modifier.padding(top = 8.dp, bottom = 4.dp).align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .3f)))
                Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f))
                        .pointerClickable(onClick = dismiss, shape = CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Lucide.X, "Close", Modifier.size(16.dp))
                    }
                }
                // Grabber, title and padding take about 120 dp of the sheet.
                Box(Modifier.padding(horizontal = 6.dp)) { content(height - 120.dp) }
            }
        }
    }
}

@Composable
private fun SelectPanel(options: List<SelectOption>, selected: List<String>, multiple: Boolean, cap: Int?,
    anyLabel: String?, anyTrailing: String?, searchPlaceholder: String, metrics: SelectMetrics,
    onChange: (List<String>) -> Unit, close: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val folded = remember(options) { options.map { foldForSearch(it.label) } }
    val searchable = options.size + (if (anyLabel != null) 1 else 0) > SearchRowLimit
    // A null row is the "Any" row; it is only offered before a search narrows the list.
    val rows: List<SelectOption?> = remember(options, folded, query, anyLabel) {
        val needle = foldForSearch(query.trim())
        buildList {
            if (anyLabel != null && needle.isEmpty()) add(null)
            options.forEachIndexed { index, option -> if (folded[index].contains(needle)) add(option) }
        }
    }
    var active by remember { mutableIntStateOf(rows.indexOfFirst { if (it == null) selected.isEmpty() else it.id in selected }.coerceAtLeast(0)) }
    // Bumped by the arrow keys only: hovering a row moves the highlight but never scrolls the list under the mouse.
    var keyboardMoves by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (active - 3).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val rowPx = with(LocalDensity.current) { metrics.rowHeight.toPx() }
    // Pointer layouts type straight into the search; touch layouts would raise the keyboard
    // over the list, so there the panel itself takes focus and only arrows and OK act.
    val searchFirst = searchable && LocalPointerHover.current

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

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
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

    Column(metrics.width.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent event.key in SelectKeys
        when (event.key) {
            Key.DirectionDown -> { move(1); true }
            Key.DirectionUp -> { move(-1); true }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { if (active in rows.indices) pick(rows[active]); true }
            Key.Escape -> { close(); true }
            else -> false
        }
    }.then(if (searchFirst) Modifier else Modifier.focusRequester(focus).focusable())) {
        if (searchable) SelectField(query, searchPlaceholder, Lucide.Search, metrics, if (searchFirst) Modifier.focusRequester(focus) else Modifier) {
            query = it; active = 0
            scope.launch { list.scrollToItem(0) }
        }
        if (rows.isEmpty()) Text("No matches for “${query.trim()}”", Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 13.sp, color = Palette.secondary)
        // A fixed width: the popover measures its column's intrinsic width, which a lazy list cannot answer.
        LazyColumn(metrics.width.heightIn(max = metrics.listMaxHeight), state = list,
            contentPadding = PaddingValues(top = 4.dp)) {
            itemsIndexed(rows) { index, row ->
                val checked = if (row == null) selected.isEmpty() else row.id in selected
                SelectRow(row?.label ?: anyLabel.orEmpty(), unavailable = row != null && !row.enabled,
                    trailing = listOfNotNull(if (row == null) anyTrailing else row.trailing, "Current".takeIf { !multiple && checked }).joinToString(" · ").ifEmpty { null },
                    checked, multiple, enabled(row), active = index == active, metrics.rowHeight, hovered = { active = index }) { pick(row) }
            }
        }
    }
}

private val SelectKeys = listOf(Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.NumPadEnter, Key.DirectionCenter)

/** A text filter's panel: one field that applies on Enter or Done, and "Any" to clear a set value. */
@Composable
private fun TextPanel(title: String, value: String, number: Boolean, metrics: SelectMetrics, onChange: (String) -> Unit, close: () -> Unit) {
    var draft by remember { mutableStateOf(value) }
    var applied by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    fun apply(next: String) {
        if (applied) return
        applied = true
        if (next != value) onChange(next)
        close()
    }
    // Typing is the point of this panel, so its field takes focus on every layout.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(metrics.width.onPreviewKeyEvent { event ->
        when {
            event.key == Key.Escape && event.type == KeyEventType.KeyDown -> { close(); true }
            event.key != Key.Enter && event.key != Key.NumPadEnter -> false
            event.type == KeyEventType.KeyDown -> { apply(draft.trim()); true }
            else -> true
        }
    }) {
        SelectField(draft, "Enter ${title.lowercase()}", null, metrics, Modifier.focusRequester(focus),
            KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Done),
            KeyboardActions(onDone = { apply(draft.trim()) })) { draft = it }
        if (LocalPointerHover.current) Text("Press Enter to apply", Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            fontSize = 12.sp, color = Palette.tertiary)
        if (value.isNotBlank()) SelectRow("Any", unavailable = false, trailing = null, checked = false, multiple = false, enabled = true,
            active = false, metrics.rowHeight, hovered = {}) { apply("") }
    }
}

/**
 * The design's text field (radius 12, hairline border, focus raises it to 1.5 px at 0.22,
 * accent caret), 40 tall in the popover and 44 in the sheet. It is filled with the page
 * background because a surface-1 field would vanish into the surface-1 panel.
 */
@Composable
private fun SelectField(text: String, placeholder: String, icon: ImageVector?, metrics: SelectMetrics, modifier: Modifier,
    keyboard: KeyboardOptions = KeyboardOptions.Default, actions: KeyboardActions = KeyboardActions.Default, change: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp).fillMaxWidth().height(if (metrics.sheet) 44.dp else 40.dp).clip(shape)
        .background(Palette.background).border(if (focused) 1.5.dp else 1.dp, if (focused) Color.White.copy(alpha = .22f) else Hairline, shape)
        .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon?.let { Icon(it, null, Modifier.size(15.dp), tint = Palette.tertiary) }
        BasicTextField(text, change, modifier.weight(1f), singleLine = true, interactionSource = interaction,
            textStyle = TextStyle(color = Palette.text, fontSize = 14.sp), cursorBrush = SolidColor(Palette.accent),
            keyboardOptions = keyboard, keyboardActions = actions,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text(placeholder, fontSize = 14.sp, color = Palette.tertiary, maxLines = 1)
                    inner()
                }
            })
        if (text.isNotEmpty()) Icon(Lucide.X, "Clear text", Modifier.size(16.dp)
            .pointerClickable(shape = CircleShape) { change("") }, tint = Palette.secondary)
    }
}

/**
 * A choice row: a check (single) or checkbox (multiple), the label (with "· unavailable" when
 * it cannot be picked) and tertiary trailing text. Mouse hover and the keyboard share one
 * highlight, the popover row's surface-2 fill.
 */
@Composable
private fun SelectRow(label: String, unavailable: Boolean, trailing: String?, checked: Boolean, multiple: Boolean, enabled: Boolean,
    active: Boolean, height: Dp, hovered: () -> Unit, pick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pointer = interaction.pointerHovered()
    LaunchedEffect(pointer) { if (pointer) hovered() }
    val click = if (multiple) Modifier.pointerToggleable(checked, interaction, null, enabled, Role.Checkbox, RowShape, hover = null) { pick() }
    else Modifier.pointerSelectable(checked, interaction, null, enabled, Role.RadioButton, RowShape, hover = null, onClick = pick)
    Row(Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(height).clip(RowShape)
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
        trailing?.let { Text(it, Modifier.widthIn(max = 120.dp), fontSize = 12.sp, color = Palette.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
