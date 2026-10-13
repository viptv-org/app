package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class ProfileCapabilities(val create: FeatureAvailability, val rename: FeatureAvailability,
    val delete: FeatureAvailability, val avatar: FeatureAvailability, val profile_pin: FeatureAvailability,
    val parent_unlock: FeatureAvailability)
@Serializable
data class ParentAccess(val pin_configured: Boolean, val unlocked: Boolean, val restricted: Boolean)
@Serializable
data class ServerProfile(val id: String, val name: String, val avatar_id: String? = null,
    val locked: Boolean = false, val is_primary: Boolean = false, val can_delete: Boolean = true, val lock_kind: String? = null)
@Serializable
data class ProfileManagement(val profiles: List<ServerProfile>, val capabilities: ProfileCapabilities,
    val parent: ParentAccess, val avatar_ids: List<String> = emptyList()) {
    fun cards() = profiles.map { Profile(it.id, it.name, null, it.locked, it.avatar_id, it.can_delete, it.lock_kind) }
    val canManage get() = listOf(capabilities.create, capabilities.rename, capabilities.delete, capabilities.avatar)
        .any { it.availability == "Available" }
}

internal suspend fun RemoteConnection.profileManagement(): ProfileManagement =
    Json { ignoreUnknownKeys = true }.decodeFromJsonElement(call("/api/v3/profiles"))
internal suspend fun RemoteConnection.createProfile(name: String) {
    call("/api/v3/profiles", HttpMethod.Post, buildJsonObject { put("name", name.trim()) })
}
internal suspend fun RemoteConnection.renameProfile(id: String, name: String) {
    call("/api/v3/profiles/${id.encodeURLPathPart()}", HttpMethod.Patch, buildJsonObject { put("name", name.trim()) })
}
internal suspend fun RemoteConnection.setProfileAvatar(id: String, avatar: String) {
    call("/api/v3/profiles/${id.encodeURLPathPart()}", HttpMethod.Patch, buildJsonObject { put("avatar_id", avatar) })
}
internal suspend fun RemoteConnection.deleteProfile(id: String) {
    call("/api/v3/profiles/${id.encodeURLPathPart()}", HttpMethod.Delete)
}
internal suspend fun RemoteConnection.unlockParent(pin: String) {
    call("/api/v3/parent/unlock", HttpMethod.Post, buildJsonObject { put("pin", pin) })
}
