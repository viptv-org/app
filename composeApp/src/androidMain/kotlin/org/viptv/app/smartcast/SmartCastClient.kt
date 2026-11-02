package org.viptv.app.smartcast

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.viptv.engine.wire.VizioControllerOutput
import org.viptv.engine.wire.VizioControllerOutputKind
import org.viptv.engine.wire.VizioRequest
import uniffi.viptv_core.SmartCastBridge as CoreSmartCastBridge

interface SmartCastDriver {
    fun start(operation: String, input: String): VizioControllerOutput
    fun resolve(requestId: UInt, status: Int, body: String): VizioControllerOutput
    fun reject(requestId: UInt): VizioControllerOutput
    fun cancel()
    fun credential(): String?
    fun clearCredential()
    fun close()
}

class NativeSmartCastDriver(config: String) : SmartCastDriver {
    private val bridge = CoreSmartCastBridge(config)

    override fun start(operation: String, input: String): VizioControllerOutput =
        smartCastJson.decodeFromString(bridge.start(operation, input))

    override fun resolve(requestId: UInt, status: Int, body: String): VizioControllerOutput =
        smartCastJson.decodeFromString(bridge.resolve(requestId, status.toUShort(), body))

    override fun reject(requestId: UInt): VizioControllerOutput =
        smartCastJson.decodeFromString(bridge.reject(requestId))

    override fun cancel() = bridge.cancel()

    override fun credential(): String? = bridge.credential()

    override fun clearCredential() = bridge.clearCredential()

    override fun close() = bridge.close()
}

class SmartCastClient(
    private val tvOrigin: String,
    private val transport: suspend (VizioRequest) -> SmartCastHttpResponse,
    private val driver: SmartCastDriver,
    private val tokenStore: SmartCastCredentialStore,
) {
    suspend fun run(operation: String, input: JsonObject = JsonObject(emptyMap())): VizioControllerOutput {
        var output = withContext(Dispatchers.IO) { driver.start(operation, input.toString()) }
        while (true) {
            when (output.kind) {
                VizioControllerOutputKind.REQUEST -> {
                    val requestId = checkNotNull(output.requestId).toUInt()
                    val request = checkNotNull(output.request)
                    output = try {
                        val response = transport(request)
                        withContext(Dispatchers.IO) { driver.resolve(requestId, response.status, response.body) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        withContext(Dispatchers.IO) { driver.reject(requestId) }
                    }
                }
                VizioControllerOutputKind.COMPLETE -> {
                    if (output.credentialChanged) driver.credential()?.let { tokenStore.save(tvOrigin, it) }
                    return output
                }
                VizioControllerOutputKind.ERROR -> return output
            }
        }
    }

    fun forget() {
        driver.cancel()
        driver.clearCredential()
        tokenStore.clear(tvOrigin)
    }

    fun close() {
        driver.cancel()
        driver.close()
    }

    companion object {
        fun create(
            tvOrigin: String,
            deviceId: String,
            deviceName: String,
            tokenStore: SmartCastCredentialStore,
            timeoutMillis: Long = 5_000,
        ): SmartCastClient {
            val config = buildJsonObject {
                put("host", tvOrigin)
                put("deviceId", deviceId)
                put("deviceName", deviceName)
                put("timeoutMillis", timeoutMillis)
                tokenStore.load(tvOrigin)?.let { put("authToken", it) }
            }.toString()
            return SmartCastClient(tvOrigin, SmartCastTransport(tvOrigin)::execute, NativeSmartCastDriver(config), tokenStore)
        }
    }
}