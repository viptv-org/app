package org.viptv.app.connection

import kotlinx.serialization.json.*
import org.viptv.engine.wire.*

private fun MediaIdentity.ordered() = copy(ids = ids.distinct().sortedWith(compareBy<ExternalId> { it.namespace }.thenBy { it.value }))

internal fun mediaLookupKey(identity: MediaIdentity, origin: MetadataOrigin?, provider: String? = null,
    parent: MediaIdentity? = null): String = buildJsonArray {
    add(Json.encodeToJsonElement(identity.ordered()))
    add(origin?.let { Json.encodeToJsonElement(it) } ?: JsonNull)
    add(provider?.let { JsonPrimitive(it) } ?: JsonNull)
    add(parent?.let { Json.encodeToJsonElement(it.ordered()) } ?: JsonNull)
}.toString()

internal fun cardLookupKey(card: Card): String = mediaLookupKey(card.identity, card.metadata_origin, card.provider_id, card.parent_identity)
internal fun libraryStatisticsKey(statistics: LibraryStatistics): String = mediaLookupKey(statistics.identity, statistics.metadata_origin)

internal fun sameLookupIdentity(first: MediaIdentity, second: MediaIdentity): Boolean = first.ordered() == second.ordered()

internal fun LibraryMembershipSnapshot.containsCard(card: Card): Boolean {
    val identity = card.parent_identity ?: card.identity
    val origin = card.metadata_origin
    if (origin != null && scoped_identities.any { it.metadata_origin == origin && sameLookupIdentity(it.identity, identity) }) return true
    // Reserved Simkl names from legacy Stremio data are not a public Simkl identity.
    val sharedAllowed = !identity.id.startsWith("simkl:") || (origin?.provider_id ?: card.provider_id) == "simkl"
    if (sharedAllowed && identities.any { it.kind == identity.kind && it.id == identity.id }) return true
    return origin == null && legacy_identities.any { it.kind == identity.kind && it.id == identity.id }
}

internal fun savedCardsContain(cards: List<Card>, candidate: Card): Boolean {
    val identity = candidate.parent_identity ?: candidate.identity
    return cards.any { it.metadata_origin == candidate.metadata_origin &&
        (it.metadata_origin != null || it.provider_id == candidate.provider_id) &&
        sameLookupIdentity(it.parent_identity ?: it.identity, identity) }
}

internal fun Card.lookupSelector(): Card = Card(identity = identity, provider_id = provider_id,
    title = title, badges = emptyList(), parent_identity = parent_identity, metadata_origin = metadata_origin)
