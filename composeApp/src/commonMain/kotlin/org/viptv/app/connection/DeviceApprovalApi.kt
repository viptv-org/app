package org.viptv.app.connection

import kotlinx.serialization.json.*

internal class ParentAuthorizationRequired : Exception("Enter the household parent PIN to continue.")

data class DeviceApproval(val code: String, val deviceName: String, val status: String, val expiresAt: Long) {
    override fun toString() = "DeviceApproval(status=$status)"
}

/**
 * The browser side of the TV sign-in flow: account-only authentication,
 * then explicit approve or deny for a pairing code the TV generated.
 * The transport deliberately avoids Home/metadata bootstrap calls.
 */
internal object DeviceApprovalApi {
    fun message(status: Int): String = when (status) {
        400 -> "That code isn't valid or has expired. Check your TV and try again."
        401 -> "Sign in to continue."
        403 -> "This code is already approved by another account."
        429 -> "Too many requests. Please wait a moment and try again."
        else -> "The server could not complete this request."
    }

    fun parse(value: JsonObject, code: String): DeviceApproval {
        val device = value["device"]?.jsonObject ?: throw RemoteFailure(502, "The server returned an invalid device.")
        val name = device["device_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw RemoteFailure(502, "The server returned an invalid device name.")
        val status = device["status"]?.jsonPrimitive?.contentOrNull
        if (status != "pending" && status != "approved") throw RemoteFailure(502, "The server returned an invalid device status.")
        val expires = value["expires_at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: throw RemoteFailure(502, "The server returned an invalid expiry.")
        return DeviceApproval(code, name, status, expires)
    }
}
