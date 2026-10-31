package org.viptv.app.connection

import org.viptv.engine.wire.IntegrationConnection
import org.viptv.engine.wire.CatalogPage
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.HistoryEntry
import org.viptv.engine.wire.HomeSnapshot

interface LocalConnection : AutoCloseable {
    suspend fun createProfile(name: String): Profile
    suspend fun renameProfile(id: String, name: String, pin: String)
    suspend fun setProfileAvatar(id: String, style: String, choice: Int, pin: String)
    suspend fun deleteProfile(id: String, pin: String)
    suspend fun setProfilePin(id: String, oldPin: String, newPin: String)
    suspend fun verifyProfilePin(id: String, pin: String): Boolean
    suspend fun bootstrap(profile: String? = null): Bootstrap
    suspend fun refreshHome(profile: String): HomeSnapshot
    suspend fun addons(): List<IntegrationConnection>
    suspend fun installAddon(profile: String, manifestUrl: String): List<IntegrationConnection>
    suspend fun commitAddonInstallation(profile: String, manifestUrl: String) { installAddon(profile, manifestUrl) }
    suspend fun search(profile: String, term: String): CatalogPage
    suspend fun searchResults(profile: String, term: String): SearchSnapshot? = null
    suspend fun detail(profile: String, card: Card, offset: Int = 0): DetailSnapshot
    suspend fun detailPage(profile: String, card: Card, offset: Int, limit: Int): DetailSnapshot = detail(profile, card, offset)
    suspend fun sources(profile: String, card: Card, publish: (List<SourceCandidate>) -> Unit = {}): SourcesOutcome
    suspend fun liveChannels(profile: String, query: LiveQuery): LiveCatalogPage = LiveCatalogPage(reason = "Live TV is unavailable on this device.")
    suspend fun liveFavorites(profile: String): Set<String> = throw UnsupportedOperationException("Channel favourites are unavailable on this device.")
    suspend fun setLiveFavorite(profile: String, channel: String, saved: Boolean): Set<String> = throw UnsupportedOperationException("Channel favourites are unavailable on this device.")
    suspend fun liveGuide(profile: String, channel: String): LiveGuideSnapshot = LiveGuideSnapshot(channel, reason = "The program guide is unavailable.")
    suspend fun liveSources(profile: String, channel: String): List<SourceCandidate> = throw LocalFailure("This live channel is unavailable.")
    suspend fun rankSources(profile: String, sources: List<SourceCandidate>, deviceMaxHeight: Int? = null): RankedSources = RankedSources(sources)
    suspend fun openSource(id: String): PlaybackInput
    suspend fun subtitles(profile: String, card: Card): org.viptv.engine.wire.SubtitleSnapshot
    suspend fun subtitleContent(id: String, input: PlaybackInput): ByteArray
    suspend fun enabledAddons(profile: String): Set<String>
    suspend fun setAddonEnabled(profile: String, id: String, enabled: Boolean)
    suspend fun removeAddon(id: String)
    suspend fun recordProgress(input: PlaybackInput, card: Card, positionMillis: Long, durationMillis: Long?)
    suspend fun history(profile: String): List<HistoryDisplay>
    suspend fun discoveryControls(profile: String, type: String): DiscoveryControls = throw LocalFailure("Browse is unavailable for this profile.")
    suspend fun browse(profile: String, query: BrowseQuery): CatalogPage = throw LocalFailure("Browse is unavailable for this profile.")
    suspend fun libraryPage(profile: String, query: org.viptv.engine.wire.LibraryQuery): org.viptv.engine.wire.LibrarySnapshot = throw LocalFailure("My List is unavailable on this device.")
    suspend fun library(profile: String): List<Card> = throw LocalFailure("My List is unavailable on this device.")
    suspend fun libraryMembership(profile: String): org.viptv.engine.wire.LibraryMembershipSnapshot? = null
    suspend fun saveFavorite(profile: String, card: Card): Unit = throw LocalFailure("My List is unavailable on this device.")
    suspend fun removeFavorite(profile: String, card: Card): Unit = throw LocalFailure("My List is unavailable on this device.")
    suspend fun browsingConfiguration(profile: String): BrowsingConfiguration? = null
    suspend fun profilePreferences(profile: String): PreferencesSnapshot = PreferencesSnapshot()
    suspend fun updateProfilePreferences(profile: String, revision: Long, preferences: ProfilePreferences): PreferencesSnapshot = throw LocalFailure("Profile preferences are unavailable on this device.")
    suspend fun sourceIntegrationSchemas(profile: String): SourceIntegrationSnapshot = SourceIntegrationSnapshot()
    suspend fun configureSourceIntegration(profile: String, revision: Long, plugin: String, configuration: kotlinx.serialization.json.JsonObject): Unit = throw LocalFailure("Source configuration is unavailable on this device.")
    suspend fun removeSourceIntegration(profile: String, revision: Long, connection: String): Unit = throw LocalFailure("Source configuration is unavailable on this device.")
    suspend fun clearHistory(profile: String)
}
expect fun localConnection(databasePath: String): LocalConnection

class LocalFailure(message: String) : Exception(message)

data class SourcesOutcome(val candidates: List<SourceCandidate> = emptyList(), val torrentOnlyProviders: Int = 0)

data class HistoryDisplay(
    val entry: HistoryEntry,
    val dayLabel: String,
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val watched: Boolean = entry.completed_at_millis != null,
    val resumeActive: Boolean = entry.completed_at_millis == null && (entry.card.progress ?: 0.0) > 0.0,
    val completionOnly: Boolean = false,
    val watchDateKnown: Boolean = true,
)

sealed interface HistoryCorrection {
    data object Watched : HistoryCorrection
    data object Unwatched : HistoryCorrection
    data class Position(val seconds: Long) : HistoryCorrection
}

data class ProgressCorrection(
    val position: Double,
    val duration: Double,
    val watched: Boolean,
    val resumeActive: Boolean,
    val completionOnly: Boolean,
    val watchDateKnown: Boolean,
)
