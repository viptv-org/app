package org.viptv.app.state

import org.viptv.app.connection.*

/**
 * One import draft for the current remote connection and selected profile.
 * Browser account sessions and native device sessions run the same flow,
 * including add-on choices; the server decides authority. Local mode has no import.
 */
fun ApplicationController.openStremioImport(restricted: Boolean): StremioImportSession? {
    val remote = connection ?: return null
    if (local != null) return null
    val profile = mutable.value.selectedProfile ?: return null
    val name = mutable.value.profiles.find { it.id == profile }?.name ?: return null
    discardStremioImport()
    return StremioImportSession(scope, RemoteStremioImport(remote), profile, name, restricted,
        owned = { connection === remote && local == null && mutable.value.selectedProfile == profile }).also { stremioImport = it }
}

fun ApplicationController.closeStremioImport(session: StremioImportSession) {
    session.dispose()
    if (stremioImport === session) stremioImport = null
}

internal fun ApplicationController.discardStremioImport() {
    stremioImport?.dispose(); stremioImport = null
}

/** A new bootstrap for the same connection and profile keeps the draft; anything else ends it. */
internal fun ApplicationController.discardReplacedStremioImport() {
    if (stremioImport?.owned?.invoke() == false) discardStremioImport()
}
