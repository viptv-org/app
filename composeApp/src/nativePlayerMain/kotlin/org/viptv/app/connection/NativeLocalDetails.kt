package org.viptv.app.connection

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.*

internal suspend fun NativeLocalConnection.fetchDetailPage(profile: String, card: Card,
    offset: Int, limit: Int, refresh: Boolean = false): DetailSnapshot {
    require(offset in 0..10_000 && limit in 1..128)
    val identity = card.parent_identity ?: card.identity
    if (identity.id.startsWith("simkl:")) throw LocalFailure("This title requires its metadata server. Reopen it from the server connection.")
    if (card.metadata_origin == null && !publicImdbLookup.matches(identity.id))
        throw LocalFailure("This saved title has no verified add-on identity. Find it again through your add-on.")

    val expected = json.encodeToString(card)
    try {
        if (!refresh) {
            val cached = withContext(Dispatchers.IO) {
                if (card.metadata_origin != null) engine.detailPageForOrigin(profile, identity.kind, identity.id, expected, offset.toUInt(), limit.toUInt())
                else engine.detailPageForProvider(profile, identity.kind, identity.id, "", offset.toUInt(), limit.toUInt())
            }
            if (cached != null) return withContext(Dispatchers.IO) { json.decodeFromString(cached) }
        }
        val plan = withContext(Dispatchers.IO) {
            if (card.metadata_origin != null) engine.planDetailForOrigin(profile, identity.kind, identity.id, expected)
            else engine.planDetail(profile, identity.kind, identity.id, "")
        }
        val operation = json.parseToJsonElement(plan).jsonObject
        val body = addonJson(client, operation.getValue("url").jsonPrimitive.content)
        return withContext(Dispatchers.IO) {
            json.decodeFromString(engine.projectDetailPage(operation.getValue("operation").jsonPrimitive.content,
                body, offset.toUInt(), limit.toUInt()))
        }
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) { throw LocalFailure("This title is unavailable from its enabled add-on, or its identity has changed.") }
}

private val publicImdbLookup = Regex("^tt[0-9]+(?::(?:0|[1-9][0-9]*):[1-9][0-9]*)?$")

/** Refresh opaque lookup assertions before sources/subtitles can use a cached SDK alias. */
internal suspend fun NativeLocalConnection.prepareMetadataCard(profile: String, card: Card): Card {
    if (card.metadata_origin == null && !publicImdbLookup.matches(card.identity.id))
        throw LocalFailure("This saved title has no verified add-on identity. Find it again through your add-on.")
    val origin = card.metadata_origin
    if (origin != null) {
        val metadataProviders = withContext(Dispatchers.IO) {
            val selected = json.decodeFromString<ProfileConfiguration>(engine.profile(profile))
            val connections = json.decodeFromString<List<IntegrationConnection>>(engine.addons())
            connections.filter { connection -> connection.enabled && selected.assignments.any {
                it.connection_id == connection.id && Capability.METADATA in it.enabled
            } }.map { it.id }
        }
        val publicLookup = publicImdbLookup.matches(card.identity.id)
        if (origin.connection_id !in metadataProviders && (!publicLookup || metadataProviders.isEmpty()))
            return card // Core still fences unproved private IDs and stream-only public profiles.
        val detail = fetchDetailPage(profile, card, 0, 24, refresh = !publicLookup)
        if (card.identity.episode == null && card.parent_identity == null) return detail.item
        // Core checked an explicit off-page episode against the complete body.
        return card.copy(metadata_origin = detail.item.metadata_origin, provider_id = detail.item.provider_id,
            parent_identity = card.parent_identity?.let { detail.item.identity })
    }
    if (!publicImdbLookup.matches(card.identity.id)) return card
    val hasMetadata = withContext(Dispatchers.IO) {
        json.decodeFromString<ProfileConfiguration>(engine.profile(profile)).assignments.any { Capability.METADATA in it.enabled }
    }
    if (!hasMetadata) return card
    val detail = fetchDetailPage(profile, card, 0, 24)
    return if (card.identity.episode == null && card.parent_identity == null) detail.item else {
        val upgraded = card.copy(metadata_origin = detail.item.metadata_origin, provider_id = detail.item.provider_id)
        fetchDetailPage(profile, upgraded, 0, 24) // Verifies every original episode/parent claim before adopting assertions.
        upgraded.copy(parent_identity = card.parent_identity?.let { detail.item.identity })
    }

}
