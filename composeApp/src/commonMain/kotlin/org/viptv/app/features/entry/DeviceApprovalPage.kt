package org.viptv.app.features.entry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.DeviceApproval
import org.viptv.app.connection.DeviceApprovalApi
import org.viptv.app.connection.RemoteFailure
import org.viptv.app.connection.ParentAuthorizationRequired
import org.viptv.app.features.settings.ParentPinDialog
import org.viptv.app.state.DeviceApprovalController
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.Button
import org.viptv.app.components.TextButton

private enum class DeviceApprovalStep { Restore, SignIn, Code, Confirm, Done, Denied }

/**
 * The browser side of the TV sign-in approval flow. Renders the W3Device
 * family: sign in if needed, enter the code shown on the TV, confirm the
 * device, then the all-set screen. Approval is always explicit; nothing
 * here loads Home, profiles, or catalog metadata.
 *
 * Design deviations from the W3 boards, tracked explicitly:
 * - Codes are 8 digits (backend contract), not the boards' letters.
 * - No profile picker: the TV chooses its profile after approval.
 */
@Composable
internal fun DeviceApprovalPage(
    compact: Boolean,
    controller: DeviceApprovalController,
    initialCode: String = "",
    goHome: () -> Unit = {},
) {
    var step by remember { mutableStateOf(DeviceApprovalStep.Restore) }
    var code by remember { mutableStateOf(initialCode.filter(Char::isDigit).take(8)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var parentAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var parentError by remember { mutableStateOf<String?>(null) }
    var device by remember { mutableStateOf<DeviceApproval?>(null) }

    // Account-only session restore: a browser that already holds a session
    // goes straight to code entry; no Home or metadata is ever requested.
    LaunchedEffect(controller) {
        controller.reset()
        controller.restore {
            step = if (controller.username == null) DeviceApprovalStep.SignIn else DeviceApprovalStep.Code
        }
    }

    fun message(failure: Throwable): String = when (failure) {
        is RemoteFailure -> DeviceApprovalApi.message(failure.status)
        else -> "Could not reach your server. Try again."
    }

    fun handleFailure(failure: Throwable, retry: () -> Unit) {
        if (failure is ParentAuthorizationRequired) { parentAction = retry; parentError = null; error = null }
        else error = message(failure)
    }

    fun lookup() {
        if (code.length != 8 || busy) return
        busy = true; error = null
        controller.lookup(code) { result ->
            busy = false
            result.fold(onSuccess = { device = it; step = DeviceApprovalStep.Confirm },
                onFailure = { handleFailure(it, ::lookup) })
        }
    }

    fun decide(approve: Boolean) {
        if (busy) return
        busy = true; error = null
        val done: (Result<Unit>) -> Unit = { result ->
            busy = false
            result.fold(onSuccess = { step = if (approve) DeviceApprovalStep.Done else DeviceApprovalStep.Denied },
                onFailure = { handleFailure(it) { decide(approve) } })
        }
        if (approve) controller.approve(code, done) else controller.deny(code, done)
    }

    fun signIn(username: String, password: String) {
        if (busy) return
        busy = true; error = null
        controller.signIn(username, password) { message ->
            busy = false
            if (message == null) step = DeviceApprovalStep.Code else error = message
        }
    }

    // "Not you?" from W3DeviceMobile: drop the browser session, return to sign-in.
    fun notYou() {
        if (busy) return
        busy = true; error = null
        controller.signOut(onFailure = { failure -> busy = false; handleFailure(failure, ::notYou) }) {
            busy = false
            code = ""
            step = DeviceApprovalStep.SignIn
        }
    }

    DeviceCard(compact) {
        when (step) {
            DeviceApprovalStep.Restore -> {
                DeviceTile()
                DeviceSubtitle("Checking your session...", compact)
            }
            DeviceApprovalStep.SignIn -> DeviceSignInCard(busy, error, ::signIn)
            DeviceApprovalStep.Code -> {
                DeviceTile()
                DeviceTitle("Enter the code on your TV", compact)
                DeviceSubtitle("You'll find it on your TV under Sign in.", compact)
                DeviceCodeInput(
                    code = code,
                    changed = { code = it; error = null },
                    submit = ::lookup,
                    error = error != null,
                    enabled = !busy,
                    compact = compact,
                )
                error?.let { DeviceError(it) }
                DevicePrimaryButton("Continue", code.length == 8 && !busy, busy, ::lookup)
                val signedInAs = controller.username
                if (compact && signedInAs != null) DeviceSessionFootnote(signedInAs, ::notYou)
                else TextButton(
                    onClick = { code = ""; error = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Palette.tertiary),
                ) { Text("Enter a different code", fontSize = 13.sp) }
            }
            DeviceApprovalStep.Confirm -> {
                val info = device
                if (info == null) DeviceTitle("Enter the code on your TV")
                else {
                    DeviceTitle("Sign in to this TV?", compact)
                    DeviceSubtitle("Only continue if the code matches the one on your TV.", compact)
                    DeviceConfirmCard(info, code)
                    DevicePrimaryButton("Sign in the TV", true, busy) { decide(true) }
                    DeviceSecondaryButton("Deny") { decide(false) }
                    error?.let { DeviceError(it) }
                }
            }
            DeviceApprovalStep.Done -> {
                DeviceCheckTile()
                DeviceTitle("Your TV is signed in", compact)
                DeviceSubtitle("The TV can now pick its profile.", compact)
                DevicePrimaryButton("Back to home", true, false, goHome)
            }
            DeviceApprovalStep.Denied -> {
                DeviceTitle("Denied", compact)
                DeviceSubtitle("This TV will not be signed in. You can close this page.", compact)
            }
        }
    }
    if (parentAction != null) ParentPinDialog("unlock", configured = true, busy = busy, failure = parentError,
        dismiss = { if (!busy) { parentAction = null; parentError = null } }, submit = { pin, _ ->
            busy = true; parentError = null
            controller.unlockParent(pin) { result ->
                busy = false
                result.fold(onSuccess = {
                    val retry = parentAction; parentAction = null; retry?.invoke()
                }, onFailure = { failure -> parentError = when {
                    failure is RemoteFailure && failure.status == 403 -> "That parent PIN was not accepted. Try again."
                    failure is RemoteFailure && failure.status == 429 -> "Too many PIN attempts. Wait before trying again."
                    else -> "Could not unlock parental controls. Try again."
                } })
            }
        })
}

@Composable
internal fun DeviceError(text: String) {
    Text(text, color = Palette.danger, fontSize = 13.sp, textAlign = TextAlign.Center)
}

/** Primary button: cream background, dark text (W3 token `button.primary`). */
@Composable
internal fun DevicePrimaryButton(label: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.text,
            contentColor = Color(0xFF111113),
            disabledContainerColor = Color(0x33F4F2EE),
            disabledContentColor = Color(0xFF111113),
        ),
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF111113), strokeWidth = 2.dp)
        else Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** Deny action: bare tertiary text link per W3DeviceConfirm. */
@Composable
internal fun DeviceSecondaryButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = Palette.tertiary),
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) { Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp) }
}

/** W3DeviceMobile footnote: "Signed in as {name} - Not you?". */
@Composable
private fun DeviceSessionFootnote(username: String, notYou: () -> Unit) {
    Row {
        Text("Signed in as $username - ", color = Palette.tertiary, fontSize = 13.sp)
        Text("Not you?", color = Palette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.pointerClickable(onClick = notYou))
    }
}

/** W3DeviceConfirm device card: TV glyph, name, status dot, and code. */
@Composable
internal fun DeviceConfirmCard(device: DeviceApproval, code: String) {
    Row(
        Modifier.fillMaxWidth().background(Color(0x0FFFFFFF), RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).background(Color(0x1AFFFFFF), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { TvGlyph(Modifier.size(24.dp), Palette.body) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(device.deviceName, color = Palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(code.chunked(4).joinToString("-"), color = Palette.secondary, fontSize = 13.sp)
        }
        // Status: green dot + chip when pending, plain when approved.
        val pending = device.status == "pending"
        Box(
            Modifier.background(if (pending) Color(0x2934C979) else Color(0x14FFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pending) Box(Modifier.size(8.dp).background(Color(0xFF34C979), RoundedCornerShape(4.dp)))
                if (pending) Spacer(Modifier.width(6.dp))
                Text(
                    if (pending) "Waiting for approval" else "Approved",
                    color = if (pending) Color(0xFF6ED598) else Palette.secondary,
                    fontSize = 12.sp, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun DeviceSignInCard(busy: Boolean, error: String?, signIn: (String, String) -> Unit) {
    DeviceTitle("Sign in to continue")
    DeviceSubtitle("Approve the TV with your VIPTV account.")
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    DeviceField(username, { username = it }, "Username", !busy)
    DeviceField(password, { password = it }, "Password", !busy, PasswordVisualTransformation(), KeyboardOptions(keyboardType = KeyboardType.Password))
    error?.let { DeviceError(it) }
    DevicePrimaryButton("Sign in", !busy && username.isNotBlank() && password.isNotBlank(), busy) {
        signIn(username.trim(), password)
    }
}

@Composable
private fun DeviceField(
    value: String,
    changed: (String) -> Unit,
    label: String,
    enabled: Boolean,
    transformation: androidx.compose.ui.text.input.VisualTransformation? = null,
    options: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value,
        changed,
        label = { Text(label, color = Palette.secondary) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = transformation ?: androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = options,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.text,
            unfocusedBorderColor = Color(0x24FFFFFF),
            focusedTextColor = Palette.text,
            unfocusedTextColor = Palette.body,
            focusedLabelColor = Palette.text,
            unfocusedLabelColor = Palette.secondary,
            cursorColor = Palette.text,
        ),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = Palette.text),
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
    )
}

/** W3DeviceDone check tile: cream circle with a drawn check. */
@Composable
internal fun DeviceCheckTile() {
    Box(
        Modifier.size(76.dp).background(Palette.text, RoundedCornerShape(38.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(34.dp)) {
            val w = size.width; val h = size.height
            val stroke = w / 10f
            val path = Path().apply {
                moveTo(w * 0.16f, h * 0.55f)
                lineTo(w * 0.42f, h * 0.80f)
                lineTo(w * 0.86f, h * 0.22f)
            }
            drawPath(path, Color(0xFF111113), style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
