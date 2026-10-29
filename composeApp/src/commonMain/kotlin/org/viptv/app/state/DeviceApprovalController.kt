package org.viptv.app.state

import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*

internal class DeviceApprovalController(
    private val scope: CoroutineScope,
    private val connectionFactory: () -> RemoteConnection = {
        RemoteConnection(browserOrigin() ?: error("This page requires the browser build."), browserMode = true)
    },
) {
    private var job: Job? = null
    private var connection: RemoteConnection? = null
    private var retiring: RemoteConnection? = null
    var username: String? = null
        private set

    private suspend fun remote(): RemoteConnection {
        retiring?.retire()
        retiring = null
        return connection ?: connectionFactory().also { connection = it }
    }

    private suspend fun post(path: String, body: JsonObject): JsonObject {
        try {
            val value = remote().accountCall(path, HttpMethod.Post, body)
            currentCoroutineContext().ensureActive()
            return value
        } catch (failure: RemoteFailure) {
            throw parentFailure(failure)
        }
    }

    fun isBusy(): Boolean = job?.isActive == true

    fun restore(onDone: () -> Unit) {
        if (isBusy()) return
        job = scope.launch {
            try {
                val value = remote().accountCall("/api/auth/status")
                currentCoroutineContext().ensureActive()
                if (value["authenticated"]?.jsonPrimitive?.booleanOrNull == true) {
                    val me = remote().accountCall("/api/auth/me")
                    currentCoroutineContext().ensureActive()
                    val name = me["account"]?.jsonObject?.get("username")?.jsonPrimitive?.contentOrNull
                    if (name != null) username = name
                } else username = null
                currentCoroutineContext().ensureActive()
                onDone()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { currentCoroutineContext().ensureActive(); onDone() }
        }
    }

    fun reset() {
        job?.cancel(); job = null
        connection?.let { retiring = it; it.close() }
        connection = null
        username = null
    }

    fun signIn(username: String, password: String, onDone: (String?) -> Unit) {
        if (isBusy()) return
        job = scope.launch {
            try {
                post("/api/auth/login", buildJsonObject {
                    put("username", username.trim())
                    put("password", password)
                })
                this@DeviceApprovalController.username = username
                onDone(null)
            } catch (error: CancellationException) { throw error }
            catch (error: RemoteFailure) {
                currentCoroutineContext().ensureActive()
                onDone(if (error.status == 401) "That username and password didn't match. Try again."
                else DeviceApprovalApi.message(error.status))
            }
            catch (_: Exception) { currentCoroutineContext().ensureActive(); onDone("Could not reach your server. Try again.") }
        }
    }

    fun lookup(code: String, onResult: (Result<DeviceApproval>) -> Unit) {
        if (isBusy()) return
        job = scope.launch {
            try {
                val device = DeviceApprovalApi.parse(post("/api/auth/device/lookup", buildJsonObject { put("user_code", code) }), code)
                onResult(Result.success(device))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { currentCoroutineContext().ensureActive(); onResult(Result.failure(error)) }
        }
    }

    fun approve(code: String, onResult: (Result<Unit>) -> Unit) {
        if (isBusy()) return
        job = scope.launch {
            try {
                post("/api/auth/device/approve", buildJsonObject { put("user_code", code) })
                onResult(Result.success(Unit))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { currentCoroutineContext().ensureActive(); onResult(Result.failure(error)) }
        }
    }

    fun deny(code: String, onResult: (Result<Unit>) -> Unit) {
        if (isBusy()) return
        job = scope.launch {
            try {
                post("/api/auth/device/deny", buildJsonObject { put("user_code", code) })
                onResult(Result.success(Unit))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { currentCoroutineContext().ensureActive(); onResult(Result.failure(error)) }
        }
    }

    private suspend fun parentFailure(failure: Throwable): Throwable = remote().parentRestriction(failure)

    fun unlockParent(pin: String, onResult: (Result<Unit>) -> Unit) {
        if (isBusy()) return
        job = scope.launch {
            val result = try {
                if (!validParentPin(pin)) throw RemoteFailure(400, "Use 4 to 8 digits.")
                remote().unlockParent(pin)
                Result.success(Unit)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { Result.failure(failure) }
            currentCoroutineContext().ensureActive()
            job = null
            onResult(result)
        }
    }

    fun signOut(onFailure: (Throwable) -> Unit = {}, onDone: () -> Unit) {
        if (isBusy()) return
        job = scope.launch {
            try {
                remote().signOut()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val cause = parentFailure(failure)
                currentCoroutineContext().ensureActive()
                onFailure(cause)
                return@launch
            }
            currentCoroutineContext().ensureActive()
            username = null
            onDone()
        }
    }
}
