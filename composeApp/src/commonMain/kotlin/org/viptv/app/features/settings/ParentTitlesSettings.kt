package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.components.ServerMessages
import org.viptv.app.connection.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.Button
import org.viptv.app.components.OutlinedButton
import org.viptv.app.components.TextButton

private data class ApprovalChoice(val kind: String, val id: String, val title: String, val row: String? = null, val saved: ApprovedTitle? = null, val candidate: org.viptv.engine.wire.Card? = null)

@Composable
internal fun ParentTitlesSettings(state: ParentTitlesState, busy: Boolean, error: String?, back: () -> Unit,
    search: (String) -> Unit, refresh: (Boolean) -> Unit, approve: (String, org.viptv.engine.wire.Card) -> Unit,
    revoke: (ApprovedTitle) -> Unit) {
    var query by remember(state.profile) { mutableStateOf(state.query) }
    var choice by remember(state.profile) { mutableStateOf<ApprovalChoice?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact = maxWidth < 640.dp
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!compact) TextButton(back) { Text("Back to parental controls") }
                Text("Approved titles", style = MaterialTheme.typography.headlineMedium)
                Text("For ${state.profileName}", style = MaterialTheme.typography.titleMedium)
                Text("Allow individual titles beyond this profile’s age limit. Adult titles cannot be approved. Search uses the integrations enabled for this profile.", color = Palette.secondary)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = Palette.danger) }
                state.notice?.let { Text(it, color = Palette.secondary) }
                OutlinedTextField(query, { query = it.take(200) }, label = { Text("Search titles to approve") },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                Button({ search(query) }, enabled = !busy && query.isNotBlank()) { Text("Search titles") }
            }
        }
        if (state.searched && state.rows.isEmpty()) item { Text("No searchable integrations are available for this profile.", color = Palette.secondary) }
        state.rows.forEach { row ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.title, style = MaterialTheme.typography.titleLarge)
                    row.message?.let { Text(ServerMessages.human(it, ServerMessages.PARENT_ROW), color = Palette.secondary) }
                    if (row.items.isEmpty()) Text(if (row.state == "Ready") "No matching titles." else "These results are unavailable. Try searching again.", color = Palette.secondary)
                }
            }
            items(row.items) { item ->
                val card = item.card
                val approved = state.isApproved(item)
                ParentTitleRowCard(card.title, card.identity.kind, item.message?.let { ServerMessages.human(it, ServerMessages.PARENT_TITLE) }) {
                    OutlinedButton({ choice = ApprovalChoice(card.identity.kind, card.identity.id, card.title, row.id, candidate = card) },
                        enabled = !busy && item.approval_supported && !approved) { Text(if (approved) "Approved" else "Approve title") }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider(color = Palette.raised)
                Text("Saved approvals", style = MaterialTheme.typography.titleLarge)
                OutlinedButton({ refresh(false) }, enabled = !busy) { Text("Refresh approved titles") }
                if (state.loaded && state.approved.isEmpty()) Text("No titles have been approved for ${state.profileName}.", color = Palette.secondary)
            }
        }
        items(state.approved) { title ->
            ParentTitleRowCard(title.name ?: title.id, title.type, null) {
                TextButton({ choice = ApprovalChoice(title.type, title.id, title.name ?: title.id, saved = title) }, enabled = !busy) { Text("Remove approval", color = Palette.danger) }
            }
        }
        if (state.nextOffset != null) item { OutlinedButton({ refresh(true) }, enabled = !busy) { Text("Load more approvals") } }
    }
    }
    choice?.let { selected ->
        val adding = selected.row != null
        AlertDialog(onDismissRequest = { if (!busy) choice = null },
            title = { Text(if (adding) "Approve this title?" else "Remove this approval?") },
            text = { Text(if (adding) "Allow ${selected.title} for ${state.profileName}? The server checks the title before saving."
                else "Remove ${selected.title} from ${state.profileName}’s approved titles? The profile’s age restrictions will apply again.") },
            confirmButton = { TextButton({
                if (!busy) {
                    if (selected.row != null) approve(selected.row, requireNotNull(selected.candidate)) else revoke(requireNotNull(selected.saved))
                    choice = null
                }
            }, enabled = !busy) { Text(if (adding) "Approve" else "Remove approval") } },
            dismissButton = { TextButton({ choice = null }, enabled = !busy) { Text("Cancel") } })
    }
}

@Composable
private fun ParentTitleRowCard(title: String, kind: String, message: String?, action: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = Palette.surface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(when (kind) { "series" -> "Series"; "movie" -> "Movie"; else -> kind }, color = Palette.secondary)
            message?.let { Text(it, color = Palette.secondary) }
            action()
        }
    }
}
