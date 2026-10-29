package org.viptv.app.state

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HomeRow
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.RowState
import org.viptv.engine.wire.Scope

enum class QuietWork { Check, Refresh }

internal data class QuietOwner(val remote: RemoteConnection?, val local: LocalConnection?, val profile: String)

internal fun Scope.owner() = copy(revision = 0)

fun ApplicationController.appForeground(foreground: Boolean) {
    playbackInFront(foreground)
    freshness.foreground(foreground)
    freshnessWatcher?.cancel(); freshnessWatcher = null
    if (!foreground) return
    freshen()
    freshnessWatcher = scope.launch { while (isActive) { delay(Freshness.TICK); freshnessTick() } }
}

fun ApplicationController.foregroundObserver(onReturn: () -> Unit = {}) = LifecycleEventObserver { _, event ->
    when (event) {
        Lifecycle.Event.ON_START -> { appForeground(true); onReturn() }
        Lifecycle.Event.ON_RESUME -> freshen()
        Lifecycle.Event.ON_STOP -> appForeground(false)
        else -> Unit
    }
}

fun ApplicationController.userActivity() {
    freshness.input()
    if (!viewerPresent) { viewerPresent = true; viewerReturned.trySend(Unit) }
}

internal suspend fun ApplicationController.trackViewerPresence(): Nothing {
    while (true) {
        val left = freshness.untilIdle()
        if (left > kotlin.time.Duration.ZERO) { delay(left); continue }
        viewerPresent = false
        viewerReturned.tryReceive()
        if (!viewerPresent) viewerReturned.receive()
    }
}

fun ApplicationController.freshen() {
    val state = mutable.value
    if (!freshness.active() || state.screen == Screen.Player || state.selectedProfile == null) return
    if (state.home != null && freshness.homeStale()) requestQuiet(QuietWork.Refresh)
    else if (freshness.checkDue(polling = false)) requestQuiet(QuietWork.Check)
}

private val POLLED_SCREENS = setOf(Screen.Home, Screen.ContinueWatching)

internal fun ApplicationController.freshnessTick() {
    val state = mutable.value
    if (!freshness.active() || state.screen !in POLLED_SCREENS || state.home == null || state.selectedProfile == null) return
    if (freshness.homeStale() && freshness.idle()) requestQuiet(QuietWork.Refresh)
    else if (freshness.checkDue(polling = true)) requestQuiet(QuietWork.Check)
}

suspend fun ApplicationController.pullToRefresh() {
    val state = mutable.value
    if (state.screen == Screen.Player || state.selectedProfile == null) return
    requestQuiet(QuietWork.Refresh, detail = state.screen in DETAIL_SCREENS)
    while (true) { val running = quietJob ?: return; running.join(); if (quietJob === running) return }
}

internal fun ApplicationController.afterPlayback(live: Boolean = false) {
    if (!live) requestQuiet(QuietWork.Refresh, detail = true)
}

internal fun ApplicationController.requestQuiet(work: QuietWork, detail: Boolean = false) {
    val owner = QuietOwner(connection, local, mutable.value.selectedProfile ?: return)
    if (owner.remote == null && owner.local == null) return
    if (work == QuietWork.Check) freshness.checkRequested()
    if (quietJob?.isActive == true && quietOwner == owner) {
        quietPending = quietPending?.let { maxOf(it, work) } ?: work
        quietDetail = quietDetail || detail
        return
    }
    quietJob?.cancel()
    quietOwner = owner; quietPending = null; quietDetail = false
    quietJob = scope.launch {
        var next: QuietWork? = work
        var withDetail = detail
        while (next != null && current(owner)) {
            quietly(owner) { if (next == QuietWork.Check) checkRevisions(owner) else refreshQuietly(owner, null, withDetail) }
            next = quietPending; withDetail = quietDetail
            quietPending = null; quietDetail = false
        }
    }
}

private fun ApplicationController.current(owner: QuietOwner) =
    connection === owner.remote && local === owner.local && mutable.value.selectedProfile == owner.profile

private suspend fun ApplicationController.quietly(owner: QuietOwner, work: suspend () -> Unit) {
    try { work() }
    catch (error: CancellationException) { throw error }
    catch (error: Exception) {
        if (error is RemoteFailure && error.status == 401 && current(owner)) expireSession(owner.remote, owner.local)
    }
}

private suspend fun ApplicationController.checkRevisions(owner: QuietOwner) {
    val remote = owner.remote ?: return freshness.unsupported()
    // A check that outlived its session must not mark the next session's clock.
    val observed = try { remote.revisions(owner.profile) } finally { if (current(owner)) freshness.checkFinished() }
    if (!current(owner)) return
    if (observed == null) return freshness.unsupported()
    if (freshness.changed(observed)) refreshQuietly(owner, observed, detail = false) else freshness.adopt(observed)
}

private suspend fun ApplicationController.refreshQuietly(owner: QuietOwner, observed: Revisions?, detail: Boolean) {
    progressJob?.join()
    val libraryMoved = observed?.let(freshness::libraryChanged) ?: true
    val addonsMoved = observed?.let(freshness::addonsChanged) ?: false
    if (addonsMoved) addonOperation?.join()
    val device = owner.local
    if (device != null) {
        val home = device.refreshHome(owner.profile)
        val membership = device.libraryMembership(owner.profile)
        val cards = device.library(owner.profile)
        if (!current(owner)) return
        publishHome(home)
        updateLibrary(cards, membership = membership)
        freshness.homeBuilt(null)
    } else {
        val result = requireNotNull(owner.remote).bootstrap()
        if (!current(owner) || result.selected != owner.profile) return
        publishHome(result.home)
        adoptContinuation(result.continuation)
        val state = mutable.value
        mutable.value = state.copy(profiles = result.profiles, profileManagement = result.profileManagement ?: state.profileManagement,
            preferences = result.preferences ?: state.preferences, policyRefreshNeeded = false)
        result.library?.let { updateLibrary(it, membership = result.libraryMembership) }
        freshness.homeBuilt(result.revisions ?: observed)
    }
    if (libraryMoved) reloadVisibleLibrary(owner)
    if (detail || (libraryMoved && mutable.value.screen in DETAIL_SCREENS)) reloadDetailQuietly(owner)
    if (addonsMoved) reloadAddonsQuietly(owner)
}

private val DETAIL_SCREENS = setOf(Screen.Details, Screen.Sources)

private fun ApplicationController.publishHome(fresh: HomeSnapshot?) {
    val state = mutable.value
    val merged = mergeHome(state.home, fresh) ?: return
    val ids = merged.rows.mapTo(mutableSetOf()) { it.id }
    mutable.value = state.copy(home = merged, homeRowBusy = state.homeRowBusy intersect ids,
        homeRowErrors = state.homeRowErrors.filterKeys { it in ids })
}

internal fun mergeHome(current: HomeSnapshot?, fresh: HomeSnapshot?): HomeSnapshot? {
    if (current == null || fresh == null || current.scope.owner() != fresh.scope.owner()) return fresh
    val shown = current.rows.associateBy { it.id }
    // A rebuild that returns no art must not blank the billboard: the hero would drop to its flat fill and lose its logo until the screen changes.
    val artwork = (current.featured + listOfNotNull(current.hero)).associateBy(::cardLookupKey)
    fun kept(row: HomeRow) = row.copy(cards = row.cards.map { artworkOf(it, artwork[cardLookupKey(it)]) })
    return fresh.copy(hero = fresh.hero?.let { artworkOf(it, artwork[cardLookupKey(it)]) },
        featured = fresh.featured.map { artworkOf(it, artwork[cardLookupKey(it)]) },
        rows = fresh.rows.map { row ->
            val old = shown[row.id]
            if (old == null || row.state != RowState.READY || old.state != RowState.READY || row.pageQuery() == null ||
                row.browse != old.browse || old.cards.size <= row.cards.size) kept(row)
            else kept(row.copy(cards = (row.cards + old.cards).distinctBy(::cardLookupKey), next_cursor = old.next_cursor))
        })
}

private fun artworkOf(card: Card, shown: Card?): Card {
    if (shown == null) return card
    fun kept(fresh: String?, current: String?) = fresh?.takeIf { it.isNotBlank() } ?: current
    val landscape = kept(card.landscape, shown.landscape)
    val poster = kept(card.poster, shown.poster)
    val logo = kept(card.logo, shown.logo)
    if (landscape == card.landscape && poster == card.poster && logo == card.logo) return card
    return card.copy(landscape = landscape, poster = poster, logo = logo)
}

private suspend fun ApplicationController.reloadVisibleLibrary(owner: QuietOwner) {
    when (mutable.value.screen) {
        Screen.Library -> if (mutable.value.libraryPage != null && current(owner)) loadLibraryPage(refresh = true)
        Screen.ContinueWatching -> reloadVisibleContinueWatching(owner)
        Screen.History -> {
            val history = owner.local?.history(owner.profile) ?: requireNotNull(owner.remote).history(owner.profile)
            if (current(owner) && mutable.value.screen == Screen.History) mutable.value = mutable.value.copy(history = history)
        }
        else -> Unit
    }
}

private val BROWSE_SCREENS = setOf(Screen.Discover, Screen.Movies, Screen.Series, Screen.Anime)

private suspend fun ApplicationController.reloadAddonsQuietly(owner: QuietOwner) {
    val remote = owner.remote ?: return
    while (true) {
        addonOperation?.join()
        val followed = addonOperation
        val result = remote.addons(owner.profile)
        if (!current(owner)) return
        if (addonOperation !== followed) continue
        return publishAddons(result, keepBrowse = mutable.value.screen in BROWSE_SCREENS)
    }
}

private suspend fun ApplicationController.reloadDetailQuietly(owner: QuietOwner) {
    val shown = mutable.value.detail ?: return
    val played = mutable.value.sourceTitle?.identity?.episode
    val season = played?.let { coordinates -> shown.seasons.firstOrNull { it.season == coordinates.season } }
    val offset = season?.offset?.toInt()?.coerceIn(0, 10_000) ?: 0
    val limit = (season?.episode_count?.toInt() ?: 24).coerceIn(1, 96)
    val fresh = owner.local?.detailPage(owner.profile, shown.item, offset, limit)
        ?: requireNotNull(owner.remote).detail(shown.item, offset, limit)
    if (!current(owner)) return
    val loaded = mutable.value.detail ?: return
    val key = cardLookupKey(shown.item)
    if (cardLookupKey(loaded.item) != key || cardLookupKey(fresh.item) != key) return
    val updated = fresh.episodes.associateBy(::cardLookupKey)
    // A sparse refresh must not blank the art the page already shows.
    val item = fresh.item.copy(landscape = fresh.item.landscape ?: loaded.item.landscape,
        poster = fresh.item.poster ?: loaded.item.poster, logo = fresh.item.logo ?: loaded.item.logo)
    mutable.value = mutable.value.copy(detail = loaded.copy(item = item,
        episodes = loaded.episodes.map { updated[cardLookupKey(it)] ?: it }))
}
