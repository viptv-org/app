package org.viptv.app.connection

import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

internal suspend fun RemoteConnection.parentRestriction(failure: Throwable): Throwable {
    currentCoroutineContext().ensureActive()
    if (failure is RemoteFailure && failure.status == 403) {
        val parent = try {
            Json { ignoreUnknownKeys = true }.decodeFromJsonElement<ParentAccess>(call("/api/parent/status"))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        currentCoroutineContext().ensureActive()
        if (parent?.restricted == true && !parent.unlocked && parent.pin_configured) return ParentAuthorizationRequired()
    }
    return failure
}

internal class RemoteDeviceApproval(private val remote: RemoteConnection) {
    suspend fun lookup(code: String): DeviceApproval = guarded {
        DeviceApprovalApi.parse(remote.call("/api/auth/device/lookup", HttpMethod.Post, body(code)).jsonObject, code)
    }

    suspend fun approve(code: String) = guarded { remote.call("/api/auth/device/approve", HttpMethod.Post, body(code)); Unit }

    suspend fun deny(code: String) = guarded { remote.call("/api/auth/device/deny", HttpMethod.Post, body(code)); Unit }

    suspend fun unlock(pin: String) {
        if (!validParentPin(pin)) throw RemoteFailure(400, "Use 4 to 8 digits.")
        remote.unlockParent(pin)
    }

    private fun body(code: String) = buildJsonObject { put("user_code", code) }

    private suspend fun <T> guarded(block: suspend () -> T): T =
        try { block() } catch (failure: RemoteFailure) { throw remote.parentRestriction(failure) }

    companion object {
        fun message(failure: Throwable): String = when {
            failure is ParentAuthorizationRequired -> failure.message.orEmpty()
            failure is RemoteFailure && failure.status == 403 -> "This profile can’t add devices. Switch to a parent profile and try again."
            failure is RemoteFailure -> DeviceApprovalApi.message(failure.status)
            else -> "Could not reach your server. Try again."
        }
    }
}
