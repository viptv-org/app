package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.minutes

internal data class LiveContext(val profile: String, val remote: RemoteConnection?, val device: LocalConnection?, val directory: Long, val favorites: Long, val configuration: Long)
internal fun ApplicationController.liveContext(): LiveContext = LiveContext(requireNotNull(mutable.value.selectedProfile), connection, local, liveDirectoryRequest, liveFavoriteScope, mutable.value.sourceIntegrations.revision)
internal fun ApplicationController.ownsLive(context: LiveContext, visible: Boolean = true): Boolean =
    mutable.value.selectedProfile == context.profile && connection === context.remote && local === context.device &&
        liveFavoriteScope == context.favorites && mutable.value.sourceIntegrations.revision == context.configuration && !mutable.value.policyRefreshNeeded &&
        (!visible || liveDirectoryRequest == context.directory && mutable.value.screen == Screen.Live)

internal suspend fun ApplicationController.loadLive(query: LiveQuery, append: Boolean = false) {
    val operation = generation
    if (!append) {
        liveDirectoryRequest++; liveGuideRequest++; liveGuideJob?.cancel(); liveGuideFetchedAt.clear()
        mutable.value = mutable.value.copy(liveGuideLoading = false, liveGuideChannel = null, liveGuides = emptyMap())
    }
    val context = liveContext()
    var wanted = query
    var channels = if (append) mutable.value.livePage?.channels.orEmpty() else emptyList()
    val initiallyVisibleFavorites = channels.count { it.id in mutable.value.liveFavorites }
    val offsets = mutableSetOf<Int>()
    var favoritesLoaded = append
    while (true) {
        if (!offsets.add(wanted.offset)) throw LocalFailure("Could not continue loading channels. Refresh Live TV.")
        val page = context.device?.liveChannels(context.profile, wanted) ?: requireNotNull(context.remote).liveChannels(context.profile, wanted)
        if (!ownsLive(context) || generation != operation) throw CancellationException("Live TV selection changed")
        val selected = wanted.connection_id ?: page.channels.firstOrNull()?.connection_id ?: page.connections.firstOrNull()?.id
        if (selected != null && page.channels.any { it.connection_id != selected })
            throw LocalFailure("The server returned channels from a different TV source. Refresh Live TV.")
        wanted = wanted.copy(connection_id = selected)
        channels = (channels + page.channels).distinctBy { it.id }
        mutable.value = mutable.value.copy(liveQuery = wanted, livePage = page.copy(channels = channels))
        if (!favoritesLoaded) { loadLiveFavorites(context); favoritesLoaded = true }
        if (!ownsLive(context) || generation != operation) throw CancellationException("Live TV selection changed")
        val state = mutable.value
        val next = page.next_cursor?.toIntOrNull()
        val needsFavoritePage = state.liveFavoritesOnly && state.liveFavoritesAvailable && state.liveFavorites.isNotEmpty() &&
            channels.count { it.id in state.liveFavorites } <= initiallyVisibleFavorites && page.next_cursor != null
        if (!needsFavoritePage) break
        if (next == null || next <= wanted.offset || next > 10_000)
            throw LocalFailure("Could not continue loading channels. Refresh Live TV.")
        wanted = wanted.copy(offset = next)
    }
    val selected = mutable.value.liveGuideChannel?.takeIf { id -> channels.any { it.id == id } }
        ?: channels.firstOrNull { !mutable.value.liveFavoritesOnly || it.id in mutable.value.liveFavorites }?.id
    selected?.let { showLiveGuide(it) }
}

fun ApplicationController.selectLiveConnection(id: String) {
    if (mutable.value.livePage?.connections?.none { it.id == id && it.enabled } != false) return
    run { loadLive(mutable.value.liveQuery.copy(connection_id = id, group_id = null, offset = 0)) }
}

fun ApplicationController.filterLive(group: String?) = run {
    mutable.value = mutable.value.copy(liveFavoritesOnly = false)
    loadLive(mutable.value.liveQuery.copy(group_id = group, offset = 0))
}
fun ApplicationController.filterLiveFavorites() = run {
    if (!mutable.value.liveFavoritesAvailable) return@run
    mutable.value = mutable.value.copy(liveFavoritesOnly = true)
    loadLive(mutable.value.liveQuery.copy(group_id = null, offset = 0))
}
fun ApplicationController.moreLive() = run {
    if (mutable.value.liveFavoritesOnly && mutable.value.liveFavorites.all { id -> mutable.value.livePage?.channels?.any { it.id == id } == true }) return@run
    val offset = mutable.value.livePage?.next_cursor?.toIntOrNull() ?: return@run
    if (offset <= mutable.value.liveQuery.offset || offset > 10_000) throw LocalFailure("Could not continue loading channels. Refresh Live TV.")
    loadLive(mutable.value.liveQuery.copy(offset = offset), append = true)
}
fun ApplicationController.refreshLive() = run { loadLive(mutable.value.liveQuery.copy(offset = 0)) }
fun ApplicationController.retryLiveGuide() { mutable.value.liveGuideChannel?.let { showLiveGuide(it, true) } }

fun ApplicationController.showLiveGuide(channel: String, force: Boolean = false) {
    if (mutable.value.screen != Screen.Live || mutable.value.livePage?.channels?.none { it.id == channel } != false) return
    liveGuideJob?.cancel()
    val request = ++liveGuideRequest
    val context = liveContext()
    val operation = generation
    mutable.value = mutable.value.copy(liveGuideChannel = channel, liveGuideLoading = false)
    val cached = mutable.value.liveGuides[channel]
    val fresh = liveGuideFetchedAt[channel]?.elapsedNow()?.let { it < 1.minutes } == true
    if (!force && fresh && cached?.availability != "TemporarilyUnavailable" && cached != null) return
    liveGuideJob = scope.launch {
        try {
            if (!force) delay(250)
            if (!ownsLive(context) || generation != operation || liveGuideRequest != request) return@launch
            mutable.value = mutable.value.copy(liveGuideLoading = true)
            val fetched = try { context.device?.liveGuide(context.profile, channel) ?: requireNotNull(context.remote).liveGuide(context.profile, channel) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (error is RemoteFailure && error.status == 401 && ownsLive(context) && liveGuideRequest == request) {
                    run { throw error }; return@launch
                }
                LiveGuideSnapshot(channel, availability = "TemporarilyUnavailable", reason = "Could not load the program guide.")
            }
            val guide = if (fetched.channel_id != channel) LiveGuideSnapshot(channel, availability = "TemporarilyUnavailable", reason = "Could not load the program guide.")
                else if (fetched.availability == "Available" && fetched.programs.isEmpty() && fetched.reason == null)
                    fetched.copy(reason = "Guide data isn’t available for this source yet. You can still watch this channel.") else fetched
            if (ownsLive(context) && generation == operation && liveGuideRequest == request && mutable.value.livePage?.channels?.any { it.id == channel } == true) {
                liveGuideFetchedAt[channel] = TimeSource.Monotonic.markNow()
                mutable.value = mutable.value.copy(liveGuides = mutable.value.liveGuides + (channel to guide))
            }
        } finally {
            if (ownsLive(context) && liveGuideRequest == request) mutable.value = mutable.value.copy(liveGuideLoading = false)
        }
    }
}

fun ApplicationController.playLive(channel: String) = run {
    val operation = generation
    liveGuideJob?.cancel()
    val card = mutable.value.livePage?.channels?.find { it.id == channel }?.card ?: throw LocalFailure("Choose an available channel first.")
    val profile = requireNotNull(mutable.value.selectedProfile)
    mutable.value = mutable.value.copy(screen = Screen.Sources, sourceReturnScreen = Screen.Live, detail = null, sourceTitle = card, sources = emptyList(), findingSources = true, liveGuideLoading = false)
    try {
        val sources = local?.liveSources(profile, channel) ?: requireNotNull(connection).liveSources(profile, channel)
        mutable.value = mutable.value.copy(sources = sources)
        if (sources.size == 1) playSource(sources.first())
    } finally { if (generation == operation) mutable.value = mutable.value.copy(findingSources = false) }
}

internal fun ApplicationController.resetLiveRequests() {
    liveGuideRequest++; liveDirectoryRequest++; liveFavoriteScope++
    liveGuideJob?.cancel(); liveGuideJob = null; liveGuideFetchedAt.clear()
    liveFavoriteJobs.values.forEach { it.cancel() }; liveFavoriteJobs.clear()
}
