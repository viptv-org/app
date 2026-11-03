package org.viptv.app.features.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.components.FilterBar
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption
import org.viptv.app.connection.SourceProducerOutcome

internal fun sourceQualityOptions(sources: List<SourceCandidate>): List<SelectOption> =
    sources.mapNotNull { it.qualityLabel() }.groupingBy { it }.eachCount().map { (label, count) -> SelectOption(label, label, count.toString()) }

internal fun sourceProviderOptions(sources: List<SourceCandidate>, producers: List<SourceProducerOutcome> = emptyList()): List<SelectOption> {
    val options = LinkedHashMap<String, SelectOption>()
    sources.groupBy { it.sourceFilterId() }.forEach { (id, group) ->
        options[id] = SelectOption(id, group.first().providerLabel(), group.size.toString())
    }
    producers.filter { producer -> sources.none { it.provider_id == producer.providerId } }.forEach { producer ->
        val id = sourceProducerFilterId(producer.providerId)
        options.getOrPut(id) { SelectOption(id, producer.displayLabel()) }
    }
    return options.values.toList()
}

@Composable
internal fun SourceFilters(sources: List<SourceCandidate>, quality: String?, provider: String?,
    qualityChanged: (String?) -> Unit, providerChanged: (String?) -> Unit,
    modifier: Modifier = Modifier, gutter: Dp = 20.dp, bleed: Boolean = false,
    producers: List<SourceProducerOutcome> = emptyList(), down: FocusRequester? = null) {
    val qualities = remember(sources) { sourceQualityOptions(sources) }
    val providers = remember(sources, producers) { sourceProviderOptions(sources, producers) }
    FilterBar(modifier, gutter = gutter, bleed = bleed,
        clear = if (quality != null || provider != null) ({ qualityChanged(null); providerChanged(null) }) else null, down = down) {
        item(key = "quality") {
            SearchableSelect("Quality", qualities, listOfNotNull(quality), { qualityChanged(it.firstOrNull()) },
                anyLabel = "All", anyTrailing = sources.size.toString())
        }
        item(key = "source") {
            SearchableSelect("Source", providers, listOfNotNull(provider), { providerChanged(it.firstOrNull()) },
                anyLabel = "All sources", anyTrailing = sources.size.toString(), searchPlaceholder = "Search sources")
        }
    }
}
