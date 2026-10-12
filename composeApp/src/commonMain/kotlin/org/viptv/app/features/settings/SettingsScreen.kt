package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.IntegrationConnection

@Composable
fun SettingsScreen(local: Boolean, addons: List<IntegrationConnection>, busy: Boolean, error: String?, install: (String) -> Unit, profiles: () -> Unit, leave: () -> Unit) {
    var url by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineLarge) }
        item { Text("Add-ons", style = MaterialTheme.typography.titleLarge) }
        item { Text(if (local) "Add-ons find the movies, series and channels you watch on this device." else "Add-ons find the movies, series and channels you watch on this account.", color = Palette.secondary) }
        if (local) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(url, { url = it }, modifier = Modifier.weight(1f), label = { Text("Add-on URL") }, placeholder = { Text("https://addon.example/manifest.json") }, singleLine = true)
                    Button(onClick = { install(url) }, enabled = !busy && url.isNotBlank()) { Text("Install") }
                }
            }
            item { Text("Only install add-ons you trust.", color = Palette.secondary, style = MaterialTheme.typography.bodySmall) }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
        error?.let { item { Text(it, color = Palette.danger) } }
        if (addons.isNotEmpty()) item { Text("Installed", style = MaterialTheme.typography.titleMedium) }
        items(addons, key = { it.id }) { addon ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(addon.name, style = MaterialTheme.typography.titleMedium)
                Text(addon.capabilities.joinToString(" · ") { it.name.lowercase().replaceFirstChar(Char::uppercaseChar) }, color = Palette.secondary, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = Palette.raised)
            }
        }
        item { TextButton(onClick = profiles) { Text("Switch profile") } }
        item { TextButton(onClick = leave) { Text(if (local) "Change connection" else "Sign out") } }
    }
}
