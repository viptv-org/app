package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class KidsPolicy(val enabled: Boolean = false, val max_age: Int = 12)

internal fun validParentPin(value: String): Boolean = value.length in 4..8 && value.all { it in '0'..'9' }

internal suspend fun RemoteConnection.saveParentPin(current: String, next: String) {
    require(next.isEmpty() || validParentPin(next)) { "PIN must contain 4 to 8 digits" }
    require(current.isEmpty() || validParentPin(current)) { "PIN must contain 4 to 8 digits" }
    call("/api/parent/pin", HttpMethod.Put, buildJsonObject {
        put("pin", next)
        if (current.isNotEmpty()) put("current_pin", current)
    })
}

internal suspend fun RemoteConnection.saveKidsPolicy(profile: String, policy: KidsPolicy) {
    require(policy.max_age in 0..17)
    call("/api/profiles/${profile.encodeURLPathPart()}/kids", HttpMethod.Put, buildJsonObject {
        put("enabled", policy.enabled); put("max_age", policy.max_age)
    })
}
