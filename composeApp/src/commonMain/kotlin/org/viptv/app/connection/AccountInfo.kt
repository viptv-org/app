package org.viptv.app.connection

import kotlinx.serialization.json.*

internal suspend fun RemoteConnection.accountIdentity(): AccountIdentity {
    val value = if (browserSession) accountCall("/api/auth/me") else call("/api/auth/me").jsonObject
    val account = value["account"]?.takeUnless { it is JsonNull }
        ?: throw RemoteFailure(502, "The server did not provide account information.")
    val identity = Json { ignoreUnknownKeys = true }.decodeFromJsonElement<AccountIdentity>(account)
    recordAccountAccess(value["capabilities"]?.jsonObject?.get("manage_account")?.jsonPrimitive?.booleanOrNull,
        owner = identity.role == "owner")
    return identity
}
