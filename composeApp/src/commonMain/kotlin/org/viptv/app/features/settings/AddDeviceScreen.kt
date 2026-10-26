package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.viptv.app.connection.*
import org.viptv.app.features.entry.*

private enum class AddDeviceStep { Code, Confirm, Done, Denied }

/**
 * Signs in another device (a TV, usually) from an already signed-in app: enter the code it shows,
 * check it, approve. This replaces sending the user to the server's web page, where they may not be
 * signed in. Approval is always explicit and goes through the account this app already holds.
 */
@Composable
internal fun AddDeviceScreen(remote: RemoteConnection, compact: Boolean, linkedCode: String? = null, linkConsumed: () -> Unit = {}) {
    val approval = remember(remote) { RemoteDeviceApproval(remote) }
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(AddDeviceStep.Code) }
    var code by remember { mutableStateOf("") }
    var device by remember { mutableStateOf<DeviceApproval?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var parentAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var parentError by remember { mutableStateOf<String?>(null) }

    fun perform(action: suspend () -> Unit, done: () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { action(); done() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: ParentAuthorizationRequired) { parentError = null; parentAction = { perform(action, done) } }
            catch (failure: Exception) { error = RemoteDeviceApproval.message(failure) }
            finally { busy = false }
        }
    }

    fun lookup() {
        if (code.length != 8) return
        perform({ device = approval.lookup(code) }) { step = AddDeviceStep.Confirm }
    }
    fun decide(approve: Boolean) = perform({ if (approve) approval.approve(code) else approval.deny(code) }) {
        step = if (approve) AddDeviceStep.Done else AddDeviceStep.Denied
    }
    fun startOver() { code = ""; device = null; error = null; step = AddDeviceStep.Code }

    // The TV's QR code opened the app with its code: skip typing and check it straight away.
    LaunchedEffect(linkedCode) {
        if (linkedCode != null) { code = linkedCode; device = null; error = null; step = AddDeviceStep.Code; linkConsumed(); lookup() }
    }

    org.viptv.app.components.TvFocusScroll(contentPadding = PaddingValues(vertical = if (compact) 16.dp else 32.dp)) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
        .padding(horizontal = 24.dp, vertical = if (compact) 16.dp else 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (step) {
            AddDeviceStep.Code -> {
                DeviceTile()
                DeviceTitle("Add a device", compact)
                DeviceSubtitle("Open VIPTV on the other device, choose Sign in, then enter the code it shows.", compact)
                DeviceCodeInput(code, { code = it; error = null }, ::lookup, error != null, !busy, compact)
                error?.let { DeviceError(it) }
                DevicePrimaryButton("Continue", code.length == 8 && !busy, busy, ::lookup)
            }
            AddDeviceStep.Confirm -> {
                device?.let { info ->
                    DeviceTitle("Sign in this device?", compact)
                    DeviceSubtitle("Only continue if the code matches the one on that device.", compact)
                    DeviceConfirmCard(info, code)
                    DevicePrimaryButton("Sign in this device", !busy, busy) { decide(true) }
                    DeviceSecondaryButton("Deny") { decide(false) }
                    error?.let { DeviceError(it) }
                }
            }
            AddDeviceStep.Done -> {
                DeviceCheckTile()
                DeviceTitle("Device signed in", compact)
                DeviceSubtitle("It can now pick its profile.", compact)
                DevicePrimaryButton("Add another device", true, false, ::startOver)
            }
            AddDeviceStep.Denied -> {
                DeviceTitle("Denied", compact)
                DeviceSubtitle("That device will not be signed in.", compact)
                DevicePrimaryButton("Add another device", true, false, ::startOver)
            }
        }
    } }
    if (parentAction != null) ParentPinDialog("unlock", configured = true, busy = busy, failure = parentError,
        dismiss = { if (!busy) { parentAction = null; parentError = null } }, submit = { pin, _ ->
            busy = true; parentError = null
            scope.launch {
                var unlocked = false
                try { approval.unlock(pin); unlocked = true }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    parentError = when {
                        failure is RemoteFailure && failure.status == 400 -> failure.message
                        failure is RemoteFailure && failure.status == 403 -> "That parent PIN was not accepted. Try again."
                        failure is RemoteFailure && failure.status == 429 -> "Too many PIN attempts. Wait before trying again."
                        else -> "Could not unlock parental controls. Try again."
                    }
                } finally { busy = false }
                // A successful PIN resumes the action the user already asked for.
                if (unlocked) { val retry = parentAction; parentAction = null; retry?.invoke() }
            }
        })
}
