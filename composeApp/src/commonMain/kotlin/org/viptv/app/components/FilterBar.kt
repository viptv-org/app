package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.viptv.engine.wire.Choice
import org.viptv.engine.wire.ControlKind
import org.viptv.engine.wire.QueryControl

// Narrower than this on a touch screen is a phone: its chips open bottom sheets.
private val PhoneWidth = 640.dp

@Composable
internal fun FilterBar(modifier: Modifier = Modifier, gutter: Dp = 0.dp, bleed: Boolean = true, clear: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val phone = !LocalPointerHover.current && maxWidth + (if (bleed) gutter * 2 else 0.dp) < PhoneWidth
        val tv = LocalTvFocusAnchoring.current
        val scale = LocalTvScale.current
        val presentation = when {
            tv -> SelectPresentation.Panel
            phone -> SelectPresentation.Sheet
            else -> SelectPresentation.Popover
        }
        // The row is one focus group on TV: a closing panel lands back on its chip, and clearing re-enters the row.
        val row = remember { FocusRequester() }
        // A TV ring is drawn outside the chip; the scrolling row would clip it at either end.
        val reach = if (tv) tvRingReach(scale) else 0.dp
        CompositionLocalProvider(LocalSelectPresentation provides presentation) {
            FocusLazyRow(Modifier.fillMaxWidth().then(if (tv) Modifier.tvRingRoom(reach, vertical = false) else Modifier)
                .bleedHorizontally(if (bleed) gutter else 0.dp)
                .then(if (tv) Modifier.focusRequester(row).focusGroup() else Modifier),
                contentPadding = PaddingValues(horizontal = gutter + reach),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                wheelScrollsRow = true) {
                content()
                if (clear != null) item(key = "filter-bar:clear") {
                    // Clearing removes the Clear chip itself, so TV puts focus back on the row.
                    ActionChip("Clear", Lucide.X, { clear(); runCatching { row.requestFocus() } })
                }
            }
        }
    }
}

@Composable
internal fun FilterDivider() {
    Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 20.dp).background(Hairline))
}

private fun Choice.isAny() = value.isEmpty() || label.equals("Any", true)

@Composable
internal fun QueryControlFilter(control: QueryControl, selected: List<String>, changed: (String) -> Unit,
    multipleChanged: (List<String>) -> Unit, impliedAny: Boolean = true, highlighted: Boolean? = null) {
    val values = selected.filter(String::isNotBlank)
    val placeholder = if (control.required) "Required" else control.choices.firstOrNull { it.isAny() }?.label ?: "Any"
    when (control.kind) {
        ControlKind.TOGGLE -> ToggleSelect(control.label, values.lastOrNull() == "true", { changed(if (it) "true" else "") })
        ControlKind.TEXT, ControlKind.NUMBER, ControlKind.DATE -> TextSelect(control.label, values.firstOrNull().orEmpty(), changed,
            placeholder = placeholder, number = control.kind == ControlKind.NUMBER, highlighted = highlighted ?: values.isNotEmpty())
        ControlKind.SINGLECHOICE, ControlKind.MULTICHOICE -> {
            val multiple = control.kind == ControlKind.MULTICHOICE
            val options = control.choices.filter { it.value.isNotEmpty() }.map { SelectOption(it.value, it.label) }
            val providerAny = control.choices.firstOrNull { it.isAny() }
            val anyLabel = when {
                control.required -> null
                providerAny == null -> if (impliedAny) "Any" else null
                providerAny.value.isEmpty() -> providerAny.label
                else -> null
            }
            val current = if (multiple) values else values.take(1)
            val change: (List<String>) -> Unit = { if (multiple) multipleChanged(it) else changed(it.firstOrNull().orEmpty()) }
            SearchableSelect(control.label, options, current, change, multiple = multiple,
                maximumSelections = control.maximum_selections?.toInt(), anyLabel = anyLabel, placeholder = placeholder,
                highlighted = highlighted ?: (anyLabel != null && current.isNotEmpty()))
        }
    }
}
