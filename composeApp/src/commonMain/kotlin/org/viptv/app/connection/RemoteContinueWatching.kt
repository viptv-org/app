package org.viptv.app.connection

import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.viptv.engine.wire.ContinuationPage
import org.viptv.engine.wire.HomeBrowseTarget
import org.viptv.engine.wire.HomeRow

/**
 * The full Continue Watching list, one page at a time (app#114). Home's row holds only the
 * server's first 12 entries; `offset` is the page's `next_cursor`, and `limit` is 1 to 100.
 */
internal suspend fun RemoteConnection.continueWatching(profile: String, offset: Int, limit: Int): ContinuationPage =
    Json { ignoreUnknownKeys = true }.decodeFromJsonElement(
        call("/api/profiles/${profile.encodeURLPathPart()}/continue?offset=$offset&limit=$limit"))

/** The row's browse action leads to the full Continue Watching list ([continueWatching]), not a catalog page. */
internal fun HomeRow.opensContinueWatching(): Boolean = browse?.target == HomeBrowseTarget.CONTINUE
