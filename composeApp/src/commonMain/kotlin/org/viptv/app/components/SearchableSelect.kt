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
import androidx.compose.ui.focus.onFocusChanged
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

@Immutable
internal data class SelectOption(val id: String, val label: String, val trailing: String? = null, val enabled: Boolean = true)

internal enum class SelectPresentation { Popover, Sheet, Panel }

internal val LocalSelectPresentation = staticCompositionLocalOf { SelectPresentation.Popover }

internal const val SearchRowLimit = 8

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

internal fun foldForSearch(text: String): String = buildString(text.length) {
    text.lowercase().forEach { char ->
        if (char in '̀'..'ͯ') return@forEach
        append(accentFolds[char] ?: char)
    }
}

private val Surface3 = Color(0xFF2A2A2E)
private val RowShape = RoundedCornerShape(10.dp)
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val ChipMaxWidth = 280.dp

private class SelectMetrics(val rowHeight: Dp, val listMaxHeight: Dp, val sheet: Boolean,
    val tv: Boolean = false, val scale: Float = 1f) {
    val width: Modifier get() = if (sheet || tv) Modifier.fillMaxWidth() else Modifier.width(PopoverMenuWidth)
}
private val PopoverMetrics = SelectMetrics(40.dp, 320.dp, sheet = false)

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
    anyTrailing: String? = null,
    placeholder: String = anyLabel ?: "Choose",
    searchPlaceholder: String = "Search ${title.lowercase()}",
    highlighted: Boolean = anyLabel != null && selected.isNotEmpty(),
    open: MutableState<Boolean> = remember { mutableStateOf(false) },
    chipFocus: FocusRequester? = null,
) {
    val distinct = remember(options) { options.distinctBy { it.id } }
    SelectChip(title, selectTriggerValue(distinct, selected, placeholder), selected.isNotEmpty(), highlighted, modifier, open, chipFocus) { metrics, close ->
        SelectPanel(distinct, selected, multiple, maximumSelections, anyLabel, anyTrailing, searchPlaceholder, metrics, onChange, close)
    }
}

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
    SelectChip(title, value.ifBlank { placeholder }, value.isNotBlank(), highlighted, modifier, open, entry = null) { metrics, close ->
        TextPanel(title, value, number, metrics, onChange, close)
    }
}

@Composable
internal fun ToggleSelect(title: String, on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val metrics = chipMetrics()
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }
    Row(modifier.chipFrame(on, focused, metrics, shape)
        .then(if (metrics.tv) Modifier.onFocusChanged { focused = it.isFocused }.tvFocusOutline(focused, metrics.corner, metrics.scale) else Modifier)
        .pointerToggleable(on, role = Role.Switch, shape = shape, onValueChange = onChange)
        .padding(start = if (on) metrics.startInset - 2.dp else metrics.startInset, end = metrics.endInset),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
        if (on) Icon(Lucide.Check, null, Modifier.size(metrics.icon), tint = if (focused) Palette.background else Palette.text)
        Text(title, fontSize = metrics.textSize, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = when { focused -> Palette.background; on -> Palette.text; else -> Palette.secondary })
    }
}

@Composable
internal fun ActionChip(label: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val metrics = chipMetrics()
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }
    Row(modifier.chipFrame(false, focused, metrics, shape, bordered = false)
        .then(if (metrics.tv) Modifier.onFocusChanged { focused = it.isFocused }.tvFocusOutline(focused, metrics.corner, metrics.scale) else Modifier)
        .pointerClickable(role = Role.Button, shape = shape, onClick = onClick)
        .padding(horizontal = metrics.actionInset), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
        icon?.let { Icon(it, null, Modifier.size(metrics.icon), tint = if (focused) Palette.background else Palette.secondary) }
        Text(label, fontSize = metrics.textSize, fontWeight = FontWeight.SemiBold,
            color = if (focused) Palette.background else Palette.secondary, maxLines = 1)
    }
}

private val OnLightSecondary = Color(0xFF4A4945)

private class ChipMetrics(val presentation: SelectPresentation, val scale: Float) {
    val tv = presentation == SelectPresentation.Panel
    private val sheet = presentation == SelectPresentation.Sheet
    val height: Dp = when { tv -> 56.dp * scale; sheet -> 44.dp; else -> 40.dp }
    val textSize = when { tv -> (22 * scale).sp; sheet -> 14.sp; else -> 13.sp }
    val icon: Dp = if (tv) 22.dp * scale else 14.dp
    val corner: Dp = height / 2
    val startInset: Dp = if (tv) 24.dp * scale else 14.dp
    val endInset: Dp = if (tv) 20.dp * scale else 12.dp
    val actionInset: Dp = if (tv) 20.dp * scale else 12.dp
    val gap: Dp = if (tv) 10.dp * scale else 6.dp
}

@Composable
private fun chipMetrics(): ChipMetrics {
    val presentation = LocalSelectPresentation.current
    return ChipMetrics(presentation, if (presentation == SelectPresentation.Panel) LocalTvScale.current else 1f)
}

private fun Modifier.chipFrame(set: Boolean, focused: Boolean, metrics: ChipMetrics, shape: RoundedCornerShape,
    bordered: Boolean = true) =
    height(metrics.height).clip(shape)
        .background(when { focused -> Palette.text; set -> Surface3; else -> Color.Transparent })
        .then(if (bordered && !focused) Modifier.border(1.dp, Color.White.copy(alpha = if (set) .22f else .10f), shape) else Modifier)

@Composable
private fun SelectChip(title: String, value: String, valueSet: Boolean, highlighted: Boolean, modifier: Modifier,
    open: MutableState<Boolean>, entry: FocusRequester?, panel: @Composable (SelectMetrics, () -> Unit) -> Unit) {
    val presentation = LocalSelectPresentation.current
    val metrics = chipMetrics()
    val close = { open.value = false }
    // On a TV, closing the panel puts focus back on the chip that opened it.
    val own = remember { FocusRequester() }
    val chipFocus = entry ?: own
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open.value) {
        if (open.value) wasOpen = true
        else if (wasOpen) runCatching { chipFocus.requestFocus() }
    }
    Box(modifier) {
        SelectTrigger(title, value, valueSet, highlighted, metrics, chipFocus.takeIf { metrics.tv }) { open.value = true }
        when (presentation) {
            SelectPresentation.Sheet -> if (open.value) SelectSheet(title, close) { listMax ->
                panel(SelectMetrics(48.dp, listMax, sheet = true), close) }
            SelectPresentation.Panel -> if (open.value) SelectPanelSheet(title, close) { scale ->
                panel(SelectMetrics(72.dp * scale, 640.dp * scale, sheet = false, tv = true, scale = scale), close) }
            SelectPresentation.Popover -> PopoverMenu(open.value, close) { panel(PopoverMetrics, close) }
        }
    }
}

@Composable
private fun SelectTrigger(title: String, value: String, valueSet: Boolean, highlighted: Boolean, metrics: ChipMetrics,
    focus: FocusRequester?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }
    val ink = if (focused) Palette.background else if (valueSet) Palette.text else Palette.secondary
    Row(Modifier.widthIn(max = if (metrics.tv) 420.dp * metrics.scale else ChipMaxWidth)
        .then(if (focus == null) Modifier else Modifier.focusRequester(focus).onFocusChanged { focused = it.isFocused }
            .tvFocusOutline(focused, metrics.corner, metrics.scale))
        .chipFrame(highlighted, focused, metrics, shape)
        .pointerClickable(role = Role.DropdownList, shape = shape, onClick = onClick)
        .padding(start = metrics.startInset, end = metrics.endInset),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
        // Not `fill`: inside the scrolling bar the chip is as wide as its text, up to the cap.
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = if (focused) OnLightSecondary else Palette.secondary)) { append("$title: ") }
            withStyle(SpanStyle(color = ink)) { append(value) }
        }, Modifier.weight(1f, fill = false), fontSize = metrics.textSize, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Icon(Lucide.ChevronDown, null, Modifier.size(metrics.icon), tint = if (focused) OnLightSecondary else Palette.secondary)
    }
}

@Composable
private fun SelectSheet(title: String, dismiss: () -> Unit, content: @Composable (Dp) -> Unit) {
    val rise = remember { Animatable(0f) }
    val riseSpec = motionSpec<Float>(tween(220))
    LaunchedEffect(Unit) { rise.animateTo(1f, riseSpec) }
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
private fun SelectPanelSheet(title: String, dismiss: () -> Unit, content: @Composable (Float) -> Unit) {
    Popup(onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val scale = maxWidth / 1920.dp
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .6f))
                .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss))
            Column(Modifier.align(Alignment.CenterEnd).width(760.dp * scale).fillMaxHeight()
                // Back and Escape close only the panel; the chip row behind it stays as it was.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) { dismiss(); true }
                    else false
                }
                .background(Color(0xFF161618)).padding(start = 56.dp * scale, end = 56.dp * scale,
                    top = 72.dp * scale, bottom = 56.dp * scale)) {
                Text(title, Modifier.semantics { heading() }, fontSize = (44 * scale).sp, lineHeight = (53 * scale).sp,
                    letterSpacing = (-.88f * scale).sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp * scale))
                Column(Modifier.fillMaxWidth().weight(1f)) { content(scale) }
            }
        }
    }
}

@Composable
private fun SelectPanel(options: List<SelectOption>, selected: List<String>, multiple: Boolean, cap: Int?,
    anyLabel: String?, anyTrailing: String?, searchPlaceholder: String, metrics: SelectMetrics,
    onChange: (List<String>) -> Unit, close: () -> Unit) {
    if (metrics.tv) {
        SelectTvPanel(options, selected, multiple, cap, anyLabel, anyTrailing, searchPlaceholder, metrics, onChange, close)
        return
    }
    var query by remember { mutableStateOf("") }
    val folded = remember(options) { options.map { foldForSearch(it.label) } }
    val searchable = options.size + (if (anyLabel != null) 1 else 0) > SearchRowLimit
    // A null row is the "Any" row, offered only before a search narrows the list.
    val rows = remember(options, folded, query, anyLabel) { choiceRows(options, folded, query, anyLabel) }
    var active by remember { mutableIntStateOf(rows.indexOfFirst { if (it == null) selected.isEmpty() else it.id in selected }.coerceAtLeast(0)) }
    // Bumped by the arrow keys only: hovering a row must not scroll the list under the mouse.
    var keyboardMoves by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (active - 3).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val rowPx = with(LocalDensity.current) { metrics.rowHeight.toPx() }
    // Pointer layouts type into the search; touch layouts focus the panel, where only arrows and OK act.
    val searchFirst = searchable && LocalPointerHover.current

    fun enabled(row: SelectOption?) = choiceEnabled(row, multiple, cap, selected)
    fun pick(row: SelectOption?) {
        if (!enabled(row)) return
        applyChoice(row, selected, multiple, onChange)
        if (!multiple) close()
    }
    fun move(step: Int) {
        var index = active + step
        while (index in rows.indices && !enabled(rows[index])) index += step
        if (index in rows.indices) { active = index; keyboardMoves++ }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
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
            Key.Escape, Key.Back -> { close(); true }
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

private fun choiceRows(options: List<SelectOption>, folded: List<String>, query: String, anyLabel: String?): List<SelectOption?> {
    val needle = foldForSearch(query.trim())
    return buildList {
        if (anyLabel != null && needle.isEmpty()) add(null)
        options.forEachIndexed { index, option -> if (folded[index].contains(needle)) add(option) }
    }
}

private fun choiceEnabled(row: SelectOption?, multiple: Boolean, cap: Int?, selected: List<String>): Boolean =
    row == null || row.enabled && (!multiple || cap == null || row.id in selected || selected.size < cap)

private fun applyChoice(row: SelectOption?, selected: List<String>, multiple: Boolean, onChange: (List<String>) -> Unit) {
    if (!multiple) { onChange(listOfNotNull(row?.id)); return }
    onChange(when {
        row == null -> emptyList()
        row.id in selected -> selected - row.id
        else -> selected + row.id
    })
}

private val SelectKeys = listOf(Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.NumPadEnter, Key.DirectionCenter)

@Composable
private fun SelectTvPanel(options: List<SelectOption>, selected: List<String>, multiple: Boolean, cap: Int?,
    anyLabel: String?, anyTrailing: String?, searchPlaceholder: String, metrics: SelectMetrics,
    onChange: (List<String>) -> Unit, close: () -> Unit) {
    val scale = metrics.scale
    var query by remember { mutableStateOf("") }
    val folded = remember(options) { options.map { foldForSearch(it.label) } }
    val searchable = options.size + (if (anyLabel != null) 1 else 0) > SearchRowLimit
    val rows = remember(options, folded, query, anyLabel) { choiceRows(options, folded, query, anyLabel) }
    val list = rememberLazyListState()
    // Keyed by option so a requester survives a search that renumbers the rows.
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun key(row: SelectOption?) = row?.id ?: "any"
    fun enabled(row: SelectOption?) = choiceEnabled(row, multiple, cap, selected)
    fun pick(row: SelectOption?) {
        if (!enabled(row)) return
        applyChoice(row, selected, multiple, onChange)
        if (!multiple) close()
    }
    // Open on the current option, scrolled into view; its focus is retried per frame until the requester is placed.
    LaunchedEffect(Unit) {
        val current = rows.indexOfFirst { if (it == null) selected.isEmpty() else it.id in selected }.coerceAtLeast(0)
        if (current > 0) list.scrollToItem((current - 2).coerceAtLeast(0))
        repeat(30) {
            withFrameNanos { }
            if (requesters[key(rows.getOrNull(current))]?.requestFocus() == true) return@LaunchedEffect
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (searchable) SelectField(query, searchPlaceholder, Lucide.Search, metrics, Modifier) { query = it }
        if (rows.isEmpty()) Text("No matches for “${query.trim()}”",
            Modifier.padding(horizontal = 8.dp * scale, vertical = 12.dp * scale), fontSize = (20 * scale).sp, color = Palette.secondary)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list,
            verticalArrangement = Arrangement.spacedBy(10.dp * scale), contentPadding = PaddingValues(vertical = 4.dp * scale)) {
            itemsIndexed(rows) { _, row ->
                val checked = if (row == null) selected.isEmpty() else row.id in selected
                TvSelectRow(row?.label ?: anyLabel.orEmpty(), unavailable = row != null && !row.enabled,
                    trailing = listOfNotNull(if (row == null) anyTrailing else row.trailing, "Current".takeIf { !multiple && checked }).joinToString(" · ").ifEmpty { null },
                    checked, multiple, enabled(row), scale, requesters.getOrPut(key(row)) { FocusRequester() }) { pick(row) }
            }
        }
    }
}

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
            event.key in listOf(Key.Escape, Key.Back) && event.type == KeyEventType.KeyDown -> { close(); true }
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
        if (value.isNotBlank()) {
            if (metrics.tv) TvSelectRow("Any", unavailable = false, trailing = null, checked = false, multiple = false, enabled = true,
                metrics.scale, remember { FocusRequester() }) { apply("") }
            else SelectRow("Any", unavailable = false, trailing = null, checked = false, multiple = false, enabled = true,
                active = false, metrics.rowHeight, hovered = {}) { apply("") }
        }
    }
}

@Composable
private fun SelectField(text: String, placeholder: String, icon: ImageVector?, metrics: SelectMetrics, modifier: Modifier,
    keyboard: KeyboardOptions = KeyboardOptions.Default, actions: KeyboardActions = KeyboardActions.Default, change: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(if (metrics.tv) 20.dp * metrics.scale else 12.dp)
    Row(Modifier.padding(horizontal = if (metrics.tv) 0.dp else 8.dp).padding(bottom = 4.dp).fillMaxWidth()
        .height(if (metrics.tv) 80.dp * metrics.scale else if (metrics.sheet) 44.dp else 40.dp).clip(shape)
        .background(Palette.background).border(if (focused) 1.5.dp else 1.dp, if (focused) Color.White.copy(alpha = .22f) else Hairline, shape)
        .padding(horizontal = if (metrics.tv) 24.dp * metrics.scale else 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (metrics.tv) 14.dp * metrics.scale else 8.dp)) {
        icon?.let { Icon(it, null, Modifier.size(if (metrics.tv) 22.dp * metrics.scale else 15.dp), tint = Palette.tertiary) }
        val size = if (metrics.tv) (22 * metrics.scale).sp else 14.sp
        BasicTextField(text, change, modifier.weight(1f), singleLine = true, interactionSource = interaction,
            textStyle = TextStyle(color = Palette.text, fontSize = size), cursorBrush = SolidColor(Palette.accent),
            keyboardOptions = keyboard, keyboardActions = actions,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text(placeholder, fontSize = size, color = Palette.tertiary, maxLines = 1)
                    inner()
                }
            })
        if (text.isNotEmpty()) Icon(Lucide.X, "Clear text", Modifier.size(if (metrics.tv) 22.dp * metrics.scale else 16.dp)
            .pointerClickable(shape = CircleShape) { change("") }, tint = Palette.secondary)
    }
}

@Composable
private fun ChoiceMark(checked: Boolean, multiple: Boolean, enabled: Boolean, onLight: Boolean,
    box: Dp, mark: Dp, radius: Dp, border: Dp, glyph: Dp) {
    Box(Modifier.size(box), contentAlignment = Alignment.Center) {
        if (!multiple) {
            if (checked) Icon(Lucide.Check, null, Modifier.size(mark), tint = if (onLight) Palette.background else Palette.accent)
            return@Box
        }
        Box(Modifier.size(mark).clip(RoundedCornerShape(radius))
            .background(if (checked) (if (onLight) Palette.background else Palette.accent) else Color.Transparent)
            .then(if (checked) Modifier else Modifier.border(border,
                if (onLight) OnLightSecondary else if (enabled) Palette.secondary else Palette.tertiary, RoundedCornerShape(radius))),
            contentAlignment = Alignment.Center) {
            if (checked) Icon(Lucide.Check, null, Modifier.size(glyph), tint = if (onLight) Palette.text else Palette.background)
        }
    }
}

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
        ChoiceMark(checked, multiple, enabled, onLight = false,
            box = 18.dp, mark = 16.dp, radius = 4.dp, border = 1.5.dp, glyph = 12.dp)
        Text(buildAnnotatedString {
            append(label)
            if (unavailable) withStyle(SpanStyle(color = Palette.tertiary, fontWeight = FontWeight.Normal)) { append(" · unavailable") }
        }, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (enabled) Palette.text else Palette.tertiary)
        trailing?.let { Text(it, Modifier.widthIn(max = 120.dp), fontSize = 12.sp, color = Palette.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun TvSelectRow(label: String, unavailable: Boolean, trailing: String?, checked: Boolean, multiple: Boolean,
    enabled: Boolean, scale: Float, requester: FocusRequester, pick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp * scale)
    val click = if (multiple) Modifier.pointerToggleable(checked, interaction, null, enabled, Role.Checkbox, shape, hover = null) { pick() }
    else Modifier.pointerSelectable(checked, interaction, null, enabled, Role.RadioButton, shape, hover = null, onClick = pick)
    Row(Modifier.fillMaxWidth().height(72.dp * scale).focusRequester(requester)
        .onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, 14.dp * scale, scale)
        .clip(shape)
        .background(when { focused -> Palette.text; checked -> Color.White.copy(alpha = .16f); else -> Color.White.copy(alpha = .04f) })
        .then(click).padding(horizontal = 24.dp * scale),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
        ChoiceMark(checked, multiple, enabled, onLight = focused,
            box = 24.dp * scale, mark = if (multiple) 22.dp * scale else 24.dp * scale,
            radius = 6.dp * scale, border = 2.dp * scale, glyph = 16.dp * scale)
        Text(buildAnnotatedString {
            append(label)
            if (unavailable) withStyle(SpanStyle(color = if (focused) OnLightSecondary else Palette.tertiary,
                fontWeight = FontWeight.Normal)) { append(" · unavailable") }
        }, Modifier.weight(1f), fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, color = if (focused) Palette.background else if (enabled) Palette.text else Palette.tertiary)
        trailing?.let { Text(it, Modifier.widthIn(max = 260.dp * scale), fontSize = (20 * scale).sp,
            color = if (focused) OnLightSecondary else Palette.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
