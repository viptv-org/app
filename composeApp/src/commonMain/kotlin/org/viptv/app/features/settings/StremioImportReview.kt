package org.viptv.app.features.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.connection.StremioAddon
import org.viptv.app.connection.StremioReviewItem
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerToggleable
import org.viptv.app.components.OutlinedButton
import org.viptv.app.components.IconButton
import org.viptv.app.components.Checkbox
import org.viptv.app.components.FilterChip

internal val stremioPreviewLabels = listOf("source_items" to "Source library records", "favorites_to_add" to "My List additions",
    "progress_to_add" to "New history/resume entries", "progress_to_update" to "Older history to update",
    "existing_preserved" to "Existing entries preserved", "already_imported" to "Already imported",
    "needs_review" to "Needs review — not imported", "skipped_items" to "Skipped source entries")
internal val stremioCompletionLabels = listOf("favorites_added" to "My List additions", "progress_added" to "New history/resume entries",
    "progress_updated" to "History entries updated", "existing_preserved" to "Existing entries preserved",
    "already_imported" to "Already imported", "needs_review" to "Needs review — not imported", "skipped_items" to "Skipped source entries")
private val actionLabels = mapOf("add" to "Add", "update" to "Update older entry", "preserve" to "Keep existing entry",
    "already_imported" to "Already imported", "none" to "No change")
private val statusLabels = mapOf("ready" to "Ready to import", "preserved" to "Preserving existing data", "needs_review" to "Needs review — left unchanged")
private val reasons = mapOf(
    "metadata_unavailable" to "Compatible metadata could not be verified.",
    "unsupported_identity" to "The title identity could not be verified with the available metadata.",
    "unmatched_identity" to "The title identity could not be verified with the available metadata.",
    "ambiguous_identity" to "Conflicting title identities need manual review.",
    "episode_metadata_missing" to "The episode could not be verified against its series metadata.",
    "watched_anchor_missing" to "The watched flags do not align with the verified episode list.",
    "unverified_episode_bitfield" to "The watched episodes could not be matched to a verified episode list.",
    "watch_date_unknown" to "Watched state is known, but the episode watch date is unavailable. Left unchanged.",
    "invalid_progress" to "The resume position or date could not be verified.",
    "addon_unavailable" to "This add-on could not be safely verified.",
    "secret_store_not_configured" to "Encrypted configuration storage is unavailable.",
    "vault_unavailable" to "Encrypted configuration storage is unavailable.",
    "unsupported_transport" to "This add-on uses an unsupported connection method.")
/** Reasons are fixed codes; anything unknown gets generic copy rather than server text. */
internal fun stremioReasonText(code: String?) = reasons[code] ?: "This entry needs more verified information and will be left unchanged."
/** m:ss like the dashboard, with hours once a position passes an hour (feature-length movies). */
internal fun stremioClock(seconds: Double): String {
    val total = seconds.toLong()
    val (h, m, s) = Triple(total / 3600, total % 3600 / 60, total % 60)
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}
/** Plain-language resources; metadata comes first because it is what helps verification. */
internal fun stremioResourceText(resources: List<String>) = buildList {
    add(if ("meta" in resources) "Metadata available" else "No metadata resource")
    if ("catalog" in resources) add("Catalogs")
    if ("stream" in resources) add("Streams")
    if ("subtitles" in resources) add("Subtitles")
}.joinToString(" · ")

@Composable
internal fun StremioSummary(counts: Map<String, Int>, labels: List<Pair<String, String>>) {
    // Two columns once there is room (tablet/browser); one wrapping column on phones.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 560.dp) 2 else 1
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            labels.chunked((labels.size + columns - 1) / columns).forEach { column ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    column.forEach { (key, label) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(label, Modifier.weight(1f), fontSize = 14.sp)
                            Text("${counts[key] ?: 0}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StremioCheckRow(label: String, checked: Boolean, enabled: Boolean, description: String? = null,
    supporting: String? = null, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().pointerToggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = change)
        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
        .padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null, enabled = enabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 14.sp)
            supporting?.let { Text(it, fontSize = 13.sp, color = Palette.secondary) }
        }
    }
}

@Composable
private fun ReviewCard(icon: ImageVector, checked: Boolean?, enabled: Boolean, description: String, dimmed: Boolean = false,
    alert: Boolean = false, modifier: Modifier = Modifier, change: (Boolean) -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.fillMaxWidth().border(if (alert) 2.dp else 1.dp, if (alert) Palette.danger else Palette.text.copy(alpha = .14f), shape)
        .then(if (checked != null) Modifier.pointerToggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = change, shape = shape)
            .semantics { contentDescription = description } else Modifier)
        .padding(14.dp).alpha(if (dimmed) .6f else 1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        if (checked != null) Checkbox(checked, null, enabled = enabled, modifier = Modifier.size(24.dp))
        Icon(icon, null, Modifier.padding(top = 2.dp).size(20.dp), tint = if (alert) Palette.danger else Palette.secondary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

private val addonFailures = mapOf(
    "manifest_id_changed" to "This add-on changed since Stremio saved it. Reinstall it from its website, or uncheck it.",
    "addon_protocol_invalid" to "This add-on uses an address VIPTV can't safely use. Uncheck it.",
    "addon_unreachable" to "This add-on didn't respond. Try again or uncheck it.",
    "addon_manifest_invalid" to "This add-on returned an invalid description. Uncheck it.",
    "addon_checks_busy" to "VIPTV is checking other add-ons. Try again in a moment.")
/** Fixed copy per failure category; no category keeps the dashboard copy, an unknown one gets generic copy. */
internal fun stremioAddonFailureText(reason: String?) = when (reason) {
    null -> "Could not verify this add-on. Uncheck it or continue without add-ons."
    else -> addonFailures[reason] ?: "This add-on couldn't be checked. Uncheck it or try again."
}

/** Ordinal among add-ons sharing an exact name, in Stremio's own order; null when the name is unique. */
internal fun stremioDuplicateOrdinals(addons: List<StremioAddon>): Map<String, Pair<Int, Int>> {
    val groups = addons.groupBy { it.name }.filterValues { it.size > 1 }
    return groups.values.flatMap { group -> group.mapIndexed { index, addon -> addon.id to (index + 1 to group.size) } }.toMap()
}

/**
 * One add-on choice. Several Stremio installs can share a name (one per debrid configuration),
 * so duplicates are numbered in Stremio's order; their private configuration is never shown.
 */
@Composable
internal fun StremioAddonRow(addon: StremioAddon, selected: Boolean, failed: Boolean, disabled: Boolean, ordinal: Pair<Int, Int>?,
    modifier: Modifier = Modifier, failure: String? = null, toggle: (Boolean) -> Unit) {
    val suffix = ordinal?.let { " (${it.first} of ${it.second})" }.orEmpty()
    ReviewCard(if (failed) Lucide.CircleAlert else Lucide.Puzzle, selected, !disabled && addon.status != "unavailable",
        "Use add-on ${addon.name}$suffix", dimmed = addon.status == "unavailable", alert = failed, modifier = modifier, change = toggle) {
        Text(addon.name + suffix, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (failed) Text("Couldn't be checked", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.danger)
        Text(when (addon.status) {
            "existing" -> "Already configured in your account"
            "add" -> if (selected) "Add to your account on confirmation" else "Not selected — won't be added"
            else -> "Unavailable — not added"
        }, fontSize = 13.sp)
        Text(stremioResourceText(addon.resources), fontSize = 13.sp, color = Palette.secondary)
        if (ordinal != null) Text("Shares its name with another add-on; numbered in your Stremio order.", fontSize = 13.sp, color = Palette.secondary)
        if (addon.status == "unavailable") Text(stremioReasonText(addon.reason), fontSize = 13.sp, color = Palette.secondary)
        if (failed) Text(stremioAddonFailureText(failure), fontSize = 13.sp, color = Palette.danger)
    }
}

internal val stremioFilters = listOf("all" to "All items", "ready" to "Ready to import", "preserved" to "Preserving existing data",
    "needs_review" to "Needs review / unmatched", "selected" to "Included items", "excluded" to "Excluded items")
internal const val stremioPageSize = 50

/**
 * Search, filter and page for the named review, kept for the whole draft so a re-review keeps them.
 * Lowercased names are computed once per review so a keystroke over thousands of rows is one
 * linear pass; exclusions are a hash set, so filtering never goes quadratic.
 */
@Stable
internal class StremioItemQuery {
    var search by mutableStateOf("")
    var filter by mutableStateOf("all")
    var page by mutableStateOf(0)
    private var source: List<StremioReviewItem>? = null
    private var names: List<String> = emptyList()
    private fun names(items: List<StremioReviewItem>): List<String> {
        if (source !== items) { source = items; names = items.map { it.name.lowercase() } }
        return names
    }
    fun filtered(items: List<StremioReviewItem>, excluded: Set<String>): List<StremioReviewItem> {
        val term = search.trim().lowercase()
        val lower = names(items)
        return items.filterIndexed { index, item -> (term.isEmpty() || lower[index].contains(term)) && when (filter) {
            "all" -> true
            "selected" -> item.selectable && item.id !in excluded
            "excluded" -> item.id in excluded
            else -> item.status == filter
        } }
    }
    fun counts(items: List<StremioReviewItem>, excluded: Set<String>): Map<String, Int> = mapOf("all" to items.size,
        "ready" to items.count { it.status == "ready" }, "preserved" to items.count { it.status == "preserved" },
        "needs_review" to items.count { it.status == "needs_review" },
        "selected" to items.count { it.selectable && it.id !in excluded }, "excluded" to excluded.size)
}

/** Live counts, search, status filters and bulk actions above the paged rows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StremioItemControls(query: StremioItemQuery, items: List<StremioReviewItem>, filtered: List<StremioReviewItem>, page: Int,
    excluded: Set<String>, disabled: Boolean, change: (Collection<String>, Boolean) -> Unit) {
    val counts = remember(items, excluded) { query.counts(items, excluded) }
    val anyMatching = filtered.any { it.selectable }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("${counts["selected"]} review items included · ${excluded.size} excluded · ${items.size} shown in this preview",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp)
        OutlinedTextField(query.search, { query.search = it.take(120); query.page = 0 }, Modifier.fillMaxWidth(), enabled = !disabled,
            singleLine = true, label = { Text("Find a title") }, leadingIcon = { Icon(Lucide.Search, null, Modifier.size(18.dp)) },
            trailingIcon = if (query.search.isNotEmpty()) ({ IconButton({ query.search = ""; query.page = 0 }, enabled = !disabled) {
                Icon(Lucide.X, "Clear search", Modifier.size(18.dp)) } }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false))
        Text("Show items", Modifier.semantics { heading() }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stremioFilters.forEach { (key, label) ->
                FilterChip(query.filter == key, { query.filter = key; query.page = 0 }, {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label); Text("${counts[key] ?: 0}", color = Palette.secondary)
                    }
                }, enabled = !disabled)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ change(items.map { it.id }, true) }, enabled = !disabled) { Text("Include all") }
            OutlinedButton({ change(items.map { it.id }, false) }, enabled = !disabled) { Text("Exclude all") }
            OutlinedButton({ change(filtered.map { it.id }, true) }, enabled = !disabled && anyMatching) { Text("Include matching") }
            OutlinedButton({ change(filtered.map { it.id }, false) }, enabled = !disabled && anyMatching) { Text("Exclude matching") }
        }
        if (filtered.isEmpty()) Text(if (items.isNotEmpty()) "No items match this filter." else "No supported items were found.",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp)
        // Results are paged; say what the list below holds right after a search or filter change.
        else Text("Showing ${page * stremioPageSize + 1}–${minOf(filtered.size, (page + 1) * stremioPageSize)} of ${filtered.size} matching",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 13.sp, color = Palette.secondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StremioPager(current: Int, pages: Int, matching: Int, disabled: Boolean, go: (Int) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically) {
        OutlinedButton({ go(current - 1) }, enabled = !disabled && current > 0) { Text("Previous items") }
        Text("Page ${current + 1} of $pages · $matching matching", fontSize = 14.sp)
        OutlinedButton({ go(current + 1) }, enabled = !disabled && current + 1 < pages) { Text("Next items") }
    }
}

@Composable
internal fun StremioReviewRow(item: StremioReviewItem, checked: Boolean, disabled: Boolean, focus: FocusRequester? = null, change: (Boolean) -> Unit) {
    val episode = item.season != null && item.episode != null
    ReviewCard(if (item.type == "series") Lucide.Tv else Lucide.Film, checked, !disabled && item.selectable,
        "Include ${item.name}" + (if (episode) " season ${item.season} episode ${item.episode}" else ""),
        dimmed = item.selectable && !checked, modifier = focus?.let { Modifier.focusRequester(it) } ?: Modifier, change = change) {
        Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text((if (item.type == "series") "Series" else "Movie") + (if (episode) " · Season ${item.season}, episode ${item.episode}" else ""),
            fontSize = 13.sp, color = Palette.secondary)
        Text(if (checked || !item.selectable) statusLabels.getValue(item.status) else "Excluded — will not be imported", fontSize = 13.sp,
            color = if (item.status == "needs_review") Palette.secondary else Palette.text)
        if (item.favoriteAction != "none") Text("My List: ${actionLabels.getValue(item.favoriteAction)}", fontSize = 13.sp)
        if (item.progressAction != "none") Text("History/resume: ${actionLabels.getValue(item.progressAction)}", fontSize = 13.sp)
        if (item.position != null && item.duration != null && item.duration > 0)
            Text("Resume ${stremioClock(item.position)} of ${stremioClock(item.duration)}", fontSize = 13.sp, color = Palette.secondary)
        if (item.watched == true) Text("Watched" + (if (item.resumeActive == true) " · Rewatch in progress" else "") +
            (if (item.watchDateKnown == false) " · watch date unavailable" else ""), fontSize = 13.sp, color = Palette.secondary)
        if (item.completionOnly == true) Text("Completion only — does not change Continue Watching", fontSize = 13.sp, color = Palette.secondary)
        if (item.status == "needs_review") Text(stremioReasonText(item.reason), fontSize = 13.sp, color = Palette.secondary)
    }
}
