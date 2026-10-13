package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*

internal suspend fun ApplicationController.loadLive(query: LiveQuery, append: Boolean = false) {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val page = local?.liveChannels(profile, query) ?: requireNotNull(connection).liveChannels(profile, query)
    val channels = ((if (append) mutable.value.livePage?.channels.orEmpty() else emptyList()) + page.channels).distinctBy { it.id }
    mutable.value = mutable.value.copy(liveQuery = query, livePage = page.copy(channels = channels), liveGuides = if (append) mutable.value.liveGuides else emptyMap())
    channels.firstOrNull()?.let { showLiveGuide(it.id) }
}

fun ApplicationController.filterLive(group: String?) = run { loadLive(mutable.value.liveQuery.copy(group_id = group, offset = 0)) }
fun ApplicationController.moreLive() = run {
    val offset = mutable.value.livePage?.next_cursor?.toIntOrNull() ?: return@run
    loadLive(mutable.value.liveQuery.copy(offset = offset), append = true)
}
fun ApplicationController.refreshLive() = run { loadLive(mutable.value.liveQuery.copy(offset = 0)) }

fun ApplicationController.showLiveGuide(channel: String) {
    if (mutable.value.liveGuides.containsKey(channel)) return
    liveGuideJob?.cancel()
    val profile = mutable.value.selectedProfile ?: return
    val remote = connection
    val device = local
    liveGuideJob = scope.launch {
        delay(250)
        val guide = try { device?.liveGuide(profile, channel) ?: requireNotNull(remote).liveGuide(profile, channel) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { LiveGuideSnapshot(channel, availability = "TemporarilyUnavailable", reason = "Could not load the program guide.") }
        if (mutable.value.selectedProfile == profile && connection === remote && local === device)
            mutable.value = mutable.value.copy(liveGuides = mutable.value.liveGuides + (channel to guide))
    }
}

fun ApplicationController.playLive(channel: String) = run {
    val operation = generation
    liveGuideJob?.cancel()
    val card = mutable.value.livePage?.channels?.find { it.id == channel }?.card ?: throw LocalFailure("Choose an available channel first.")
    val profile = requireNotNull(mutable.value.selectedProfile)
    mutable.value = mutable.value.copy(screen = Screen.Sources, sourceReturnScreen = Screen.Live, detail = null, sourceTitle = card, sources = emptyList(), findingSources = true)
    try {
        val sources = local?.liveSources(profile, channel) ?: requireNotNull(connection).liveSources(profile, channel)
        mutable.value = mutable.value.copy(sources = sources)
        if (sources.size == 1) playSource(sources.first())
    } finally { if (generation == operation) mutable.value = mutable.value.copy(findingSources = false) }
}
