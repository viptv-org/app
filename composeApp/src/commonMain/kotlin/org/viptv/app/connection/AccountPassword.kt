package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.json.*

internal const val passwordIncorrectCode = "password_incorrect"

// The route answers 405 to a GET; only a 404 means the server predates it.
internal suspend fun RemoteConnection.passwordChangeAvailable(): Boolean = try {
    call("/api/auth/password")
    true
} catch (failure: RemoteFailure) { failure.status != 404 }

internal suspend fun RemoteConnection.changePassword(currentPassword: String, newPassword: String) {
    check(accountManagementEnabled) { "Manage this from a signed-in phone, computer or the web dashboard." }
    accountCall("/api/auth/password", HttpMethod.Post, buildJsonObject {
        put("current_password", currentPassword)
        put("new_password", newPassword)
    })
}