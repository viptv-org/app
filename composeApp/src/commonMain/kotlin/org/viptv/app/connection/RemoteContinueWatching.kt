package org.viptv.app.connection

import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.viptv.engine.wire.ContinuationPage
import org.viptv.engine.wire.HomeBrowseTarget
import org.viptv.engine.wire.HomeRow

internal suspend fun RemoteConnection.continueWatching(profile: String, offset: Int, limit: Int): ContinuationPage =
    Json { ignoreUnknownKeys = true }.decodeFromJsonElement(
        call("/api/profiles/${profile.encodeURLPathPart()}/continue?offset=$offset&limit=$limit"))

internal fun HomeRow.opensContinueWatching(): Boolean = browse?.target == HomeBrowseTarget.CONTINUE
