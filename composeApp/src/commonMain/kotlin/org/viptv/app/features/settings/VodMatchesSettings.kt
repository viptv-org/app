package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.viptv.app.components.OutlinedButton
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption
import org.viptv.app.components.TextButton
import org.viptv.app.state.VodMatchRow
import org.viptv.app.state.VodMatchesState
import org.viptv.app.theme.Palette

private val vodMatchTypeOptions = listOf(SelectOption("movie", "Movies"), SelectOption("series", "Series"))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VodMatchesSettings(state: VodMatchesState, providerChanged: (Long?) -> Unit, typeChanged: (String?) -> Unit,
    search: (String) -> Unit, loadMore: () -> Unit, retry: () -> Unit, openForm: (VodMatchRow) -> Unit,
    closeForm: () -> Unit, changeMetadata: (String) -> Unit, save: () -> Unit) {
    val providers = remember(state.providers) { state.providers.map { SelectOption(it.id.toString(), it.name) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("VOD matches", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Provider titles without a metadata match. Set the metadata ID a title should resolve to.",
                    color = Palette.secondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchableSelect("Provider", providers, listOfNotNull(state.providerId?.toString()),
                        { ids -> providerChanged(ids.firstOrNull()?.toLongOrNull()) }, anyLabel = "All providers",
                        searchPlaceholder = "Search providers")
                    SearchableSelect("Type", vodMatchTypeOptions, listOfNotNull(state.type),
                        { ids -> typeChanged(ids.firstOrNull()) }, anyLabel = "All types")
                }
                OutlinedTextField(state.query, { search(it.take(128)) }, label = { Text("Search titles") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = Palette.danger) }
                state.notice?.let { Text(it, color = Palette.secondary) }
            }
        }
        if (state.error != null) {
            item { OutlinedButton({ retry() }) { Text("Retry") } }
        }
        if (state.loaded && state.rows.isEmpty() && state.error == null) {
            item {
                Text(if (state.filtered) "No matching titles. Change the filters or search."
                    else "No unmatched titles.", color = Palette.secondary)
            }
        }
        items(state.rows, key = { it.vodId }) { row ->
            VodMatchCard(row, state.providerName(row.providerId), state.saving) { openForm(row) }
        }
        if (state.nextCursor != null) {
            item { OutlinedButton({ loadMore() }, enabled = !state.loading) { Text("Load more titles") } }
        }
    }
    state.form?.let { form ->
        AlertDialog(onDismissRequest = { if (!state.saving) closeForm() },
            title = { Text(if (form.metadataId.isBlank()) "Match title" else "Edit match") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(form.name, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(vodMatchTypeLabel(form.type), form.providerName).joinToString(" · "),
                        color = Palette.secondary)
                    OutlinedTextField(form.metadataId, { changeMetadata(it) }, label = { Text("Metadata ID") },
                        singleLine = true, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                    Text("For example tt0111161 or tmdb:550.", color = Palette.secondary,
                        style = MaterialTheme.typography.bodySmall)
                    state.error?.let { Text(it, color = Palette.danger) }
                }
            },
            confirmButton = {
                TextButton({ save() }, enabled = !state.saving && form.metadataId.isNotBlank()) {
                    Text(if (state.saving) "Saving…" else "Save match")
                }
            },
            dismissButton = { TextButton({ closeForm() }, enabled = !state.saving) { Text("Cancel") } })
    }
}

@Composable
private fun VodMatchCard(row: VodMatchRow, providerName: String?, busy: Boolean, openForm: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = Palette.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(providerName, vodMatchTypeLabel(row.type), row.year?.toString()).joinToString(" · "),
                color = Palette.secondary, style = MaterialTheme.typography.bodySmall)
            Text(row.metadataId?.let { "Matched to $it" } ?: "No metadata match",
                color = if (row.metadataId != null) Palette.accent else Palette.secondary)
            OutlinedButton({ openForm() }, enabled = !busy) {
                Text(if (row.metadataId != null) "Edit match" else "Match title")
            }
        }
    }
}

internal fun vodMatchTypeLabel(type: String): String = when (type) {
    "series" -> "Series"
    "movie" -> "Movie"
    else -> type
}