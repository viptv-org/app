package org.viptv.app.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.viptv.engine.wire.LiveGuide
import org.viptv.engine.wire.LivePage
import org.viptv.engine.wire.LiveQuery
import org.viptv.engine.wire.SourceCandidate

internal suspend fun NativeLocalConnection.fetchLiveChannels(profile: String, query: LiveQuery): LivePage =
    withContext(Dispatchers.IO) { json.decodeFromString(engine.liveChannels(profile, json.encodeToString(query))) }

internal suspend fun NativeLocalConnection.fetchLiveGuide(profile: String, channel: String): LiveGuide =
    withContext(Dispatchers.IO) { json.decodeFromString(engine.liveGuide(profile, channel)) }

internal suspend fun NativeLocalConnection.fetchLiveSources(profile: String, channel: String): List<SourceCandidate> =
    withContext(Dispatchers.IO) { json.decodeFromString(engine.liveSources(profile, channel)) }
