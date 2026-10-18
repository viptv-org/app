package org.viptv.app.features.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.connection.StremioAddon
import org.viptv.app.connection.StremioReviewItem
import org.viptv.app.theme.Palette

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
    "watch_date_unknown" to "Watched state is known, but the episode watch date is unavailable. Left unchanged.",
    "invalid_progress" to "The resume position or date could not be verified.",
    "addon_unavailable" to "This add-on could not be safely verified.",
    "secret_store_not_configured" to "Encrypted configuration storage is unavailable.",
    "vault_unavailable" to "Encrypted configuration storage is unavailable.",
    "unsupported_transport" to "This add-on uses an unsupported connection method.")
/** Reasons are fixed codes; anything unknown gets generic copy rather than server text. */
internal fun stremioReasonText(code: String?) = reasons[code] ?: "This entry needs more verified information and will be left unchanged."
private fun clock(seconds: Double) = "${(seconds / 60).toInt()}:${(seconds % 60).toInt().toString().padStart(2, '0')}"

@Composable
internal fun StremioSummary(counts: Map<String, Int>, labels: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        labels.forEach { (key, label) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(label, Modifier.weight(1f), fontSize = 14.sp)
                Text("${counts[key] ?: 0}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun StremioCheckRow(label: String, checked: Boolean, enabled: Boolean, description: String? = null,
    change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = change)
        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
        .padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null, enabled = enabled)
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
    }
}

@Composable
private fun ReviewCard(icon: ImageVector, checked: Boolean?, enabled: Boolean, description: String, dimmed: Boolean = false,
    change: (Boolean) -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().border(1.dp, Palette.text.copy(alpha = .14f), shape)
        .then(if (checked != null) Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = change)
            .semantics { contentDescription = description } else Modifier)
        .padding(14.dp).alpha(if (dimmed) .6f else 1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        if (checked != null) Checkbox(checked, null, enabled = enabled, modifier = Modifier.size(24.dp))
        Icon(icon, null, Modifier.padding(top = 2.dp).size(20.dp), tint = Palette.secondary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

/** Editable for account sessions; a read-only status list for device sessions. */
@Composable
internal fun StremioAddonReview(addons: List<StremioAddon>, selected: Set<String>, failed: Set<String>, editable: Boolean,
    disabled: Boolean, toggle: (String, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Metadata add-ons can help verify titles and episode identities. Stream-only add-ons do not supply episode metadata.",
            fontSize = 13.sp, color = Palette.secondary)
        if (addons.isEmpty()) Text("No Stremio add-ons were found. Your existing account add-ons can still assist.",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp)
        addons.forEach { addon ->
            ReviewCard(Lucide.Puzzle, if (editable) addon.id in selected else null, !disabled && addon.status != "unavailable",
                "Use add-on ${addon.name}", change = { toggle(addon.id, it) }) {
                Text(addon.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(when (addon.status) {
                    "existing" -> "Already configured in your account"
                    "add" -> if (editable) "Add to your account on confirmation" else "New — not added from this device"
                    else -> "Unavailable — not added"
                }, fontSize = 13.sp)
                Text((if ("meta" in addon.resources) "Metadata available" else "No metadata resource") +
                    (if ("stream" in addon.resources) " · Stream resource" else ""), fontSize = 13.sp, color = Palette.secondary)
                if (addon.status == "unavailable") Text(stremioReasonText(addon.reason), fontSize = 13.sp, color = Palette.secondary)
                if (addon.id in failed) Text("Could not verify this add-on. Uncheck it or continue without add-ons.", fontSize = 13.sp, color = Palette.danger)
            }
        }
        Text(if (editable) "Selected new add-ons are account-wide, not limited to this profile. Their private configuration is encrypted and is never shown here. Nothing is added until final confirmation."
            else "Add-ons are account-wide. You can add them from VIPTV on the web.", fontSize = 13.sp, color = Palette.secondary)
    }
}

private val filters = listOf("all" to "All items", "ready" to "Ready to import", "preserved" to "Preserving existing data",
    "needs_review" to "Needs review / unmatched", "selected" to "Included items", "excluded" to "Excluded items")
private const val pageSize = 50

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StremioItemReview(items: List<StremioReviewItem>, excluded: Set<String>, disabled: Boolean,
    change: (Collection<String>, Boolean) -> Unit) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var page by remember { mutableStateOf(0) }
    val term = search.trim().lowercase()
    val filtered = remember(items, term, filter, excluded) { items.filter { item ->
        item.name.lowercase().contains(term) && (filter == "all" || filter == item.status ||
            (filter == "selected" && item.selectable && item.id !in excluded) || (filter == "excluded" && item.id in excluded))
    } }
    val pages = maxOf(1, (filtered.size + pageSize - 1) / pageSize)
    val current = minOf(page, pages - 1)
    val visible = filtered.drop(current * pageSize).take(pageSize)
    val included = items.count { it.selectable && it.id !in excluded }
    val anyMatching = filtered.any { it.selectable }
    Column(Modifier.semantics { contentDescription = "Items to review" }, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("$included review items included · ${excluded.size} excluded · ${items.size} shown in this preview",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp)
        OutlinedTextField(search, { search = it.take(120); page = 0 }, Modifier.fillMaxWidth(), enabled = !disabled,
            singleLine = true, label = { Text("Find a title") })
        Text("Show items", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { (key, label) ->
                FilterChip(filter == key, { filter = key; page = 0 }, { Text(label) }, enabled = !disabled)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ change(items.map { it.id }, true) }, enabled = !disabled) { Text("Include all") }
            OutlinedButton({ change(items.map { it.id }, false) }, enabled = !disabled) { Text("Exclude all") }
            OutlinedButton({ change(filtered.map { it.id }, true) }, enabled = !disabled && anyMatching) { Text("Include matching") }
            OutlinedButton({ change(filtered.map { it.id }, false) }, enabled = !disabled && anyMatching) { Text("Exclude matching") }
        }
        if (visible.isEmpty()) Text(if (items.isNotEmpty()) "No items match this filter." else "No supported items were found.",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp)
        visible.forEach { item -> key(item.id) { ReviewRow(item, item.selectable && item.id !in excluded, disabled) { change(listOf(item.id), it) } } }
        if (pages > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically) {
            OutlinedButton({ page = current - 1 }, enabled = !disabled && current > 0) { Text("Previous items") }
            Text("Page ${current + 1} of $pages · ${filtered.size} matching", fontSize = 14.sp)
            OutlinedButton({ page = current + 1 }, enabled = !disabled && current + 1 < pages) { Text("Next items") }
        }
        Text("Excluding an item only limits this import. It never removes anything from Stremio or VIPTV. Entries needing review remain unchanged.",
            fontSize = 13.sp, color = Palette.secondary)
    }
}

@Composable
private fun ReviewRow(item: StremioReviewItem, checked: Boolean, disabled: Boolean, change: (Boolean) -> Unit) {
    val episode = item.season != null && item.episode != null
    ReviewCard(if (item.type == "series") Lucide.Tv else Lucide.Film, checked, !disabled && item.selectable,
        "Include ${item.name}" + (if (episode) " season ${item.season} episode ${item.episode}" else ""),
        dimmed = item.selectable && !checked, change = change) {
        Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text((if (item.type == "series") "Series" else "Movie") + (if (episode) " · Season ${item.season}, episode ${item.episode}" else ""),
            fontSize = 13.sp, color = Palette.secondary)
        Text(if (checked || !item.selectable) statusLabels.getValue(item.status) else "Excluded — will not be imported", fontSize = 13.sp,
            color = if (item.status == "needs_review") Palette.secondary else Palette.text)
        if (item.favoriteAction != "none") Text("My List: ${actionLabels.getValue(item.favoriteAction)}", fontSize = 13.sp)
        if (item.progressAction != "none") Text("History/resume: ${actionLabels.getValue(item.progressAction)}", fontSize = 13.sp)
        if (item.position != null && item.duration != null && item.duration > 0)
            Text("Resume ${clock(item.position)} of ${clock(item.duration)}", fontSize = 13.sp, color = Palette.secondary)
        if (item.watched == true) Text("Watched" + (if (item.resumeActive == true) " · Rewatch in progress" else "") +
            (if (item.watchDateKnown == false) " · watch date unavailable" else ""), fontSize = 13.sp, color = Palette.secondary)
        if (item.completionOnly == true) Text("Completion only — does not change Continue Watching", fontSize = 13.sp, color = Palette.secondary)
        if (item.status == "needs_review") Text(stremioReasonText(item.reason), fontSize = 13.sp, color = Palette.secondary)
    }
}
