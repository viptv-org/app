package org.viptv.app.connection

import kotlinx.serialization.json.*

data class AccountAccess(val role: String, val manageAccount: Boolean, val manageIntegrations: Boolean? = null) {
    val pairedDevice: Boolean get() = role == "device"
    val member: Boolean get() = role == "member"
    val managesSources: Boolean get() = role in setOf("owner", "member") &&
        (manageIntegrations ?: (role == "owner" && manageAccount))
}

internal suspend fun RemoteConnection.accountAccess(): AccountAccess {
    val value = identityCall()
    val account = value["account"]?.jsonObject ?: throw RemoteFailure(502, "The server did not provide account information.")
    val role = value["role"]?.jsonPrimitive?.contentOrNull ?: account["role"]?.jsonPrimitive?.contentOrNull ?: "member"
    val manage = value["capabilities"]?.jsonObject?.get("manage_account")?.jsonPrimitive?.booleanOrNull
    val integrations = value["capabilities"]?.jsonObject?.get("manage_integrations")?.jsonPrimitive?.booleanOrNull
    return AccountAccess(role, manage ?: (role != "device"), integrations)
}
