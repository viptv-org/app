package org.viptv.app.features.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.components.FilterBar
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption

/** One option per quality, in first-seen order, with its source count. */
internal fun sourceQualityOptions(sources: List<SourceCandidate>): List<SelectOption> =
    sources.mapNotNull { it.qualityLabel() }.groupingBy { it }.eachCount().map { (label, count) -> SelectOption(label, label, count.toString()) }

/** One option per add-on source (its [sourceFilterId]), in first-seen order, with its source count. */
internal fun sourceProviderOptions(sources: List<SourceCandidate>): List<SelectOption> =
    sources.groupBy { it.sourceFilterId() }.map { (id, group) -> SelectOption(id, group.first().providerLabel(), group.size.toString()) }

/**
 * The Quality and Source filters of the source picker on every layout (app#117): the same
 * filter bar as Browse and My List, "Quality: All ⌄" and "Source: All sources ⌄", each with
 * its count, then "Clear" while either is set. TV composes it too (app#121); its chips open
 * the panel and take focus, so [gutter] and [bleed] let the host place the row.
 */
@Composable
internal fun SourceFilters(sources: List<SourceCandidate>, quality: String?, provider: String?,
    qualityChanged: (String?) -> Unit, providerChanged: (String?) -> Unit,
    modifier: Modifier = Modifier, gutter: Dp = 20.dp, bleed: Boolean = false) {
    val qualities = remember(sources) { sourceQualityOptions(sources) }
    val providers = remember(sources) { sourceProviderOptions(sources) }
    FilterBar(modifier, gutter = gutter, bleed = bleed,
        clear = if (quality != null || provider != null) ({ qualityChanged(null); providerChanged(null) }) else null) {
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
