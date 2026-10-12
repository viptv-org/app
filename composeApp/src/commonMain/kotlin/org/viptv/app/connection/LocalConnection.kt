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
    suspend fun setProfileAvatar(id: String, avatarId: String, pin: String)
    suspend fun deleteProfile(id: String, pin: String)
    suspend fun setProfilePin(id: String, oldPin: String, newPin: String)
    suspend fun verifyProfilePin(id: String, pin: String): Boolean
    suspend fun bootstrap(profile: String? = null): Bootstrap
    suspend fun refreshHome(profile: String): HomeSnapshot
    suspend fun addons(): List<IntegrationConnection>
    suspend fun installAddon(profile: String, manifestUrl: String): List<IntegrationConnection>
    suspend fun search(profile: String, term: String): CatalogPage
    suspend fun detail(profile: String, card: Card, offset: Int = 0): DetailSnapshot
    suspend fun sources(profile: String, card: Card): List<SourceCandidate>
    suspend fun openSource(id: String): PlaybackInput
    suspend fun enabledAddons(profile: String): Set<String>
    suspend fun setAddonEnabled(profile: String, id: String, enabled: Boolean)
    suspend fun removeAddon(id: String)
    suspend fun recordProgress(input: PlaybackInput, card: Card, positionMillis: Long, durationMillis: Long?)
    suspend fun history(profile: String): List<HistoryDisplay>
    suspend fun clearHistory(profile: String)
}
expect fun localConnection(databasePath: String): LocalConnection

class LocalFailure(message: String) : Exception(message)

data class HistoryDisplay(val entry: HistoryEntry, val dayLabel: String)
