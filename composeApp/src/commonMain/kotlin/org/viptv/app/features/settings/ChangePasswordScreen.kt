package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.viptv.app.components.CredentialFields
import org.viptv.app.components.CredentialForm
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteFailure
import org.viptv.app.connection.changePassword
import org.viptv.app.connection.passwordIncorrectCode
import org.viptv.app.connection.validNewPassword

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
        CredentialForm(
            fields = CredentialFields.changePassword(current, currentError, next, confirmation, if (mismatch) passwordMismatchMessage else null),
            submitLabel = "Change password",
            valid = valid,
            busy = busy,
            error = failure,
            onChange = { index, value ->
                when (index) {
                    0 -> { current = value; currentError = null; failure = null }
                    1 -> { next = value; failure = null }
                    else -> { confirmation = value; failure = null }
                }
            },
            onSubmit = ::submit,
            showProgress = true,
            secondaryLabel = "Cancel",
            onSecondary = back,
        )
    }
}