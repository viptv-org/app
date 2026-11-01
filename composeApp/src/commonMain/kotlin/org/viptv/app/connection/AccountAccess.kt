package org.viptv.app.connection

import kotlinx.serialization.json.*

data class AccountAccess(val role: String, val manageAccount: Boolean) {
    val pairedDevice: Boolean get() = role == "device"
    val member: Boolean get() = role != "device" && !manageAccount
    val managesSources: Boolean get() = manageAccount
}

internal suspend fun RemoteConnection.accountAccess(): AccountAccess {
    val value = if (accountManagementEnabled) accountCall("/api/auth/me") else call("/api/auth/me").jsonObject
    val account = value["account"]?.jsonObject ?: throw RemoteFailure(502, "The server did not provide account information.")
    val role = value["role"]?.jsonPrimitive?.contentOrNull ?: account["role"]?.jsonPrimitive?.contentOrNull ?: "member"
    val manage = value["capabilities"]?.jsonObject?.get("manage_account")?.jsonPrimitive?.booleanOrNull
    return AccountAccess(role, manage ?: (role != "device"))
}