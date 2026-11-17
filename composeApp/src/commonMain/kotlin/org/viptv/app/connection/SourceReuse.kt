package org.viptv.app.connection

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.*

@Immutable
data class SourceReuseMemory(val providerId: String, val languages: List<String> = emptyList(),
    val providerName: String? = null, val sourceId: String? = null)

@Immutable
data class SourceReuseContext(val title: SourceReuseMemory? = null, val across: SourceReuseMemory? = null) {
    val present: Boolean get() = title != null || across != null
}

internal fun SourceReuseMemory.json(stableSource: Boolean): JsonObject = buildJsonObject {
    put("provider_id", providerId)
    put("audio_languages", JsonArray(languages.map(::JsonPrimitive)))
    providerName?.let { put("provider_name", it) }
    if (stableSource) sourceId?.let { put("source_id", it) }
}

internal fun SourceReuseContext.json(stableSource: Boolean): JsonObject = buildJsonObject {
    title?.let { put("remembered_source", it.json(stableSource)) }
    across?.let { put("cross_title_source", it.json(stableSource)) }
}
