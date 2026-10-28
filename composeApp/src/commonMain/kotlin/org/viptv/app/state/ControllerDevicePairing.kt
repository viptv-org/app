package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import kotlin.time.TimeSource

fun ApplicationController.cancelDevicePairing() {
    pairingGeneration++; pairingJob?.cancel(); pairingJob = null
    if (mutable.value.screen == Screen.Connect)
        mutable.value = mutable.value.copy(devicePairing = null, busy = false, error = null)
}

fun ApplicationController.startDevicePairing(origin: String) {
    cancelDevicePairing()
    this.generation++; job?.cancel()
    val generation = pairingGeneration
    mutable.value = mutable.value.copy(busy = true, error = null)
    pairingJob = scope.launch {
        var candidate: RemoteConnection? = null
        var adopted = false
        fun active() = pairingGeneration == generation && mutable.value.screen == Screen.Connect
        try {
            candidate = remoteFactory(origin, sessionStore)
            val transport = candidate.devicePairing(::active)
            val challenge = transport.create()
            val started = TimeSource.Monotonic.markNow()
            var nextPoll = challenge.intervalSeconds * 1000
            withTimeout(challenge.display.remainingSeconds * 1000) {
                while (active()) {
                    val elapsed = started.elapsedNow().inWholeMilliseconds
                    mutable.value = mutable.value.copy(devicePairing = challenge.display.copy(
                        remainingSeconds = (challenge.display.remainingSeconds - elapsed / 1000).coerceAtLeast(0)))
                    if (elapsed >= nextPoll) {
                        val result = transport.poll(challenge)
                        if (!active()) return@withTimeout
                        if (result != null) {
                            connection?.close(); connection = candidate; local?.close(); local = null
                            adopted = true; pairingJob = null; accept(result)
                            return@withTimeout
                        }
                        nextPoll = started.elapsedNow().inWholeMilliseconds + challenge.intervalSeconds * 1000
                    }
                    delay(1000)
                }
            }
        } catch (_: TimeoutCancellationException) {
            if (active()) mutable.value = mutable.value.copy(error = DEVICE_PAIRING_EXPIRED_MESSAGE)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (active()) mutable.value = mutable.value.copy(error = when (error) {
                is RemoteFailure, is DevicePairingEnded -> error.message
                else -> "Could not reach your server. Check the address and try again."
            })
        } finally {
            if (!adopted) candidate?.close()
            if (active()) mutable.value = mutable.value.copy(devicePairing = null, busy = false)
        }
    }
}
