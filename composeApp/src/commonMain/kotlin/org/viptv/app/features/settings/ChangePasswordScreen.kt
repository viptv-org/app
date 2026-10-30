package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.viptv.app.components.Button
import org.viptv.app.components.TextButton
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteFailure
import org.viptv.app.connection.changePassword
import org.viptv.app.connection.passwordIncorrectCode
import org.viptv.app.connection.validNewPassword
import org.viptv.app.theme.Palette

internal const val passwordIncorrectMessage = "That password is not correct."
internal const val passwordMismatchMessage = "Passwords do not match."
internal const val passwordRateLimitedMessage = "Too many attempts. Wait a minute and try again."
internal const val passwordFailedMessage = "Could not change your password. Try again."

@Composable
internal fun ChangePasswordScreen(remote: RemoteConnection, back: () -> Unit, changed: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var currentError by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val mismatch = confirmation.isNotEmpty() && confirmation != next
    val valid = current.isNotEmpty() && validNewPassword(next) && next == confirmation
    fun submit() {
        if (busy || !valid) return
        busy = true; currentError = null; failure = null
        scope.launch {
            try {
                remote.changePassword(current, next)
                changed()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (thrown: RemoteFailure) {
                if (thrown.code == passwordIncorrectCode) { currentError = passwordIncorrectMessage; current = "" }
                else if (thrown.status == 429) failure = passwordRateLimitedMessage
                else failure = passwordFailedMessage
            } catch (_: Exception) { failure = passwordFailedMessage }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Change password", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(current, { current = it; currentError = null; failure = null }, Modifier.fillMaxWidth(), enabled = !busy,
                label = { Text("Current password") }, singleLine = true, isError = currentError != null,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            currentError?.let { Text(it, color = Palette.danger) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(next, { next = it; failure = null }, Modifier.fillMaxWidth(), enabled = !busy,
                label = { Text("New password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Text("At least 12 characters.", fontSize = 13.sp, lineHeight = 17.sp, color = Palette.secondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(confirmation, { confirmation = it; failure = null }, Modifier.fillMaxWidth(), enabled = !busy,
                label = { Text("Confirm new password") }, singleLine = true, isError = mismatch,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            if (mismatch) Text(passwordMismatchMessage, color = Palette.danger)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        failure?.let { Text(it, color = Palette.danger) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(::submit, enabled = valid && !busy) { Text("Change password") }
            TextButton(back, enabled = !busy) { Text("Cancel") }
        }
    }
}
