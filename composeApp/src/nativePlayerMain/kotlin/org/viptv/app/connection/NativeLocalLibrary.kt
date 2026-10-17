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

internal suspend fun NativeLocalConnection.fetchLocalLibrary(profile: String): List<Card> = withContext(Dispatchers.IO) {
        val cards = mutableListOf<Card>()
        var offset = 0u
        do {
            val page = json.decodeFromString<CatalogPage>(engine.library(profile, offset, 100u))
            cards += page.cards
            offset += page.cards.size.toUInt()
            if (page.complete || page.cards.isEmpty()) break
        } while (true)
        cards
    }

internal suspend fun NativeLocalConnection.fetchLocalSaveFavorite(profile: String, card: Card) {
    val prepared = prepareMetadataCard(profile, card)
    withContext(Dispatchers.IO) {
        val revision = json.decodeFromString<ProfileConfiguration>(engine.profile(profile)).scope.revision.toULong()
        engine.saveFavorite(profile, revision, json.encodeToString(prepared))
    }

}

internal suspend fun NativeLocalConnection.fetchLocalRemoveFavorite(profile: String, card: Card) = withContext(Dispatchers.IO) {
        val revision = json.decodeFromString<ProfileConfiguration>(engine.profile(profile)).scope.revision.toULong()
        if (card.metadata_origin != null) engine.removeFavoriteForOrigin(profile, revision, json.encodeToString(card))
        else engine.removeFavorite(profile, revision, card.identity.kind, card.identity.id)
    }
