package org.viptv.app.state

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.RowState
import org.viptv.engine.wire.Scope

/** What the background lane does next. A refresh outranks a check when requests coalesce. */
enum class QuietWork { Check, Refresh }

/** A background result belongs to the session and profile that asked for it. */
internal data class QuietOwner(val remote: RemoteConnection?, val local: LocalConnection?, val profile: String)

/** Home identity without its revision: a refreshed Home is the same Home to paging, focus and the hero. */
internal fun Scope.owner() = copy(revision = 0)

/**
 * The app came to the front or went behind (Android start/stop, desktop
 * window focus, browser tab visibility). Returning checks immediately; the
 * watcher runs only while the app is in front.
 */
fun ApplicationController.appForeground(foreground: Boolean) {
    playbackInFront(foreground)
    freshness.foreground(foreground)
    freshnessWatcher?.cancel(); freshnessWatcher = null
    if (!foreground) return
    freshen()
    freshnessWatcher = scope.launch { while (isActive) { delay(Freshness.TICK); freshnessTick() } }
}

/**
 * The one foreground hook: start and stop are [appForeground], resume is [freshen].
 * App.kt installs it when it owns the controller; Android's MainActivity installs it
 * on its own lifecycle, because ApplicationModel owns the controller there (app#75).
 * [onReturn] runs after each return to the front, once the controller knows of it.
 */
fun ApplicationController.foregroundObserver(onReturn: () -> Unit = {}) = LifecycleEventObserver { _, event ->
    when (event) {
        Lifecycle.Event.ON_START -> { appForeground(true); onReturn() }
        Lifecycle.Event.ON_RESUME -> freshen()
        Lifecycle.Event.ON_STOP -> appForeground(false)
        else -> Unit
    }
}

/** Key, remote, touch or pointer input. An aged Home waits until the viewer is idle. */
fun ApplicationController.userActivity() {
    freshness.input()
    if (!viewerPresent) { viewerPresent = true; viewerReturned.trySend(Unit) }
}

/**
 * Keeps [ApplicationController.viewerPresent] current while something depends on it:
 * sleeps until the viewer has been [Freshness.idle], marks them away, then waits for
 * [userActivity] to bring them back. Neither wait asks for frames.
 */
internal suspend fun ApplicationController.trackViewerPresence(): Nothing {
    while (true) {
        val left = freshness.untilIdle()
        if (left > kotlin.time.Duration.ZERO) { delay(left); continue }
        viewerPresent = false
        // Drop a wake-up left over from before this idle spell, then wait for input.
        viewerReturned.tryReceive()
        if (!viewerPresent) viewerReturned.receive()
    }
}

/** Foreground return or entry to a screen: check now, or rebuild an aged Home. */
fun ApplicationController.freshen() {
    val state = mutable.value
    if (!freshness.active() || state.screen == Screen.Player || state.selectedProfile == null) return
    if (state.home != null && freshness.homeStale()) requestQuiet(QuietWork.Refresh)
    else if (freshness.checkDue(polling = false)) requestQuiet(QuietWork.Check)
}

/** While Home is visible: poll the revisions, and rebuild an aged Home once the viewer is idle. */
internal fun ApplicationController.freshnessTick() {
    val state = mutable.value
    if (!freshness.active() || state.screen != Screen.Home || state.home == null || state.selectedProfile == null) return
    if (freshness.homeStale() && freshness.idle()) requestQuiet(QuietWork.Refresh)
    else if (freshness.checkDue(polling = true)) requestQuiet(QuietWork.Check)
}

/**
 * Phone and tablet pull-to-refresh: rebuild Home and the page on display now,
 * with no revisions check and no coalescing. Quiet-lane rules apply: no busy
 * cover, a failure keeps the shown data, an expired session goes to sign-in.
 * Returns once the lane has nothing left to do, which ends the indicator.
 */
suspend fun ApplicationController.pullToRefresh() {
    val state = mutable.value
    if (state.screen == Screen.Player || state.selectedProfile == null) return
    requestQuiet(QuietWork.Refresh, detail = state.screen in DETAIL_SCREENS)
    // A check or refresh already running finishes first; the forced refresh follows it in the same job.
    while (true) { val running = quietJob ?: return; running.join(); if (quietJob === running) return }
}

/** Progress changed: rebuild Home and the details Back returns to once the final save lands. */
internal fun ApplicationController.afterPlayback(live: Boolean = false) {
    if (!live) requestQuiet(QuietWork.Refresh, detail = true)
}

/** One background lane. A request during work runs once more afterwards instead of being dropped. */
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

/** Failures keep the last good data and stay silent; only an expired session is surfaced. */
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
    // A refresh with no observation follows playback: only progress is known to have moved.
    val addonsMoved = observed?.let(freshness::addonsChanged) ?: false
    // An add-on change in progress on this device is never interrupted: rebuild from what it leaves behind.
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

/** Details stays composed behind the sources sheet, so both show its progress. */
private val DETAIL_SCREENS = setOf(Screen.Details, Screen.Sources)

/** A missing Home never erases the shown one. Paging state survives for rows that still exist. */
private fun ApplicationController.publishHome(fresh: HomeSnapshot?) {
    val state = mutable.value
    val merged = mergeHome(state.home, fresh) ?: return
    val ids = merged.rows.mapTo(mutableSetOf()) { it.id }
    mutable.value = state.copy(home = merged, homeRowBusy = state.homeRowBusy intersect ids,
        homeRowErrors = state.homeRowErrors.filterKeys { it in ids })
}

/**
 * Fresh rows replace shown rows, except that a catalog row the viewer has
 * already paged keeps its loaded tail: a refresh never shrinks a row under focus.
 */
internal fun mergeHome(current: HomeSnapshot?, fresh: HomeSnapshot?): HomeSnapshot? {
    if (current == null || fresh == null || current.scope.owner() != fresh.scope.owner()) return fresh
    val shown = current.rows.associateBy { it.id }
    return fresh.copy(rows = fresh.rows.map { row ->
        val old = shown[row.id]
        if (old == null || row.state != RowState.READY || old.state != RowState.READY || row.pageQuery() == null ||
            row.browse != old.browse || old.cards.size <= row.cards.size) row
        else row.copy(cards = (row.cards + old.cards).distinctBy(::cardLookupKey), next_cursor = old.next_cursor)
    })
}

private suspend fun ApplicationController.reloadVisibleLibrary(owner: QuietOwner) {
    when (mutable.value.screen) {
        Screen.Library -> if (mutable.value.libraryPage != null && current(owner)) loadLibraryPage(refresh = true)
        Screen.History -> {
            val history = owner.local?.history(owner.profile) ?: requireNotNull(owner.remote).history(owner.profile)
            if (current(owner) && mutable.value.screen == Screen.History) mutable.value = mutable.value.copy(history = history)
        }
        else -> Unit
    }
}

private val BROWSE_SCREENS = setOf(Screen.Discover, Screen.Movies, Screen.Series, Screen.Anime)

/** The add-on list (Settings → Add-ons) and what derives from it. A Browse page on display keeps its cards. */
private suspend fun ApplicationController.reloadAddonsQuietly(owner: QuietOwner) {
    val remote = owner.remote ?: return
    while (true) {
        addonOperation?.join()
        val followed = addonOperation
        val result = remote.addons(owner.profile)
        if (!current(owner)) return
        // An add-on operation that started meanwhile may publish a newer list: wait for it and read again.
        if (addonOperation !== followed) continue
        return publishAddons(result, keepBrowse = mutable.value.screen in BROWSE_SCREENS)
    }
}

/** Watched state and progress for the page Back returns to; loaded episodes, focus and paging stay. */
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
    // A sparse refresh (no art fields) must not wipe the art the page already
    // showed — the quiet reload used to blank the backdrop after a merge.
    val item = fresh.item.copy(landscape = fresh.item.landscape ?: loaded.item.landscape,
        poster = fresh.item.poster ?: loaded.item.poster, logo = fresh.item.logo ?: loaded.item.logo)
    mutable.value = mutable.value.copy(detail = loaded.copy(item = item,
        episodes = loaded.episodes.map { updated[cardLookupKey(it)] ?: it }))
}
