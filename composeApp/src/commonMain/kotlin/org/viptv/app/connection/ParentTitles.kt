package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MetadataOrigin
import org.viptv.engine.wire.MediaIdentity

@Serializable
data class ParentTitleItem(val card: Card, val approval_supported: Boolean = false, val message: String? = null)
@Serializable
data class ParentTitleRow(val id: String, val title: String, val provider_id: String, val addon_id: Long? = null,
    val state: String = "Ready", val message: String? = null, val items: List<ParentTitleItem> = emptyList())
@Serializable
data class ParentTitleSearch(val rows: List<ParentTitleRow> = emptyList(), val complete: Boolean = true)
@Serializable
data class ApprovedTitle(val id: String, val type: String, val name: String? = null, val poster: String? = null,
    val metadata_provider_id: String? = null, val metadata_addon_id: Long? = null, val approval_supported: Boolean = false,
    val metadata_origin: MetadataOrigin? = null, val card: Card? = null)
@Serializable
private data class ApprovedTitlePage(val items: List<ApprovedTitle> = emptyList())

data class ParentTitlesState(val profile: String, val profileName: String, val query: String = "",
    val rows: List<ParentTitleRow> = emptyList(), val searched: Boolean = false,
    val approved: List<ApprovedTitle> = emptyList(), val loaded: Boolean = false,
    val nextOffset: Int? = null, val notice: String? = null) {
    fun isApproved(item: ParentTitleItem) = approved.any { saved ->
        if (saved.card != null) saved.card.metadata_origin == item.card.metadata_origin && sameLookupIdentity(saved.card.identity, item.card.identity)
        else saved.type == item.card.identity.kind && saved.id == item.card.identity.id &&
            (saved.metadata_origin == item.card.metadata_origin || saved.metadata_origin == null && saved.metadata_provider_id != null)
    }
}

internal suspend fun RemoteConnection.parentTitleSearch(profile: String, query: String): ParentTitleSearch =
    Json { ignoreUnknownKeys = true }.decodeFromJsonElement(call("/api/parent/search?query=${query.encodeURLParameter()}&profile_id=${profile.encodeURLParameter()}"))

internal suspend fun RemoteConnection.approvedTitles(profile: String, offset: Int = 0): List<ApprovedTitle> =
    Json { ignoreUnknownKeys = true }.decodeFromJsonElement<ApprovedTitlePage>(
        call("/api/profiles/${profile.encodeURLPathPart()}/approvals?offset=$offset")).items

internal suspend fun RemoteConnection.saveTitleApproval(profile: String, kind: String, id: String, approved: Boolean,
    provider: String? = null, addon: Long? = null, expected: Card? = null): ApprovedTitle? {
    val response = call("/api/profiles/${profile.encodeURLPathPart()}/approvals", HttpMethod.Post, buildJsonObject {
        put("type", kind); put("id", id); put("approved", approved)
        provider?.let { put("metadata_provider_id", it) }; addon?.let { put("metadata_addon_id", it) }
        expected?.metadata_origin?.let {
            put("metadata_origin", Json.encodeToJsonElement(it))
            put("card", Json.encodeToJsonElement(expected.lookupSelector()))
        }
    }).jsonObject
    val saved = response["card"]?.takeUnless { it is JsonNull }?.let {
        Json { ignoreUnknownKeys = true }.decodeFromJsonElement<Card>(it)
    } ?: return null
    return ApprovedTitle(saved.identity.id, saved.identity.kind, saved.title, saved.poster,
        metadata_provider_id = saved.metadata_origin?.provider_id, metadata_origin = saved.metadata_origin, card = saved)
}

internal fun approvedTitleKey(title: ApprovedTitle): String = title.card?.let(::cardLookupKey)
    ?: mediaLookupKey(MediaIdentity(title.id, title.type, emptyList()), title.metadata_origin,
        "${title.metadata_provider_id}:${title.metadata_addon_id}")
