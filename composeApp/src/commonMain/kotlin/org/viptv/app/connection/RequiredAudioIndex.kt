package org.viptv.app.connection

import kotlinx.serialization.json.*

internal fun JsonObject.requiredAudioIndex(): Int? {
    val value = this["required_audio_index"]?.takeUnless { it is JsonNull } ?: return null
    return value.jsonPrimitive.intOrNull?.takeIf { it >= 0 }
        ?: throw IllegalArgumentException("The source requires an invalid audio track.")
}
