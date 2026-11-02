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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.viptv.app.components.Button
import org.viptv.app.components.TextButton
import org.viptv.app.components.Switch
import org.viptv.app.connection.*
import org.viptv.app.theme.Palette

@Composable
internal fun GatewaySettings(snapshot: GatewaySnapshot?, checks: Map<String, GatewayCheck>, grants: Map<String, List<Long>>,
    busy: Boolean, error: String?, errorCode: String?, loadError: String?, remote: RemoteConnection?,
    refresh: () -> Unit, check: (String) -> Unit, setEnabled: (String, Boolean) -> Unit, remove: (String) -> Unit,
    grantAccess: (String, Long) -> Unit, revokeAccess: (String, Long) -> Unit, loadGrants: (String) -> Unit,
    television: Boolean = false, scale: Float = 1f, first: FocusRequester? = null, left: FocusRequester? = null) {
    if (television) {
        TvGatewaySettings(snapshot, checks, busy, error, errorCode, loadError, refresh, check, setEnabled, scale, first, left)
        return
    }
    var registering by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<Gateway?>(null) }
    var deleting by remember { mutableStateOf<Gateway?>(null) }
    var granting by remember { mutableStateOf<Gateway?>(null) }
    val secretStorageConfigured = snapshot?.secret_storage_configured != false
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Playback gateways", style = MaterialTheme.typography.titleLarge)
            TextButton(refresh, enabled = !busy) { Text("Refresh") }
        }
        Text("Gateways transcode and deliver streams this device cannot play directly. Gateways are shared across the account.", color = Palette.secondary)
        if (!secretStorageConfigured) Text("The server's encryption keyring is not configured, so no gateway can be registered until the operator configures it.", color = Palette.danger)
        loadError?.let {
            Text(it, color = Palette.danger)
            TextButton(refresh, enabled = !busy) { Text("Retry") }
        }
        val operationMessage = gatewayCodeMessage(errorCode) ?: error
        operationMessage?.let { Text(it, color = Palette.danger) }
        Button({ registering = true }, enabled = secretStorageConfigured && !busy) { Text("Register gateway") }
        snapshot?.items.orEmpty().forEach { gateway ->
            GatewayCard(gateway, checks[gateway.id], busy,
                { check(gateway.id) }, { setEnabled(gateway.id, it) },
                { replacing = gateway }, { deleting = gateway }, { granting = gateway })
        }
        if (snapshot != null && snapshot.items.isEmpty() && loadError == null) Text("No gateways are configured for your account.", color = Palette.secondary)
    }
    if (registering) {
        val remoteConnection = remote
        if (remoteConnection != null) GatewayForm(null, remoteConnection,
            dismiss = { registering = false }, saved = { registering = false; refresh() }) else registering = false
    }
    replacing?.let { target ->
        val remoteConnection = remote
        if (remoteConnection != null) GatewayForm(target, remoteConnection,
            dismiss = { replacing = null }, saved = { replacing = null; refresh() })
    }
    deleting?.let { target -> AlertDialog(onDismissRequest = { deleting = null }, containerColor = Palette.surface,
        title = { Text("Delete ${target.name}?") },
        text = { Text("Playback that uses this gateway will stop until another gateway is configured or shared with your account.") },
        confirmButton = { TextButton({ remove(target.id); deleting = null }, enabled = !busy) { Text("Delete", color = Palette.danger) } },
        dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } }) }
    granting?.let { target ->
        GatewayAccessDialog(target, grants[target.id], busy, error, errorCode,
            { loadGrants(target.id) }, { grantAccess(target.id, it) }, { revokeAccess(target.id, it) },
            { granting = null })
    }
}

@Composable
private fun GatewayCard(gateway: Gateway, checked: GatewayCheck?, busy: Boolean,
    check: () -> Unit, setEnabled: (Boolean) -> Unit, replace: () -> Unit, remove: () -> Unit, grants: () -> Unit) {
    Surface(color = Palette.surface, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(gateway.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (gateway.can_manage) "Yours" else "Shared with your account", fontSize = 13.sp, color = Palette.secondary)
                }
                if (gateway.can_manage) Switch(gateway.enabled, setEnabled, enabled = !busy, modifier = Modifier)
            }
            Text(gateway.endpoint, fontSize = 13.sp, color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Namespace ${gateway.namespace} · priority ${gateway.priority} · ${if (gateway.enabled) "Enabled" else "Disabled"}", fontSize = 13.sp, color = Palette.secondary)
            checked?.let { Text(capacityLabel(it), fontSize = 13.sp, color = if (it.ready) Palette.body else Palette.secondary) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(check, enabled = !busy) { Text(if (checked == null) "Check capacity" else "Check again") }
                if (gateway.can_manage) {
                    TextButton(replace, enabled = !busy) { Text("Replace") }
                    TextButton(remove, enabled = !busy) { Text("Delete", color = Palette.danger) }
                    TextButton(grants, enabled = !busy) { Text("Access", color = Palette.secondary) }
                }
            }
        }
    }
}

@Composable
private fun GatewayAccessDialog(gateway: Gateway, grantees: List<Long>?, busy: Boolean, error: String?, errorCode: String?,
    load: () -> Unit, grant: (Long) -> Unit, revoke: (Long) -> Unit, dismiss: () -> Unit) {
    var accountInput by remember { mutableStateOf("") }
    var revoking by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(gateway.id) { load() }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, containerColor = Palette.surface,
        title = { Text("Access to ${gateway.name}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text("Grant access to another account ID. Revoked accounts lose this gateway at their next playback.", color = Palette.secondary)
            (gatewayCodeMessage(errorCode) ?: error)?.let { Text(it, color = Palette.danger) }
            val ids = grantees ?: emptyList()
            if (ids.isEmpty() && grantees != null) Text("No account has access yet.", color = Palette.secondary)
            ids.forEach { id ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Account $id", Modifier.weight(1f), fontSize = 14.sp, color = Palette.text)
                    TextButton({ revoking = id }, enabled = !busy) { Text("Revoke", color = Palette.danger) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(accountInput, { accountInput = it }, Modifier.weight(1f), enabled = !busy,
                    label = "Account ID",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Button({ accountInput.toLongOrNull()?.let(grant); accountInput = "" }, enabled = !busy && accountInput.toLongOrNull() != null) { Text("Grant") }
            }
        } },
        confirmButton = { TextButton(dismiss, enabled = !busy) { Text("Close") } })
    revoking?.let { id -> AlertDialog(onDismissRequest = { if (!busy) revoking = null }, containerColor = Palette.surface,
        title = { Text("Revoke access?") },
        text = { Text("Account $id will lose access to ${gateway.name}.") },
        confirmButton = { TextButton({ revoke(id); revoking = null }, enabled = !busy) { Text("Revoke", color = Palette.danger) } },
        dismissButton = { TextButton({ revoking = null }, enabled = !busy) { Text("Cancel") } }) }
}

internal fun capacityLabel(check: GatewayCheck): String = when {
    !check.ready -> "Not ready"
    check.available == null -> "Capacity unknown"
    else -> "Ready · ${check.available.inputs} inputs, ${check.available.outputs} outputs, ${check.available.viewers} viewers"
}

@Composable
private fun GatewayForm(existing: Gateway?, remote: RemoteConnection, dismiss: () -> Unit, saved: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var endpoint by remember { mutableStateOf(existing?.endpoint.orEmpty()) }
    var namespace by remember { mutableStateOf(existing?.namespace.orEmpty()) }
    var priority by remember { mutableStateOf(existing?.priority?.toString().orEmpty()) }
    var key by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val priorityValid = priority.isBlank() || priority.toIntOrNull() in 0..10000
    val valid = name.isNotBlank() && endpoint.isNotBlank() && namespace.isNotBlank() && key.isNotBlank() && priorityValid
    fun submit() {
        if (submitting || !valid) return
        submitting = true; failure = null
        scope.launch {
            try {
                val parsed = priority.toIntOrNull()
                if (existing == null) remote.registerGateway(name, endpoint, namespace, parsed, key)
                else remote.replaceGateway(existing.id, name, endpoint, namespace, parsed, key)
                saved()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (thrown: RemoteFailure) { failure = gatewayCodeMessage(thrown.code) ?: thrown.message }
            catch (_: Exception) { failure = "Could not save the gateway. Try again." }
            finally { submitting = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!submitting) dismiss() }, containerColor = Palette.surface,
        title = { Text(if (existing == null) "Register gateway" else "Replace ${existing.name}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
            AppTextField(name, { name = it }, Modifier.fillMaxWidth(), enabled = !submitting, label = "Name")
            AppTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), enabled = !submitting, label = "HTTPS endpoint",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            AppTextField(namespace, { namespace = it }, Modifier.fillMaxWidth(), enabled = !submitting, label = "Namespace")
            AppTextField(priority, { priority = it }, Modifier.fillMaxWidth(), enabled = !submitting, label = "Priority (0–10000)",
                isError = !priorityValid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (!priorityValid) Text("Use a priority from 0 to 10000.", color = Palette.danger, fontSize = 13.sp)
            AppTextField(key, { key = it }, Modifier.fillMaxWidth(), enabled = !submitting, label = "Integration key",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false))
            failure?.let { Text(it, color = Palette.danger) }
            if (submitting) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(::submit, enabled = valid && !submitting) { Text(if (existing == null) "Register" else "Replace") } },
        dismissButton = { TextButton(dismiss, enabled = !submitting) { Text("Cancel") } })
}