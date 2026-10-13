package org.viptv.app.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.ProfileConfiguration
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.engine.wire.CatalogPage
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.HistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import uniffi.viptv_core.LocalEngine

internal suspend fun NativeLocalConnection.fetchLocalDiscoveryControls(profile: String, type: String): DiscoveryControls = withContext(Dispatchers.IO) {
        json.decodeFromString(engine.discoveryControls(profile, type))
    }

internal suspend fun NativeLocalConnection.fetchLocalBrowse(profile: String, query: BrowseQuery): CatalogPage {
        val plan = withContext(Dispatchers.IO) { json.parseToJsonElement(engine.planDiscover(profile, json.encodeToString(query))).jsonObject }
        val requests = plan.getValue("requests").jsonArray
        val results = fetchLocalCatalogRows(requests) { addonJson(client, it) }
        if (requests.isNotEmpty() && results.all { it.jsonObject["body"] == null || it.jsonObject["body"] == JsonNull })
            throw LocalFailure("Could not load this catalog. Check your connection and try again.")
        return withContext(Dispatchers.IO) { json.decodeFromString(engine.projectDiscover(plan.getValue("id").jsonPrimitive.content, results.toString())) }
    }
