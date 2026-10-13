package org.viptv.app.connection

import kotlinx.serialization.Serializable
import org.viptv.engine.wire.SourceCandidate

@Serializable
data class SourceCredentialField(val id: String, val label: String, val required: Boolean = false, val secret: Boolean = false)

@Serializable
data class SourceProviderChoice(val id: String, val label: String)

@Serializable
data class SourceIntegrationSchema(
    val plugin_id: String,
    val name: String,
    val state: String,
    val runtime_enabled: Boolean = false,
    val credential_fields: List<SourceCredentialField> = emptyList(),
    val connection_id: String? = null,
    val provider_choices: List<SourceProviderChoice> = emptyList(),
    val maximum_providers: Int = 12,
)

@Serializable
data class SourceIntegrationSnapshot(
    val schemas: List<SourceIntegrationSchema> = emptyList(),
    val revision: Long = 0,
    val connections: List<org.viptv.engine.wire.IntegrationConnection> = emptyList(),
)

@Serializable
internal data class IntegrationSourceEvent(val seq: Long, val sources: List<SourceCandidate> = emptyList())

@Serializable
internal data class IntegrationSourcePage(
    val id: String,
    val events: List<IntegrationSourceEvent> = emptyList(),
    val done: Boolean = false,
    val error_code: String? = null,
)
