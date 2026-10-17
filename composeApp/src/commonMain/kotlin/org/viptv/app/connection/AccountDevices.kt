package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class AccountIdentity(val id: String, val username: String, val name: String, val role: String)
@Serializable
data class AccountDevice(val id: String, val name: String, val kind: String, val created_at: Long,
    val current: Boolean, val can_sign_out: Boolean)
@Serializable
data class AccountDevices(val account: AccountIdentity, val devices: List<AccountDevice>, val can_sign_out_all: Boolean)

private val accountCodec = Json { ignoreUnknownKeys = true }
internal suspend fun RemoteConnection.accountDevices(): AccountDevices {
    check(accountManagementEnabled) { "Manage devices through the server web dashboard." }
    return try { accountCodec.decodeFromJsonElement<AccountDevices>(call("/api/account/devices")) }
    catch (failure: RemoteFailure) {
        if (failure.status == 404) throw RemoteFailure(404, "Update the server to manage devices here.")
        throw failure
    }
}
internal suspend fun RemoteConnection.signOutDevice(id: String) {
    check(accountManagementEnabled) { "Manage devices through the server web dashboard." }
    require(id.isNotBlank() && id.length <= 128)
    call("/api/account/devices/${id.encodeURLPathPart()}", HttpMethod.Delete)
}
internal suspend fun RemoteConnection.signOutAllDevices() {
    check(accountManagementEnabled) { "Manage devices through the server web dashboard." }
    call("/api/account/devices", HttpMethod.Delete)
}
