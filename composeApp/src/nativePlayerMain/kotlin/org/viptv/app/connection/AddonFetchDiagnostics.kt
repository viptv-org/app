package org.viptv.app.connection

/** Opt-in QA counts only: no URL, query, headers, provider payload, or exception message. */
@Volatile private var addonObserver: ((String) -> Unit)? = null
fun observeAddonFetches(observer: ((String) -> Unit)?) { addonObserver = observer }
internal fun addonFetchFact(kind: String, status: Int, started: Long, bytes: Int = 0, error: String? = null) {
    addonObserver?.invoke("kind=$kind status=$status elapsed_ms=${(System.nanoTime()-started)/1_000_000} bytes=$bytes error=${error ?: "none"}")
}
