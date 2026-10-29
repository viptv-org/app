package org.viptv.app.connection

import kotlinx.serialization.json.*

internal suspend fun RemoteConnection.accountIdentity(): AccountIdentity {
    val value = if (accountManagementEnabled) accountCall("/api/auth/me") else call("/api/auth/me").jsonObject
    val account = value["account"]?.takeUnless { it is JsonNull }
        ?: throw RemoteFailure(502, "The server did not provide account information.")
    return Json { ignoreUnknownKeys = true }.decodeFromJsonElement(account)
}
