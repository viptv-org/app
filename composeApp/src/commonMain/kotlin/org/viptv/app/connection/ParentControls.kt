package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class RatingLabel(val label: String, val age: Int = 0)

@Serializable
data class KidsRatings(val movie: List<RatingLabel> = emptyList(), val series: List<RatingLabel> = emptyList())

@Serializable
data class KidsPolicy(val enabled: Boolean = false, val max_age: Int = 12, val movie_rating: String? = null,
    val series_rating: String? = null, val allow_unrated: Boolean = false, val ratings: KidsRatings = KidsRatings())

@Serializable
data class ProfilePinStatus(val availability: String = "Unsupported", val message: String? = null,
    val configured: Boolean = false)

data class ParentProfileControls(val policy: KidsPolicy = KidsPolicy(), val leavePin: Boolean? = null)

internal val ProfileManagement.profilePinAvailable: Boolean
    get() = capabilities.profile_pin.availability == "Available"

internal fun validParentPin(value: String): Boolean = value.length in 4..8 && value.all { it in '0'..'9' }

internal suspend fun RemoteConnection.saveParentPin(current: String, next: String) {
    require(next.isEmpty() || validParentPin(next)) { "PIN must contain 4 to 8 digits" }
    require(current.isEmpty() || validParentPin(current)) { "PIN must contain 4 to 8 digits" }
    call("/api/parent/pin", HttpMethod.Put, buildJsonObject {
        put("pin", next)
        if (current.isNotEmpty()) put("current_pin", current)
    })
}

internal suspend fun RemoteConnection.kidsPolicy(profile: String): KidsPolicy =
    Json { ignoreUnknownKeys = true }.decodeFromJsonElement(
        call("/api/profiles/${profile.encodeURLPathPart()}/kids"))

internal suspend fun RemoteConnection.saveKidsPolicy(profile: String, policy: KidsPolicy): KidsPolicy {
    require(policy.max_age in 0..17)
    return Json { ignoreUnknownKeys = true }.decodeFromJsonElement(call("/api/profiles/${profile.encodeURLPathPart()}/kids", HttpMethod.Put,
        buildJsonObject {
            put("enabled", policy.enabled)
            put("max_age", policy.max_age)
            put("movie_rating", policy.movie_rating ?: "")
            put("series_rating", policy.series_rating ?: "")
            put("allow_unrated", policy.allow_unrated)
        }))
}

internal suspend fun RemoteConnection.profilePin(profile: String): ProfilePinStatus =
    Json { ignoreUnknownKeys = true }.decodeFromJsonElement(
        call("/api/profiles/${profile.encodeURLPathPart()}/pin"))

internal suspend fun RemoteConnection.saveProfilePin(profile: String, current: String, next: String) {
    require(next.isEmpty() || validParentPin(next)) { "PIN must contain 4 to 8 digits" }
    require(current.isEmpty() || validParentPin(current)) { "PIN must contain 4 to 8 digits" }
    call("/api/profiles/${profile.encodeURLPathPart()}/pin", HttpMethod.Put, buildJsonObject {
        put("pin", next)
        if (current.isNotEmpty()) put("current_pin", current)
    })
}
