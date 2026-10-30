package org.viptv.app.features.settings

import org.viptv.engine.wire.Capability
import org.viptv.engine.wire.IntegrationConnection

internal enum class AddonProvides(val label: String, val capabilities: Set<Capability>) {
    CATALOGS("Catalogs", setOf(Capability.DISCOVERY, Capability.SEARCH)),
    STREAMS("Streams", setOf(Capability.SOURCES)),
    METADATA("Metadata", setOf(Capability.METADATA)),
    SUBTITLES("Subtitles", setOf(Capability.SUBTITLES)),
    LIVE("Live TV", setOf(Capability.LIVETV)),
    OTHER("Other", setOf(Capability.TRACKING, Capability.AUTHORIZATION, Capability.MAPPING));

    fun matches(addon: IntegrationConnection): Boolean = addon.capabilities.any { it in capabilities }
}

internal fun IntegrationConnection.provides(): List<AddonProvides> = AddonProvides.entries.filter { it.matches(this) }

internal fun IntegrationConnection.providesSummary(): String =
    provides().joinToString(" · ") { it.label }.ifEmpty { "No capabilities listed" }

internal data class AddonFilter(val query: String = "", val provides: AddonProvides? = null) {
    fun filtered(addons: List<IntegrationConnection>): List<IntegrationConnection> {
        val term = query.trim()
        val only = provides
        return addons.filter { addon ->
            (term.isEmpty() || addon.name.contains(term, ignoreCase = true)) && (only == null || only.matches(addon))
        }
    }
}

internal fun addonProvidesCounts(addons: List<IntegrationConnection>): Map<AddonProvides, Int> =
    AddonProvides.entries.mapNotNull { entry -> addons.count(entry::matches).takeIf { it > 0 }?.let { entry to it } }.toMap()
