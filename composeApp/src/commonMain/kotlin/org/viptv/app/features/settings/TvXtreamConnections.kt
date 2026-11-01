package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.LocalTvColumnFocus
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.tvRingReach
import org.viptv.app.components.tvRingRoom
import org.viptv.app.connection.XtreamConnection
import org.viptv.app.connection.XtreamScopes
import org.viptv.app.features.entry.TvEntryKeyboard
import org.viptv.app.theme.Palette

private enum class TvXtreamMode { Actions, Edit, Password, Delete }

private sealed interface TvXtreamReturn {
    data class Row(val id: Long) : TvXtreamReturn
    data object Add : TvXtreamReturn
}

private val TvXtreamListBottom = 72.dp

@Composable
internal fun TvXtreamConnections(connections: List<XtreamConnection>?, defaultLive: Long?, busy: Boolean, error: String?,
    notice: String?, canManage: Boolean, manageNote: String?, scale: Float, first: FocusRequester, sidebar: FocusRequester,
    request: () -> Unit, create: (String, String, String, String, XtreamScopes) -> Unit,
    update: (Long, String, XtreamScopes) -> Unit, replacePassword: (Long, String) -> Unit, sync: (Long) -> Unit,
    setDefault: (Long) -> Unit, remove: (Long) -> Unit) {
    var openId by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var returnTo by remember { mutableStateOf<TvXtreamReturn?>(null) }
    val rowFocus = remember { mutableMapOf<Long, FocusRequester>() }
    val addFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { request() }
    // The Retry button holds focus until the list replaces it; the removed node would drop focus to an unrelated sidebar row.
    val loaded = connections != null
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        repeat(3) {
            withFrameNanos { }
            if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    val open = openId?.let { id -> connections?.find { it.id == id } }
    LaunchedEffect(connections, openId) { if (openId != null && connections != null && open == null) openId = null }
    // The page replaces the list in place, so closing it hands the D-pad back to the row that opened it.
    LaunchedEffect(openId, creating, returnTo) {
        if (openId != null || creating) return@LaunchedEffect
        val target = returnTo ?: return@LaunchedEffect
        val candidates = when (target) {
            is TvXtreamReturn.Row -> listOfNotNull(rowFocus[target.id])
            TvXtreamReturn.Add -> listOfNotNull(addFocus, first)
        }
        repeat(3) {
            withFrameNanos { }
            if (candidates.any { runCatching { it.requestFocus() }.getOrDefault(false) }) return@LaunchedEffect
        }
        returnTo = null
    }
    val close: () -> Unit = {
        returnTo = if (creating) TvXtreamReturn.Add else openId?.let { TvXtreamReturn.Row(it) }
        creating = false
        openId = null
    }
    if (creating || open != null) {
        TvXtreamPage(open, creating, defaultLive == open?.id, busy, error, canManage, requireNotNull(manageNote), scale, close,
            create = create, update = update, replacePassword = replacePassword, sync = sync, setDefault = setDefault,
            remove = { id -> remove(id); returnTo = TvXtreamReturn.Add; creating = false; openId = null })
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        TvSettingsHeading(xtreamConnectionsSection, scale)
        Text("Connections belong to this account. Live, movie and series catalogues come from each one you enable.",
            color = Palette.secondary, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
        if (!canManage) Text(requireNotNull(manageNote), color = Palette.tertiary, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
        notice?.let { Text(it, color = Palette.secondary, fontSize = (22 * scale).sp) }
        error?.takeIf { openId == null && !creating }?.let { Text(it, color = Palette.danger, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp) }
        val list = connections
        if (list == null) {
            Text(if (busy) "Loading connections…" else "Could not load connections.", color = Palette.secondary, fontSize = (22 * scale).sp)
            TvAction(Modifier.height(72.dp * scale).width(240.dp * scale), scale, requester = first, left = sidebar,
                enabled = !busy, keepFocus = true, activate = request) { tint ->
                Text("Retry", color = tint, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
            }
        } else {
            CompositionLocalProvider(LocalTvColumnFocus provides null) {
                LazyColumn(Modifier.fillMaxWidth().weight(1f).tvRingRoom(tvRingReach(scale), horizontal = false),
                    contentPadding = PaddingValues(top = tvRingReach(scale), bottom = TvXtreamListBottom * scale),
                    verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    itemsIndexed(list, key = { _, connection -> connection.id }) { index, connection ->
                        val requester = remember(connection.id) { FocusRequester() }
                        SideEffect { rowFocus[connection.id] = requester }
                        TvXtreamRow(connection, defaultLive == connection.id, scale, sidebar,
                            requester = if (index == 0) first else requester, activate = { openId = connection.id })
                    }
                    if (list.isEmpty()) item(key = "empty") {
                        Text("No Xtream connections yet.", Modifier.padding(horizontal = 24.dp * scale), color = Palette.secondary, fontSize = (22 * scale).sp)
                    }
                    if (canManage) item(key = "add") {
                        TvAction(Modifier.fillMaxWidth().height(100.dp * scale)
                            .focusProperties { left = sidebar; down = FocusRequester.Cancel; if (list.isEmpty()) up = FocusRequester.Cancel }
                            .then(if (list.isEmpty()) Modifier.focusRequester(first) else Modifier.focusRequester(addFocus)), scale, ring = false,
                            enabled = !busy, keepFocus = true, activate = { creating = true }, arrangement = Arrangement.Start,
                            contentPadding = PaddingValues(horizontal = 24.dp * scale)) { tint ->
                            Box(Modifier.size(60.dp * scale).background(tint.copy(alpha = 0.1f), RoundedCornerShape(14.dp * scale)),
                                contentAlignment = Alignment.Center) { Icon(Lucide.Plus, "Add an Xtream connection", Modifier.size(30.dp * scale), tint) }
                            Spacer(Modifier.width(20.dp * scale))
                            Text("Add an Xtream connection", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvXtreamRow(connection: XtreamConnection, defaultLive: Boolean, scale: Float, sidebar: FocusRequester,
    requester: FocusRequester?, activate: () -> Unit) {
    TvAction(Modifier.fillMaxWidth().height(100.dp * scale), scale, requester = requester, left = sidebar, ring = false,
        background = Color(0xFF191919), arrangement = Arrangement.Start, contentPadding = PaddingValues(horizontal = 24.dp * scale),
        activate = activate) { tint ->
        Icon(Lucide.Server, null, Modifier.size(30.dp * scale), tint)
        Spacer(Modifier.width(20.dp * scale))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
            Text(connection.name, color = tint, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(xtreamStatusLine(connection) + " · " + xtreamScopeSummary(connection.scopes), color = tint.copy(alpha = 0.75f),
                fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (defaultLive) Text("Default live", color = tint.copy(alpha = 0.75f), fontSize = (20 * scale).sp, fontWeight = FontWeight.SemiBold)
        Icon(Lucide.ChevronRight, null, Modifier.size(24.dp * scale), tint)
    }
}

@Composable
private fun TvXtreamPage(connection: XtreamConnection?, creating: Boolean, defaultLive: Boolean, busy: Boolean,
    error: String?, canManage: Boolean, manageNote: String, scale: Float, close: () -> Unit,
    create: (String, String, String, String, XtreamScopes) -> Unit, update: (Long, String, XtreamScopes) -> Unit,
    replacePassword: (Long, String) -> Unit, sync: (Long) -> Unit, setDefault: (Long) -> Unit, remove: (Long) -> Unit) {
    var mode by remember(connection?.id, creating) { mutableStateOf(if (creating) TvXtreamMode.Edit else TvXtreamMode.Actions) }
    var name by remember(connection?.id) { mutableStateOf(connection?.name.orEmpty()) }
    var url by remember(connection?.id) { mutableStateOf("") }
    var username by remember(connection?.id) { mutableStateOf("") }
    var password by remember(connection?.id) { mutableStateOf("") }
    var scopes by remember(connection?.id) { mutableStateOf(connection?.scopes ?: XtreamScopes()) }
    var field by remember(mode) { mutableStateOf<String?>(null) }
    val initial = remember { FocusRequester() }
    val manageable = canManage && (connection == null || connection.credentialsEncrypted)
    val editable = manageable && !busy
    val valid = when {
        creating -> name.isNotBlank() && url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        mode == TvXtreamMode.Password -> password.isNotBlank()
        mode == TvXtreamMode.Edit -> name.isNotBlank()
        else -> false
    }
    LaunchedEffect(mode) {
        repeat(4) {
            withFrameNanos { }
            if (runCatching { initial.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    fun change(key: String, transform: (String) -> String) {
        when (key) {
            "name" -> name = transform(name).take(200)
            "url" -> url = transform(url).take(2048)
            "username" -> username = transform(username).take(512)
            else -> password = transform(password).take(2048)
        }
    }
    fun value(key: String) = when (key) {
        "name" -> name; "url" -> url; "username" -> username; else -> password
    }
    val fields = when {
        creating -> listOf("name", "url", "username", "password")
        mode == TvXtreamMode.Password -> listOf("password")
        mode == TvXtreamMode.Edit -> listOf("name")
        else -> emptyList()
    }
    Column(Modifier.fillMaxSize()) {
                Text(xtreamConnectionsSection, color = Palette.secondary, fontSize = (24 * scale).sp)
                Spacer(Modifier.height(8.dp * scale))
                Text(if (creating) "Add an Xtream connection" else requireNotNull(connection).name,
                    fontSize = (56 * scale).sp, lineHeight = (64 * scale).sp, letterSpacing = (-1.12f * scale).sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp * scale))
                Text(if (creating) "Enter the login your provider gave you. Saved credentials are never shown again."
                    else xtreamStatusLine(requireNotNull(connection)) + " · " + xtreamScopeSummary(requireNotNull(connection).scopes),
                    color = Palette.secondary, fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp)
                if (!creating && !requireNotNull(connection).credentialsEncrypted)
                    Text("Operator migration required before this connection can be changed.", color = Palette.tertiary,
                        fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
                if (!canManage) Text(manageNote, color = Palette.tertiary, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
                Spacer(Modifier.height(20.dp * scale))
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    TvFocusScroll(horizontal = false) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp * scale)) {
                        when (mode) {
                            TvXtreamMode.Actions -> TvXtreamActions(requireNotNull(connection), defaultLive, editable, manageable, scale, initial,
                                sync = { sync(requireNotNull(connection).id) },
                                useDefault = { setDefault(requireNotNull(connection).id) },
                                edit = { mode = TvXtreamMode.Edit },
                                password = { mode = TvXtreamMode.Password },
                                delete = { mode = TvXtreamMode.Delete },
                                close = close)
                            TvXtreamMode.Edit, TvXtreamMode.Password -> {
                                fields.forEachIndexed { index, key ->
                                    TvXtreamField(label = when (key) {
                                        "name" -> "Connection name"; "url" -> "Server URL"; "username" -> "Username"
                                        else -> if (creating) "Password" else "New password"
                                    }, value = value(key), secret = key == "password", active = field == key, busy = !editable,
                                        scale = scale, requester = if (index == 0) initial else null, keepFocus = manageable,
                                        onActivate = { field = key })
                                }
                                if (mode == TvXtreamMode.Edit) {
                                    Text("Available content", color = Palette.secondary, fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
                                    xtreamScopeRows.forEach { (key, label) ->
                                        TvXtreamScopeRow(label, scopes.flag(key), editable, scale, null,
                                            activate = { scopes = scopes.with(key, !scopes.flag(key)) })
                                    }
                                }
                                val active = field
                                if (active != null && editable) TvEntryKeyboard(scale,
                                    onDelete = { change(active) { it.dropLast(1) } }, onClear = { change(active) { "" } }) { input ->
                                    change(active) { it + input }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                                    TvAction(Modifier.height(72.dp * scale).width(280.dp * scale), scale, primary = true,
                                        enabled = valid && !busy, keepFocus = valid, activate = {
                                            val target = connection
                                            when {
                                                target == null -> create(name.trim(), url.trim(), username.trim(), password, scopes)
                                                mode == TvXtreamMode.Password -> replacePassword(target.id, password)
                                                else -> update(target.id, name.trim(), scopes)
                                            }
                                            close()
                                        }) { tint ->
                                        Text(if (creating) "Add connection" else "Save", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                                    }
                                    TvAction(Modifier.height(72.dp * scale).width(220.dp * scale), scale, enabled = !busy, keepFocus = true,
                                        activate = { if (creating) close() else mode = TvXtreamMode.Actions }) { tint ->
                                        Text("Cancel", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            TvXtreamMode.Delete -> {
                                Text("This removes the connection and its saved credentials. Its catalogues and live channels stop showing. Viewing history is kept.",
                                    color = Palette.secondary, fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                                    TvAction(Modifier.height(72.dp * scale).width(260.dp * scale), scale, requester = initial, enabled = !busy, keepFocus = true,
                                        activate = { remove(requireNotNull(connection).id) }) {
                                        Icon(Lucide.Trash2, null, Modifier.size(28.dp * scale), Palette.danger); Spacer(Modifier.width(12.dp * scale))
                                        Text("Remove", color = Palette.danger, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                                    }
                                    TvAction(Modifier.height(72.dp * scale).width(220.dp * scale), scale, enabled = !busy, keepFocus = true,
                                        activate = { mode = TvXtreamMode.Actions }) { tint ->
                                        Text("Cancel", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    error?.let { Text("Nothing was saved. $it", color = Palette.danger, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp) }
                }
            }
        }
    }
}

@Composable
private fun TvXtreamActions(connection: XtreamConnection, defaultLive: Boolean, editable: Boolean, keepFocus: Boolean, scale: Float,
    initial: FocusRequester, sync: () -> Unit, useDefault: () -> Unit, edit: () -> Unit, password: () -> Unit,
    delete: () -> Unit, close: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        TvAction(Modifier.fillMaxWidth().height(96.dp * scale), scale, requester = initial, enabled = editable && !connection.refresh.running, keepFocus = keepFocus,
            arrangement = Arrangement.Start, contentPadding = PaddingValues(horizontal = 28.dp * scale), activate = sync) { tint ->
            Icon(Lucide.RefreshCw, null, Modifier.size(28.dp * scale), tint); Spacer(Modifier.width(24.dp * scale))
            Text("Sync now", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
        if (connection.scopes.enabled && connection.scopes.live && !defaultLive) TvAction(Modifier.fillMaxWidth().height(96.dp * scale), scale,
            enabled = editable, keepFocus = keepFocus, arrangement = Arrangement.Start, contentPadding = PaddingValues(horizontal = 28.dp * scale),
            activate = useDefault) { tint ->
            Icon(Lucide.Star, null, Modifier.size(28.dp * scale), tint); Spacer(Modifier.width(24.dp * scale))
            Text("Use as default live playlist", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
        TvAction(Modifier.fillMaxWidth().height(96.dp * scale), scale, enabled = editable, keepFocus = keepFocus, arrangement = Arrangement.Start,
            contentPadding = PaddingValues(horizontal = 28.dp * scale), activate = edit) { tint ->
            Icon(Lucide.Pencil, null, Modifier.size(28.dp * scale), tint); Spacer(Modifier.width(24.dp * scale))
            Text("Edit name and content", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
        TvAction(Modifier.fillMaxWidth().height(96.dp * scale), scale, enabled = editable, keepFocus = keepFocus, arrangement = Arrangement.Start,
            contentPadding = PaddingValues(horizontal = 28.dp * scale), activate = password) { tint ->
            Icon(Lucide.KeyRound, null, Modifier.size(28.dp * scale), tint); Spacer(Modifier.width(24.dp * scale))
            Text("Replace password", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
        TvAction(Modifier.fillMaxWidth().height(96.dp * scale), scale, enabled = editable, keepFocus = keepFocus, arrangement = Arrangement.Start,
            contentPadding = PaddingValues(horizontal = 28.dp * scale), activate = delete) {
            Icon(Lucide.Trash2, null, Modifier.size(28.dp * scale), Palette.danger); Spacer(Modifier.width(24.dp * scale))
            Text("Remove connection", color = Palette.danger, fontSize = (26 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
        connection.refresh.error?.let { Text(it, color = Palette.danger, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp) }
        TvAction(Modifier.height(72.dp * scale).width(220.dp * scale), scale, activate = close) { tint ->
            Text("Back", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TvXtreamField(label: String, value: String, secret: Boolean, active: Boolean, busy: Boolean, scale: Float,
    requester: FocusRequester?, keepFocus: Boolean, onActivate: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
        Text(label, fontSize = (24 * scale).sp, color = Palette.text, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().height(80.dp * scale).clip(RoundedCornerShape(14.dp * scale))
            .background(Color.White.copy(alpha = 0.08f))
            .border((if (active) 2 else 1).dp * scale, if (active) Palette.text else Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp * scale))
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .focusable(enabled = !busy || keepFocus).onFocusChanged { focused = it.isFocused }
            .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !busy) { onActivate() }
            .padding(horizontal = 28.dp * scale), verticalAlignment = Alignment.CenterVertically) {
            Text(if (value.isEmpty()) label else if (secret) "•".repeat(value.length.coerceAtMost(48)) else value,
                Modifier.weight(1f), fontSize = (28 * scale).sp, color = if (value.isEmpty()) Palette.secondary else Palette.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (focused && active) Box(Modifier.padding(start = 16.dp * scale).width(2.dp * scale).height(40.dp * scale).background(Palette.accent))
        }
    }
}

@Composable
private fun TvXtreamScopeRow(label: String, checked: Boolean, enabled: Boolean, scale: Float, requester: FocusRequester?,
    activate: () -> Unit) {
    TvAction(Modifier.fillMaxWidth().height(72.dp * scale), scale, requester = requester, enabled = enabled,
        arrangement = Arrangement.Start, contentPadding = PaddingValues(horizontal = 24.dp * scale), activate = activate) { tint ->
        Box(Modifier.size((24 * scale).dp).clip(RoundedCornerShape(6.dp * scale))
            .background(if (checked) Palette.text else Color.White.copy(alpha = 0.12f))) {
            if (checked) Icon(Lucide.Check, null, Modifier.size((18 * scale).dp).align(Alignment.Center), tint = Palette.background)
        }
        Spacer(Modifier.width(16.dp * scale))
        Text(label, color = tint, fontSize = (24 * scale).sp, fontWeight = FontWeight.Medium)
    }
}

private val xtreamScopeRows = listOf("enabled" to "Connection enabled", "live" to "Live TV",
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
