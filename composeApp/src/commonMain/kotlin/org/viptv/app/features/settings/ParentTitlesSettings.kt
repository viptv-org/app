package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import org.viptv.app.components.ServerMessages
import org.viptv.app.connection.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.Button
import org.viptv.app.components.OutlinedButton
import org.viptv.app.components.TextButton

private data class TitleChoice(val kind: String, val id: String, val title: String, val action: String,
    val row: String? = null, val saved: ApprovedTitle? = null, val blocked: BlockedTitle? = null,
    val candidate: org.viptv.engine.wire.Card? = null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParentTitlesSettings(state: ParentTitlesState, busy: Boolean, error: String?, back: () -> Unit,
    search: (String) -> Unit, refresh: (Boolean) -> Unit, approve: (String, org.viptv.engine.wire.Card) -> Unit,
    revoke: (ApprovedTitle) -> Unit, block: (org.viptv.engine.wire.Card) -> Unit, unblock: (BlockedTitle) -> Unit,
    refreshBlocked: (Boolean) -> Unit, first: FocusRequester? = null) {
    var query by remember(state.profile) { mutableStateOf(state.query) }
    var choice by remember(state.profile) { mutableStateOf<TitleChoice?>(null) }
    if (first != null) LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact = maxWidth < 640.dp
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!compact) TextButton(back, modifier = if (first != null) Modifier.focusRequester(first) else Modifier) { Text("Back to parental controls") }
                Text("Approved and blocked titles", style = MaterialTheme.typography.headlineMedium)
                Text("For ${state.profileName}", style = MaterialTheme.typography.titleMedium)
                Text("Allow individual titles beyond this profile’s age limit, or block titles entirely. Adult titles cannot be approved. Search uses the integrations enabled for this profile.", color = Palette.secondary)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = Palette.danger) }
                state.notice?.let { Text(it, color = Palette.secondary) }
                OutlinedTextField(query, { query = it.take(200) }, label = { Text("Search titles") },
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
                val blocked = state.isBlocked(item)
                ParentTitleRowCard(card.title, card.identity.kind, item.message?.let { ServerMessages.human(it, ServerMessages.PARENT_TITLE) }) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ choice = TitleChoice(card.identity.kind, card.identity.id, card.title, "approve",
                            row.id, candidate = card) },
                            enabled = !busy && item.approval_supported && !approved) { Text(if (approved) "Approved" else "Approve title") }
                        if (card.identity.kind == "movie" || card.identity.kind == "series")
                            TextButton({ choice = TitleChoice(card.identity.kind, card.identity.id, card.title, "block",
                                row.id, candidate = card) }, enabled = !busy && !blocked) { Text(if (blocked) "Blocked" else "Block title") }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider(color = Palette.raised)
                Text("Approved titles", style = MaterialTheme.typography.titleLarge)
                OutlinedButton({ refresh(false) }, enabled = !busy) { Text("Refresh approved titles") }
                if (state.loaded && state.approved.isEmpty()) Text("No titles have been approved for ${state.profileName}.", color = Palette.secondary)
            }
        }
        items(state.approved) { title ->
            ParentTitleRowCard(title.name ?: title.id, title.type, null) {
                TextButton({ choice = TitleChoice(title.type, title.id, title.name ?: title.id, "revoke", saved = title) },
                    enabled = !busy) { Text("Remove approval", color = Palette.danger) }
            }
        }
        if (state.nextOffset != null) item { OutlinedButton({ refresh(true) }, enabled = !busy) { Text("Load more approvals") } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider(color = Palette.raised)
                Text("Blocked titles", style = MaterialTheme.typography.titleLarge)
                Text("Blocked titles stay hidden for ${state.profileName}, even when they are approved.", color = Palette.secondary)
                OutlinedButton({ refreshBlocked(false) }, enabled = !busy) { Text("Refresh blocked titles") }
                if (state.blockedLoaded && state.blocked.isEmpty()) Text("No titles are blocked for ${state.profileName}.", color = Palette.secondary)
            }
        }
        items(state.blocked) { title ->
            ParentTitleRowCard(title.name ?: title.id, title.type, null) {
                TextButton({ choice = TitleChoice(title.type, title.id, title.name ?: title.id, "unblock", blocked = title) },
                    enabled = !busy) { Text("Unblock title") }
            }
        }
        if (state.blockedNextOffset != null) item { OutlinedButton({ refreshBlocked(true) }, enabled = !busy) { Text("Load more blocked titles") } }
    }
    }
    choice?.let { selected ->
        AlertDialog(onDismissRequest = { if (!busy) choice = null },
            title = { Text(when (selected.action) {
                "approve" -> "Approve this title?"
                "block" -> "Block this title?"
                "unblock" -> "Unblock this title?"
                else -> "Remove this approval?"
            }) },
            text = { Text(when (selected.action) {
                "approve" -> "Allow ${selected.title} for ${state.profileName}? The server checks the title before saving."
                "block" -> "Hide ${selected.title} from ${state.profileName}? A block outranks an approval."
                "unblock" -> "Show ${selected.title} to ${state.profileName} again? This profile’s age restrictions still apply."
                else -> "Remove ${selected.title} from ${state.profileName}’s approved titles? The profile’s age restrictions will apply again."
            }) },
            confirmButton = { TextButton({
                if (!busy) {
                    when (selected.action) {
                        "approve" -> approve(requireNotNull(selected.row), requireNotNull(selected.candidate))
                        "block" -> block(requireNotNull(selected.candidate))
                        "unblock" -> unblock(requireNotNull(selected.blocked))
                        else -> revoke(requireNotNull(selected.saved))
                    }
                    choice = null
                }
            }, enabled = !busy) { Text(when (selected.action) {
                "approve" -> "Approve"
                "block" -> "Block"
                "unblock" -> "Unblock"
                else -> "Remove approval"
            }) } },
            dismissButton = { TextButton({ choice = null }, enabled = !busy) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
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