package org.viptv.app.features.settings

import org.viptv.app.components.AppTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.Button
import org.viptv.app.components.Checkbox
import org.viptv.app.components.FocusLazyColumn
import org.viptv.app.components.IconButton
import org.viptv.app.components.LocalPointerHover
import org.viptv.app.components.PhoneSheet
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption
import org.viptv.app.components.TextButton
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.connection.XtreamConnection
import org.viptv.app.connection.XtreamRefresh
import org.viptv.app.connection.XtreamScopes
import org.viptv.app.theme.Palette

internal enum class XtreamFormMode { Create, Edit, Password }

private const val XtreamMaximumName = 200

internal fun xtreamSyncLabel(refresh: XtreamRefresh): String = when (refresh.state) {
    "queued" -> "Sync queued"
    "running" -> "Syncing"
    "succeeded" -> "Up to date"
    "failed" -> "Sync failed"
    "cancelled" -> "Sync cancelled"
    else -> "Not synced yet"
}

internal fun xtreamScopeSummary(scopes: XtreamScopes): String {
    val content = listOfNotNull("Live TV".takeIf { scopes.live }, "Movies".takeIf { scopes.movies },
        "Series".takeIf { scopes.series })
    return content.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "No content enabled"
}

internal fun xtreamStatusLine(connection: XtreamConnection): String =
    (if (connection.scopes.enabled) "Enabled" else "Disabled") + " · " + xtreamSyncLabel(connection.refresh)

internal fun xtreamDefaultLiveOptions(connections: List<XtreamConnection>): List<SelectOption> =
    connections.filter { it.scopes.enabled && it.scopes.live }.map { SelectOption(it.id.toString(), it.name) }

@Composable
internal fun XtreamConnectionsSettings(connections: List<XtreamConnection>?, defaultLive: Long?, busy: Boolean,
    error: String?, notice: String?, canManage: Boolean, manageNote: String?, compact: Boolean, back: (() -> Unit)?,
    request: () -> Unit, create: (String, String, String, String, XtreamScopes) -> Unit,
    update: (Long, String, XtreamScopes) -> Unit, replacePassword: (Long, String) -> Unit, sync: (Long) -> Unit,
    setDefault: (Long) -> Unit, remove: (Long) -> Unit) {
    var mode by remember { mutableStateOf<XtreamFormMode?>(null) }
    var editing by remember { mutableStateOf<XtreamConnection?>(null) }
    var deleting by remember { mutableStateOf<XtreamConnection?>(null) }
    LaunchedEffect(Unit) { request() }
    val open: (XtreamFormMode, XtreamConnection?) -> Unit = { next, connection -> editing = connection; mode = next }
    val closeForm = { if (!busy) { mode = null; editing = null } }
    val sheet = !LocalPointerHover.current && compact
    FocusLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(if (compact) 16.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            if (compact) Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                back?.let { IconButton(onClick = it, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Lucide.ChevronLeft, "Back to settings") } }
                Text(xtreamConnectionsSection, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                back?.let { IconButton(onClick = it) { Icon(Lucide.ChevronLeft, "Back to settings") } }
                Text(xtreamConnectionsSection, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Text("Xtream connections belong to this account. Live, movie and series catalogues come from each connection you enable.",
                fontSize = 14.sp, lineHeight = 21.sp, color = Palette.secondary)
        }
        notice?.let { item { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 13.sp, color = Palette.secondary) } }
        if (!canManage) item { Text(requireNotNull(manageNote), fontSize = 13.sp, lineHeight = 19.sp, color = Palette.secondary) }
        error?.let { item { Text(it, fontSize = 13.sp, lineHeight = 19.sp, color = Palette.danger) } }
        if (canManage) item {
            Button({ open(XtreamFormMode.Create, null) }, Modifier.fillMaxWidth().heightIn(min = 44.dp), enabled = !busy,
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                Icon(Lucide.Plus, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("Add an Xtream connection", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
        if (connections == null) {
            if (!busy) item {
                Text(if (error == null) "Connections are not loaded." else "Could not load connections.", color = Palette.secondary)
                TextButton(request, enabled = !busy) { Text("Retry") }
            }
        } else {
            if (xtreamDefaultLiveOptions(connections).isNotEmpty()) item {
                SearchableSelect("Default live playlist", xtreamDefaultLiveOptions(connections),
                    listOfNotNull(defaultLive?.toString()),
                    { ids -> ids.firstOrNull()?.toLongOrNull()?.let { if (canManage && !busy) setDefault(it) } },
                    anyLabel = null, placeholder = "Choose", searchPlaceholder = "Search connections")
            }
            if (connections.isEmpty() && !busy) item {
                Text("No Xtream connections yet. Add one to browse its catalogues.", color = Palette.secondary)
            }
            connections.forEach { connection ->
                item(key = connection.id) {
                    XtreamConnectionRow(connection, defaultLive == connection.id, busy, canManage,
                        edit = { open(XtreamFormMode.Edit, connection) },
                        password = { open(XtreamFormMode.Password, connection) },
                        sync = { sync(connection.id) },
                        useDefault = { setDefault(connection.id) },
                        remove = { deleting = connection })
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(request, enabled = !busy) { Text("Refresh list") }
                    Text(if (connections.size == 1) "1 connection" else "${connections.size} connections", fontSize = 13.sp, color = Palette.secondary)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    val form = mode
    if (form != null) {
        val draft = rememberXtreamDraft(form, editing)
        val save = {
            if (draft.valid && !busy) {
                val target = editing
                when {
                    target == null -> create(draft.name.trim(), draft.url.trim(), draft.username.trim(), draft.password, draft.scopes)
                    form == XtreamFormMode.Password -> replacePassword(target.id, draft.password)
                    else -> update(target.id, draft.name.trim(), draft.scopes)
                }
                mode = null; editing = null
            }
        }
        if (sheet) PhoneSheet(visible = true, title = xtreamFormTitle(form), dismiss = closeForm, touch = true,
            closeEnabled = !busy, closeLabel = "Close connection form", dragEnabled = !busy) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                XtreamFormFields(form, draft, busy, error)
                Button(save, Modifier.fillMaxWidth().heightIn(min = 44.dp), enabled = draft.valid && !busy,
                    shape = RoundedCornerShape(12.dp)) { Text(xtreamFormAction(form), fontWeight = FontWeight.Bold) }
                TextButton(closeForm, enabled = !busy) { Text("Cancel") }
            }
        } else AlertDialog(onDismissRequest = closeForm, title = { Text(xtreamFormTitle(form)) },
            text = { TvFocusScroll(horizontal = false) {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    XtreamFormFields(form, draft, busy, error)
                } } },
            confirmButton = { TextButton(save, enabled = draft.valid && !busy) { Text(xtreamFormAction(form)) } },
            dismissButton = { TextButton(closeForm, enabled = !busy) { Text("Cancel") } })
    }
    deleting?.let { connection ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("Remove ${connection.name}?") },
            text = { Text("This removes the connection and its saved credentials. Its catalogues and live channels stop showing. Viewing history is kept.") },
            confirmButton = { TextButton({ remove(connection.id); deleting = null }, enabled = !busy) { Text("Remove", color = Palette.danger) } },
            dismissButton = { TextButton({ deleting = null }, enabled = !busy) { Text("Cancel") } })
    }
}

private fun xtreamFormTitle(mode: XtreamFormMode): String = when (mode) {
    XtreamFormMode.Create -> "Add an Xtream connection"
    XtreamFormMode.Edit -> "Edit connection"
    XtreamFormMode.Password -> "Replace password"
}

private fun xtreamFormAction(mode: XtreamFormMode): String = when (mode) {
    XtreamFormMode.Create -> "Add connection"
    XtreamFormMode.Edit -> "Save connection"
    XtreamFormMode.Password -> "Replace password"
}

@Stable
internal class XtreamDraft(val mode: XtreamFormMode, connection: XtreamConnection?) {
    var name by mutableStateOf(connection?.name.orEmpty())
    var url by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var scopes by mutableStateOf(connection?.scopes ?: XtreamScopes())
    val valid: Boolean
        get() = when (mode) {
            XtreamFormMode.Create -> name.isNotBlank() && url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
            XtreamFormMode.Edit -> name.isNotBlank()
            XtreamFormMode.Password -> password.isNotBlank()
        }
}

@Composable
private fun rememberXtreamDraft(mode: XtreamFormMode, connection: XtreamConnection?): XtreamDraft =
    remember(mode, connection) { XtreamDraft(mode, connection) }

@Composable
private fun XtreamFormFields(mode: XtreamFormMode, draft: XtreamDraft, busy: Boolean, error: String?) {
    if (mode != XtreamFormMode.Password) AppTextField(draft.name, { draft.name = it.take(XtreamMaximumName) },
        Modifier.fillMaxWidth(), label = "Connection name", enabled = !busy)
    if (mode == XtreamFormMode.Create) {
        AppTextField(draft.url, { draft.url = it.take(2048) }, Modifier.fillMaxWidth(), label = "Server URL",
            placeholder = "https://example.com:8080", enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
        AppTextField(draft.username, { draft.username = it.take(512) }, Modifier.fillMaxWidth(), label = "Username", enabled = !busy, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
    }
    if (mode != XtreamFormMode.Edit) AppTextField(draft.password, { draft.password = it.take(2048) }, Modifier.fillMaxWidth(),
        label = if (mode == XtreamFormMode.Create) "Password" else "New password", enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        visualTransformation = PasswordVisualTransformation())
    if (mode != XtreamFormMode.Password) {
        Text("Available content", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.secondary)
        xtreamScopeFields.forEach { (key, label) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(draft.scopes.flag(key), { on -> draft.scopes = draft.scopes.with(key, on) }, enabled = !busy)
                Spacer(Modifier.width(8.dp))
                Text(label, fontSize = 15.sp)
            }
        }
    } else Text("The server address and username stay as they are. Saved credentials are never shown.",
        fontSize = 13.sp, lineHeight = 19.sp, color = Palette.secondary)
    error?.let { Text(it, fontSize = 13.sp, lineHeight = 19.sp, color = Palette.danger) }
}

private val xtreamScopeFields = listOf("enabled" to "Connection enabled", "live" to "Live TV",
    "movies" to "Movies", "series" to "Series")

private fun XtreamScopes.flag(key: String): Boolean = when (key) {
    "enabled" -> enabled
    "live" -> live
    "movies" -> movies
    else -> series
}

private fun XtreamScopes.with(key: String, value: Boolean): XtreamScopes = when (key) {
    "enabled" -> copy(enabled = value)
    "live" -> copy(live = value)
    "movies" -> copy(movies = value)
    else -> copy(series = value)
}

@Composable
private fun XtreamConnectionRow(connection: XtreamConnection, defaultLive: Boolean, busy: Boolean, canManage: Boolean,
    edit: () -> Unit, password: () -> Unit, sync: () -> Unit, useDefault: () -> Unit, remove: () -> Unit) {
    val editable = canManage && !busy && connection.credentialsEncrypted
    Surface(shape = RoundedCornerShape(14.dp), color = Palette.text.copy(alpha = .05f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Lucide.Server, null, Modifier.size(20.dp), tint = Palette.secondary)
                Text(connection.name, Modifier.weight(1f), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                if (defaultLive) Text("Default live", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.accent)
            }
            Text(xtreamStatusLine(connection), fontSize = 13.sp, color = Palette.secondary)
            Text(xtreamScopeSummary(connection.scopes), fontSize = 13.sp, color = Palette.secondary)
            connection.refresh.error?.let { Text(it, fontSize = 13.sp, lineHeight = 19.sp, color = Palette.danger) }
            if (!connection.credentialsEncrypted) Text("Operator migration required before this connection can be changed.",
                fontSize = 13.sp, lineHeight = 19.sp, color = Palette.secondary)
            XtreamRowActions {
                TextButton(edit, enabled = editable) { Text("Edit") }
                TextButton(password, enabled = editable) { Text("Replace password") }
                TextButton(sync, enabled = editable && !connection.refresh.running) { Text("Sync now") }
                if (connection.scopes.enabled && connection.scopes.live && !defaultLive)
                    TextButton(useDefault, enabled = canManage && !busy) { Text("Use as default live") }
                TextButton(remove, enabled = editable) { Text("Remove", color = Palette.danger) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XtreamRowActions(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}
