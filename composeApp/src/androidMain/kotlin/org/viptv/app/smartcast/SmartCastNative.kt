package org.viptv.app.smartcast

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.viptv.engine.wire.VizioDiscoveryCandidate
import org.viptv.engine.wire.VizioFailure
import org.viptv.engine.wire.VizioPlatformSupport
import org.viptv.engine.wire.VizioProtocolResponse
import org.viptv.engine.wire.VizioRequest
import uniffi.viptv_core.vizioDeviceinfoName
import uniffi.viptv_core.vizioDiscoveryCandidates
import uniffi.viptv_core.vizioPlatformSupport
import uniffi.viptv_core.vizioRequest
import uniffi.viptv_core.vizioResponse

internal val smartCastJson = Json { ignoreUnknownKeys = true }

sealed interface VizioCandidatesResult {
    data class Ok(val candidates: List<VizioDiscoveryCandidate>) : VizioCandidatesResult
    data class Err(val failure: VizioFailure) : VizioCandidatesResult
}

sealed interface VizioRequestResult {
    data class Ok(val request: VizioRequest) : VizioRequestResult
    data class Err(val failure: VizioFailure) : VizioRequestResult
}

sealed interface VizioResponseResult {
    data class Ok(val response: VizioProtocolResponse) : VizioResponseResult
    data class Err(val failure: VizioFailure) : VizioResponseResult
}

interface SmartCastNative {
    fun discoveryCandidates(subnet: String): VizioCandidatesResult
    fun deviceinfoName(body: String): String?
    fun platformSupport(platform: String): VizioPlatformSupport
    fun planRequest(operation: String, input: String): VizioRequestResult
    fun parseResponse(status: Int, body: String, allowStatusless: Boolean): VizioResponseResult
}

object NativeSmartCast : SmartCastNative {
    override fun discoveryCandidates(subnet: String): VizioCandidatesResult {
        val envelope = smartCastJson.parseToJsonElement(vizioDiscoveryCandidates(subnet)).jsonObject
        val ok = envelope["Ok"]
        if (ok != null) return VizioCandidatesResult.Ok(smartCastJson.decodeFromJsonElement(ok))
        return VizioCandidatesResult.Err(smartCastJson.decodeFromJsonElement(envelope.getValue("Err")))
    }

    override fun deviceinfoName(body: String): String? = vizioDeviceinfoName(body)

    override fun platformSupport(platform: String): VizioPlatformSupport =
        smartCastJson.decodeFromString(vizioPlatformSupport(platform))

    override fun planRequest(operation: String, input: String): VizioRequestResult {
        val envelope = smartCastJson.parseToJsonElement(vizioRequest(operation, input)).jsonObject
        val ok = envelope["Ok"]
        if (ok != null) return VizioRequestResult.Ok(smartCastJson.decodeFromJsonElement(ok))
        return VizioRequestResult.Err(smartCastJson.decodeFromJsonElement(envelope.getValue("Err")))
    }

    override fun parseResponse(status: Int, body: String, allowStatusless: Boolean): VizioResponseResult {
        val envelope = smartCastJson.parseToJsonElement(vizioResponse(status.toUShort(), body, allowStatusless)).jsonObject
        val ok = envelope["Ok"]
        if (ok != null) return VizioResponseResult.Ok(smartCastJson.decodeFromJsonElement(ok))
        return VizioResponseResult.Err(smartCastJson.decodeFromJsonElement(envelope.getValue("Err")))
    }
}