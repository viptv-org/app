package org.viptv.app.features.sources

import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.components.LocalPointerHover
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption
import org.viptv.app.components.Surface
import org.viptv.app.components.useSelect

/** The source picker's filters turn into dropdowns above three options, "All" included (owner, app#59). */
internal const val SourceFilterChoiceLimit = 3

/**
 * The [useSelect] rule with the source picker's own limit. Its chips scroll instead of
 * sharing a track, so long add-on names alone never force the dropdown. Pass the
 * options with the "All" entry.
 */
internal fun sourceFilterUsesSelect(options: List<SelectOption>): Boolean = useSelect(options, SourceFilterChoiceLimit, budget = null)

/** One option per quality, in first-seen order, with its source count. */
internal fun sourceQualityOptions(sources: List<SourceCandidate>): List<SelectOption> =
    sources.mapNotNull { it.qualityLabel() }.groupingBy { it }.eachCount().map { (label, count) -> SelectOption(label, label, count.toString()) }

/** One option per add-on source (its [sourceFilterId]), in first-seen order, with its source count. */
internal fun sourceProviderOptions(sources: List<SourceCandidate>): List<SelectOption> =
    sources.groupBy { it.sourceFilterId() }.map { (id, group) -> SelectOption(id, group.first().providerLabel(), group.size.toString()) }

/**
 * The Quality and Source filters of the phone, tablet, web and desktop picker. On pointer
 * layouts (web, desktop) a filter with more than [SourceFilterChoiceLimit] options is the
 * shared [SearchableSelect]; touch layouts and smaller sets keep the chip rows.
 */
@Composable
internal fun SourceFilters(sources: List<SourceCandidate>, quality: String?, provider: String?,
    qualityChanged: (String?) -> Unit, providerChanged: (String?) -> Unit) {
    val qualities = remember(sources) { sourceQualityOptions(sources) }
    val providers = remember(sources) { sourceProviderOptions(sources) }
    val pointer = LocalPointerHover.current
    val qualitySelect = pointer && sourceFilterUsesSelect(listOf(SelectOption("", "All", sources.size.toString())) + qualities)
    val providerSelect = pointer && sourceFilterUsesSelect(listOf(SelectOption("", "All sources")) + providers)
    @Composable fun Chip(label: String, selected: Boolean, click: () -> Unit) {
        Surface(onClick = click, shape = RoundedCornerShape(18.dp), color = if (selected) Color(0xFFF4F3EF) else Color(0xFF29292B),
            contentColor = if (selected) Color(0xFF171719) else Color(0xFFCCCCCE)) {
            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 12.sp)
        }
    }
    @Composable fun QualitySelect(modifier: Modifier) = SearchableSelect("Quality", qualities, listOfNotNull(quality),
        { qualityChanged(it.firstOrNull()) }, modifier, anyLabel = "All", anyTrailing = sources.size.toString())
    @Composable fun ProviderSelect(modifier: Modifier) = SearchableSelect("Source", providers, listOfNotNull(provider),
        { providerChanged(it.firstOrNull()) }, modifier, anyLabel = "All sources", anyTrailing = sources.size.toString())
    /** Dropdowns share a line; a single one keeps half of it so it reads as the same control. */
    @Composable fun Selects(content: @Composable RowScope.() -> Unit) =
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (qualitySelect && providerSelect) Selects { QualitySelect(Modifier.weight(1f)); ProviderSelect(Modifier.weight(1f)) }
        else {
            if (qualitySelect) Selects { QualitySelect(Modifier.weight(1f)); Spacer(Modifier.weight(1f)) }
            else LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf<SelectOption?>(null) + qualities) { option ->
                    Chip("${option?.label ?: "All"} ${option?.trailing ?: sources.size}", quality == option?.id) { qualityChanged(option?.id) }
                }
            }
            if (providerSelect) Selects { ProviderSelect(Modifier.weight(1f)); Spacer(Modifier.weight(1f)) }
            else LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf<SelectOption?>(null) + providers) { option ->
                    Chip(option?.label ?: "All sources", provider == option?.id) { providerChanged(option?.id) }
                }
            }
        }
    }
}
