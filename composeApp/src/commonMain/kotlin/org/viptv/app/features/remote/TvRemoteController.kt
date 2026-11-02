package org.viptv.app.features.remote

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.viptv.engine.wire.VizioControllerOutput
import org.viptv.engine.wire.VizioControllerOutputKind
import org.viptv.engine.wire.VizioFailure
import org.viptv.engine.wire.VizioFailureKind
import org.viptv.engine.wire.VizioRemoteKey

internal const val remoteQueueLimit = 8

private val VizioFailureKind.offlining: Boolean
    get() = this == VizioFailureKind.TRANSPORT || this == VizioFailureKind.HTTPSTATUS || this == VizioFailureKind.AUTHENTICATION

@Stable
internal class TvRemoteController(
    private val engineFactory: () -> VizioRemoteEngine?,
    private val scope: CoroutineScope,
    private val receiverUrl: String,
) {
    var online by mutableStateOf(false); private set
    var checking by mutableStateOf(false); private set
    var launching by mutableStateOf(false); private set
    var pendingKeys by mutableIntStateOf(0); private set

    private val commands = Mutex()
    private var engine: VizioRemoteEngine? = null
    private var epoch = 0

    fun open() {
        releaseSession()
        epoch++
        pendingKeys = 0
        online = false
        checking = false
        launching = false
        engine = engineFactory()
        reachable()
    }

    fun close() {
        epoch++
        pendingKeys = 0
        online = false
        checking = false
        launching = false
        releaseSession()
    }

    fun retry() = reachable()

    fun key(key: VizioRemoteKey): Boolean {
        if (!online || checking || pendingKeys >= remoteQueueLimit) return false
        pendingKeys++
        val ticket = epoch
        scope.launch {
            try {
                val output = run(ticket, "key", buildJsonObject { put("key", key.name) }) ?: return@launch
                when (output.kind) {
                    VizioControllerOutputKind.COMPLETE -> Unit
                    VizioControllerOutputKind.ERROR -> handleFailure(output.error)
                    VizioControllerOutputKind.REQUEST -> Unit
                }
            } finally {
                if (ticket == epoch) pendingKeys--
            }
        }
        return true
    }

    fun launchOnTv() {
        if (!online || checking || launching) return
        launching = true
        val ticket = epoch
        scope.launch {
            try {
                val output = run(ticket, "launchConjure", buildJsonObject { put("url", receiverUrl) }) ?: return@launch
                when (output.kind) {
                    VizioControllerOutputKind.COMPLETE -> Unit
                    VizioControllerOutputKind.ERROR -> handleFailure(output.error)
                    VizioControllerOutputKind.REQUEST -> Unit
                }
            } finally {
                if (ticket == epoch) launching = false
            }
        }
    }

    private fun reachable() {
        if (checking) return
        checking = true
        val ticket = epoch
        scope.launch {
            val output = run(ticket, "pingAuth") ?: return@launch
            if (ticket != epoch) return@launch
            when (output.kind) {
                VizioControllerOutputKind.COMPLETE -> {
                    checking = false
                    online = true
                }
                else -> {
                    checking = false
                    online = false
                }
            }
        }
    }

    private fun handleFailure(failure: VizioFailure?) {
        if (failure?.kind?.offlining == true) online = false
    }

    private suspend fun run(ticket: Int, operation: String, input: JsonObject = JsonObject(emptyMap())): VizioControllerOutput? =
        commands.withLock {
            if (ticket != epoch) return@withLock null
            val active = engine ?: return@withLock null
            try {
                active.run(operation, input)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                VizioControllerOutput(
                    VizioControllerOutputKind.ERROR, null, null, null,
                    VizioFailure(VizioFailureKind.TRANSPORT, "TV is unreachable", true, null), false,
                )
            }
        }

    private fun releaseSession() {
        val active = engine ?: return
        engine = null
        scope.launch { commands.withLock { runCatching { active.close() } } }
    }
}