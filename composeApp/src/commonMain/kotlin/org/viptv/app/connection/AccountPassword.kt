package org.viptv.app.connection

import kotlinx.serialization.json.*

internal const val passwordIncorrectCode = "password_incorrect"

internal suspend fun RemoteConnection.changePassword(currentPassword: String, newPassword: String) {
    credentialCall("/api/auth/password", buildJsonObject {
        put("current_password", currentPassword)
        put("new_password", newPassword)
    })
}
