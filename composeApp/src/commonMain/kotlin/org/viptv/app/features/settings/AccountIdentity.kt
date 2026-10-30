package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TextButton
import org.viptv.app.connection.AccountIdentity
import org.viptv.app.theme.Palette

@Composable
internal fun AccountIdentityScreen(identity: AccountIdentity?, busy: Boolean, error: String?, load: () -> Unit,
    notice: String? = null, content: (@Composable () -> Unit)? = null) {
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Account", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        if (identity != null) {
            Column(Modifier.fillMaxWidth()) {
                IdentityRow("Name", identity.name)
                IdentityRow("Username", "@${identity.username}")
                IdentityRow("Role", if (identity.role == "owner") "Owner" else "Member")
            }
        } else Text(if (busy) "Loading your account…" else "Could not load your account.", color = Palette.secondary)
        notice?.let { Text(it, color = Palette.secondary) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = Palette.danger) }
        if (identity == null || error != null) TextButton(load, enabled = !busy) { Text("Retry account") }
        content?.invoke()
    }
}

@Composable
private fun IdentityRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 14.sp, lineHeight = 20.sp, color = Palette.secondary)
    }
}
