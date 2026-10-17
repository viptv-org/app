package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.BrowsingConfiguration
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.*

@Composable
internal fun BrowsingProviderSettings(configuration: BrowsingConfiguration?, plugins: List<PluginDescriptor>, busy: Boolean,
    select: (String) -> Unit) {
    if (configuration == null) return
    val choices = (plugins.filter { Capability.DISCOVERY in it.capabilities }.map { it.id }
        + configuration.profile.browsing_plugin).distinct()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Metadata provider", style = MaterialTheme.typography.titleLarge)
        Text("This profile uses one provider for Home and browsing. Search uses all enabled search integrations. Stream and subtitle add-ons can work together.", color = Palette.secondary)
        choices.forEach { plugin ->
            val name = plugins.find { it.id == plugin }?.name ?: plugin.replaceFirstChar(Char::uppercaseChar)
            val selected = configuration.profile.browsing_plugin == plugin
            val available = selected || configuration.connections.any { it.plugin_id == plugin && it.enabled && Capability.DISCOVERY in it.capabilities }
            if (choices.size == 1) Text(name)
            else FilterChip(selected, { select(plugin) }, enabled = available && !busy, label = { Text(name) })
        }
    }
}
