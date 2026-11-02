// Generated from the Rust SmartCast contracts.
package org.viptv.engine.wire

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class VizioControllerOutputKind {
    @SerialName("request") REQUEST,
    @SerialName("complete") COMPLETE,
    @SerialName("error") ERROR,
}

@Serializable
enum class VizioFailureKind {
    @SerialName("invalidConfig") INVALIDCONFIG,
    @SerialName("invalidInput") INVALIDINPUT,
    @SerialName("authentication") AUTHENTICATION,
    @SerialName("invalidParameter") INVALIDPARAMETER,
    @SerialName("endpointNotFound") ENDPOINTNOTFOUND,
    @SerialName("busy") BUSY,
    @SerialName("transport") TRANSPORT,
    @SerialName("invalidResponse") INVALIDRESPONSE,
    @SerialName("httpStatus") HTTPSTATUS,
}

@Serializable
enum class VizioHttpMethod {
    @SerialName("GET") GET,
    @SerialName("PUT") PUT,
}

@Serializable
enum class VizioRemoteAction {
    @SerialName("KEYPRESS") KEYPRESS,
    @SerialName("KEYDOWN") KEYDOWN,
    @SerialName("KEYUP") KEYUP,
}

@Serializable
enum class VizioRemoteKey {
    @SerialName("SEEK_FWD") SEEK_FWD,
    @SerialName("SEEK_BACK") SEEK_BACK,
    @SerialName("PAUSE") PAUSE,
    @SerialName("PLAY") PLAY,
    @SerialName("DOWN") DOWN,
    @SerialName("LEFT") LEFT,
    @SerialName("OK") OK,
    @SerialName("RIGHT") RIGHT,
    @SerialName("UP") UP,
    @SerialName("BACK") BACK,
    @SerialName("SMARTCAST") SMARTCAST,
    @SerialName("CC_TOGGLE") CC_TOGGLE,
    @SerialName("INFO") INFO,
    @SerialName("MENU") MENU,
    @SerialName("HOME") HOME,
    @SerialName("VOL_DOWN") VOL_DOWN,
    @SerialName("VOL_UP") VOL_UP,
    @SerialName("MUTE_OFF") MUTE_OFF,
    @SerialName("MUTE_ON") MUTE_ON,
    @SerialName("MUTE_TOGGLE") MUTE_TOGGLE,
    @SerialName("PIC_MODE") PIC_MODE,
    @SerialName("PIC_SIZE") PIC_SIZE,
    @SerialName("INPUT_NEXT") INPUT_NEXT,
    @SerialName("CH_DOWN") CH_DOWN,
    @SerialName("CH_UP") CH_UP,
    @SerialName("CH_PREV") CH_PREV,
    @SerialName("EXIT") EXIT,
    @SerialName("POW_OFF") POW_OFF,
    @SerialName("POW_ON") POW_ON,
    @SerialName("POW_TOGGLE") POW_TOGGLE,
}

@Serializable
enum class VizioTransportSupport {
    @SerialName("native") NATIVE,
    @SerialName("unavailable") UNAVAILABLE,
}

@Serializable
@Immutable
data class VizioAppConfig(
    val `appId`: String,
    val `nameSpace`: Int,
    val `message`: String? = null,
)

@Serializable
@Immutable
data class VizioControllerOutput(
    val `kind`: VizioControllerOutputKind,
    val `requestId`: Long? = null,
    val `request`: VizioRequest? = null,
    val `result`: Map<String, JsonElement>? = null,
    val `error`: VizioFailure? = null,
    val `credentialChanged`: Boolean,
)

@Serializable
@Immutable
data class VizioDiscoveryCandidate(
    val `host`: String,
    val `port`: Int,
)

@Serializable
@Immutable
data class VizioFailure(
    val `kind`: VizioFailureKind,
    val `message`: String,
    val `retryable`: Boolean,
    val `protocolStatus`: String? = null,
)

@Serializable
@Immutable
data class VizioInputInfo(
    val `cname`: String,
    val `name`: String,
    val `metaName`: String,
    val `current`: Boolean,
    val `hashValue`: Long? = null,
)

@Serializable
@Immutable
data class VizioPairingChallenge(
    val `challengeType`: Int,
    val `token`: Long,
)

@Serializable
@Immutable
data class VizioPlatformSupport(
    val `protocolAvailable`: Boolean,
    val `transport`: VizioTransportSupport,
    val `reason`: String,
)

@Serializable
@Immutable
data class VizioProtocolResponse(
    val `status`: String,
    val `detail`: String,
    val `raw`: Map<String, JsonElement> = emptyMap(),
)

@Serializable
@Immutable
data class VizioRemoteEvent(
    val `codeSet`: Int,
    val `code`: Int,
    val `action`: VizioRemoteAction,
)

@Serializable
@Immutable
data class VizioRequest(
    val `method`: VizioHttpMethod,
    val `url`: String,
    val `headers`: Map<String, String> = emptyMap(),
    val `body`: Map<String, JsonElement>? = null,
    val `timeoutMillis`: Long,
    val `maxResponseBytes`: Long,
)