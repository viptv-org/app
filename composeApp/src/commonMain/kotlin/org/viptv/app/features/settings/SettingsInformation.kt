package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.openAccountAuthorization
import org.viptv.app.resources.Res
import org.viptv.app.theme.Palette
import org.viptv.app.components.OutlinedButton
import org.viptv.app.components.TextButton

@Composable
internal fun SettingsInformation(section: String, local: Boolean, address: String?, profileName: String?,
    profiles: () -> Unit, changeConnection: () -> Unit) {
    var license by remember { mutableStateOf<String?>(null) }
    org.viptv.app.components.TvFocusScroll(horizontal = false, contentPadding = androidx.compose.foundation.layout.PaddingValues(32.dp)) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(if (section == "About") "About VIPTV" else section, style = MaterialTheme.typography.headlineMedium)
        when (section) {
            "General" -> {
                Text(if (local) "Using this device locally" else "Connected to a server", style = MaterialTheme.typography.titleLarge)
                if (local) Text("Profiles, provider connections and viewing progress stay on this device.", color = Palette.secondary)
                else address?.let { SelectionContainer { Text(it, color = Palette.secondary) } }
                profileName?.let { Text("Current profile · $it", color = Palette.secondary) }
                OutlinedButton(profiles) { Text("Manage profiles") }
                TextButton(changeConnection) { Text(if (local) "Change connection" else "Sign out") }
            }
            "Watch on TV" -> {
                if (local || address == null) {
                    Text("TV sign-in uses a server account. Sign in to the same server on both devices to share profiles and progress.", color = Palette.secondary)
                    OutlinedButton(changeConnection) { Text("Change connection") }
                } else {
                    Text("On your TV, choose Sign in and Continue in browser. Open the page below on your phone or computer and enter the code shown on the TV.", color = Palette.secondary)
                    ServerWebAction("Open TV sign-in", address + "device")
                }
            }
            "Help" -> {
                HelpTopic("No titles or sources", "Enable the right integrations for your selected profile. Browsing and search integrations supply titles; stream integrations supply playable sources.")
                HelpTopic("A source will not play", "Choose another available source. Source availability can change independently of VIPTV. Your viewing position is saved while you watch.")
                HelpTopic("Watch on another device", "Sign in to the same server and select the same profile. Local mode keeps its library and progress on this device.")
                HelpTopic("Recover account access", "Use your username and saved recovery code on the server’s web sign-in page. Recovery changes your password, replaces the recovery code and signs out other sessions.")
                if (!local && address != null) ServerWebAction("Open server dashboard", address)
            }
            "About" -> {
                Text("VIPTV", style = MaterialTheme.typography.titleLarge)
                Text("Experimental build", color = Palette.secondary)
                Text("Native clients can use local providers or connect to a server. Browsers use a server account.", color = Palette.secondary)
                Text("Application and font notices", style = MaterialTheme.typography.titleMedium)
                OutlinedButton({ license = "application" }) { Text("Application license") }
                TextButton({ license = "onest" }) { Text("Onest font license") }
                TextButton({ license = "bricolage-grotesque" }) { Text("Bricolage Grotesque font license") }
            }
        }
    } }
    license?.let { key -> LicenseNotice(key) { license = null } }
}

@Composable
private fun HelpTopic(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, color = Palette.secondary)
    }
}

@Composable
internal fun ServerWebAction(label: String, address: String) {
    var failed by remember(address) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SelectionContainer { Text(address, color = Palette.secondary) }
        OutlinedButton({ try { openAccountAuthorization(address); failed = false } catch (_: Exception) { failed = true } }) { Text(label) }
        if (failed) Text("Open this address in a browser on your phone or computer.", color = Palette.secondary)
    }
}

@Composable
private fun LicenseNotice(key: String, dismiss: () -> Unit) {
    var text by remember(key) { mutableStateOf<String?>(null) }
    var failed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { try { text = Res.readBytes("files/licenses/$key.txt").decodeToString() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true } }
    AlertDialog(onDismissRequest = dismiss, title = { Text("License notice") },
        text = { Box(Modifier.heightIn(max = 500.dp)) {
            if (text != null) SelectionContainer { Text(requireNotNull(text), Modifier.verticalScroll(rememberScrollState())) }
            else if (failed) Text("The packaged notice could not be opened.", color = Palette.danger)
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        } }, confirmButton = { TextButton(dismiss) { Text("Close") } })
}
