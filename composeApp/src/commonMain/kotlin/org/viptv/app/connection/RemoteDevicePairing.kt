package org.viptv.app.connection

import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

data class DevicePairingDisplay(val verificationUri: String, val qrUri: String, val userCode: String, val remainingSeconds: Long) {
    override fun toString() = "DevicePairingDisplay(code=<redacted>, remainingSeconds=$remainingSeconds)"
}

/**
 * The server ended a pairing before approval, reported with the OAuth device-flow errors
 * (RFC 8628 section 3.5): `access_denied` when the account holder declined it, `expired_token`
 * when the code ran out. `authorization_pending` is never an ending.
 */
class DevicePairingEnded(val reason: Reason) : Exception(when (reason) {
    Reason.Denied -> "Sign-in was declined. Request a new code."
    Reason.Expired -> DEVICE_PAIRING_EXPIRED
}) {
    enum class Reason { Denied, Expired }
}

internal const val DEVICE_PAIRING_EXPIRED = "This sign-in code expired. Request a new code."

internal class DevicePairingChallenge(val display: DevicePairingDisplay, internal val secret: String, var intervalSeconds: Long) {
    override fun toString() = "DevicePairingChallenge(deviceCode=<redacted>)"
}

/** The server owns codes, approval and credentials; this adapter transports that contract. */
internal class RemoteDevicePairing(
    private val send: suspend (String, JsonObject) -> HttpResponse,
    private val adopt: suspend (JsonObject) -> Bootstrap,
) {
    suspend fun create(): DevicePairingChallenge {
        val response = send("/api/auth/device/code", buildJsonObject { put("device_name", deviceDisplayName()) })
        if (response.status != HttpStatusCode.OK) throw RemoteFailure(response.status.value, "Could not create a sign-in code. Try again.")
        val value = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        fun text(key: String) = value[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it.length <= 2048 }
            ?: throw RemoteFailure(502, "The server returned an invalid sign-in code.")
        fun https(value: String) = Url(value).also {
            if (it.protocol != URLProtocol.HTTPS || it.user != null || it.password != null || it.host.isBlank())
                throw RemoteFailure(502, "The server returned an invalid sign-in address.")
        }.toString()
        val expires = value["expires_in"]?.jsonPrimitive?.longOrNull?.takeIf { it in 1..1800 }
            ?: throw RemoteFailure(502, "The server returned an invalid code expiry.")
        val interval = value["interval"]?.jsonPrimitive?.longOrNull?.takeIf { it in 1..60 }
            ?: throw RemoteFailure(502, "The server returned an invalid polling interval.")
        val uri = https(text("verification_uri"))
        val complete = value["verification_uri_complete"]?.jsonPrimitive?.contentOrNull ?: uri
        val code = text("user_code").takeIf { it.length <= 32 && it.all { c -> c.isLetterOrDigit() || c == '-' } }
            ?: throw RemoteFailure(502, "The server returned an invalid sign-in code.")
        return DevicePairingChallenge(DevicePairingDisplay(uri, https(complete), code, expires), text("device_code"), interval)
    }

    suspend fun poll(challenge: DevicePairingChallenge): Bootstrap? {
        val response = send("/api/auth/device/token", buildJsonObject { put("device_code", challenge.secret) })
        val value = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        if (response.status == HttpStatusCode.OK) return adopt(value)
        val code = value["error_code"]?.jsonPrimitive?.contentOrNull ?: value["error"]?.jsonPrimitive?.contentOrNull
        if (response.status == HttpStatusCode.BadRequest) when (code) {
            "authorization_pending" -> return null
            "access_denied" -> throw DevicePairingEnded(DevicePairingEnded.Reason.Denied)
            "expired_token" -> throw DevicePairingEnded(DevicePairingEnded.Reason.Expired)
        }
        if (response.status == HttpStatusCode.TooManyRequests) { challenge.intervalSeconds = (challenge.intervalSeconds + 5).coerceAtMost(60); return null }
        throw RemoteFailure(response.status.value, "This sign-in code is no longer available. Request a new code.")
    }
}
