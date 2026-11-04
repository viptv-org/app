package org.viptv.app.connection

import kotlinx.serialization.Serializable
import org.viptv.engine.wire.CatalogPage

@Serializable
data class AddonFailure(
    val addon_id: Long,
    val addon_name: String,
    val error_code: String,
)

@Serializable
internal data class AddonFailuresBody(val failures: List<AddonFailure> = emptyList())

data class BrowsePage(val page: CatalogPage, val failures: List<AddonFailure> = emptyList())