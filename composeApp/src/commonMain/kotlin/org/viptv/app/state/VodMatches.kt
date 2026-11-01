package org.viptv.app.state

data class VodMatchProvider(val id: Long, val name: String)

data class VodMatchRow(val vodId: String, val providerId: Long, val type: String, val name: String,
    val year: Int? = null, val poster: String? = null, val metadataId: String? = null)

data class VodMatchForm(val vodId: String, val type: String, val name: String, val providerName: String? = null,
    val metadataId: String = "")

data class VodMatchesState(
    val providers: List<VodMatchProvider> = emptyList(),
    val providerId: Long? = null,
    val type: String? = null,
    val query: String = "",
    val rows: List<VodMatchRow> = emptyList(),
    val nextCursor: String? = null,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val saving: Boolean = false,
    val form: VodMatchForm? = null) {
    val filtered: Boolean get() = providerId != null || type != null || query.isNotBlank()

    fun providerName(id: Long): String? = providers.firstOrNull { it.id == id }?.name
}
