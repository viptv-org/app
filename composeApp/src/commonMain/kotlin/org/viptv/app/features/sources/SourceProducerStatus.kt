package org.viptv.app.features.sources

import org.viptv.app.connection.SOURCE_FORMAT_UNSUPPORTED
import org.viptv.app.connection.SourceProducerOutcome

internal fun sourceProducerFilterId(providerId: String): String = providerId + ":"

internal fun sourceProducerFor(producers: List<SourceProducerOutcome>, filterId: String?): SourceProducerOutcome? =
    filterId?.let { id -> producers.firstOrNull { sourceProducerFilterId(it.providerId) == id } }

internal fun SourceProducerOutcome.displayLabel(): String = label.ifBlank { sourceProducerFallbackLabel(providerId) }

internal fun sourceProducerFallbackLabel(providerId: String): String = when {
    providerId.startsWith("addon") -> "this add-on"
    providerId.startsWith("iptv") -> "this provider"
    providerId.startsWith("vsources") -> "this source integration"
    else -> "this source"
}

internal fun sourceProducerNotice(producer: SourceProducerOutcome?, discoveryPending: Boolean, hasRows: Boolean): String? {
    producer ?: return null
    val label = producer.displayLabel()
    return when {
        producer.errorCode == SOURCE_FORMAT_UNSUPPORTED -> "$label returned formats this app cannot play. Choose another source."
        producer.errorMessage != null -> producer.errorMessage
        producer.errorCode != null -> "$label could not return sources. Choose another source."
        hasRows -> null
        discoveryPending -> "Still checking $label"
        else -> "No playable sources from $label"
    }
}