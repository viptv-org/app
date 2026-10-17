package org.viptv.app.connection

import kotlinx.serialization.json.*

/** One-time account recovery material. Intentionally not serializable or logged. */
class RecoveryKit internal constructor(val codes: List<String>, val recovered: Boolean) {
    override fun toString() = "RecoveryKit(redacted)"
}

internal fun recoveryKit(value: JsonObject, recovered: Boolean): RecoveryKit {
    val codes = value["recovery_codes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
        ?: listOfNotNull(value["recovery_code"]?.jsonPrimitive?.contentOrNull)
    if (codes.isEmpty() || codes.size > 16 || codes.any { it.isBlank() || it.length > 256 || it.any(Char::isISOControl) })
        throw RemoteFailure(502, "Your account was updated, but the recovery code response was incomplete. Keep this page open and contact your server administrator.")
    return RecoveryKit(codes, recovered)
}

internal fun validAccountUsername(value: String): Boolean = value.trim().let { name ->
    name.isNotEmpty() && name.length <= 80 && name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "._-@" }
}
internal fun validNewPassword(value: String): Boolean = value.encodeToByteArray().size in 12..256
