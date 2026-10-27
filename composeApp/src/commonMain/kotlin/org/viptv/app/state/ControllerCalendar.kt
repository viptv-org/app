package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.features.calendar.CalendarEntry
import org.viptv.engine.wire.*

fun ApplicationController.calendarCategory(type: String) {
    if (type !in listOf("series", "anime", "movie") || mutable.value.calendarType == type) return
    mutable.value = mutable.value.copy(calendarType = type)
    run { loadCalendar() }
}

/** Zero returns to this week and refreshes its release status. */
fun ApplicationController.calendarWeek(change: Int) {
    if (change !in -1..1) return
    val week = if (change == 0) 0 else (mutable.value.calendarWeek + change).coerceIn(-52, 52)
    mutable.value = mutable.value.copy(calendarWeek = week)
    run { loadCalendar() }
}

internal suspend fun ApplicationController.loadCalendar() {
    calendarReread?.cancel(); calendarReread = null
    val remote = connection ?: return
    val state = mutable.value
    val window = calendarWindow(state.calendarWeek * 7)
    val profile = state.selectedProfile
    val operation = generation
    mutable.value = state.copy(calendarDays = window.days, calendarEntries = emptyList(), calendarSources = null, calendarAvailable = true)
    val context=currentCoroutineContext()
    try {
        val read = remote.calendar(window, state.calendarType) { entries ->
            context.ensureActive()
            if (generation != operation || connection !== remote || mutable.value.selectedProfile != profile ||
                mutable.value.screen != Screen.Calendar) throw CancellationException("Calendar changed")
            mutable.value = mutable.value.copy(calendarEntries = entries)
        }
        context.ensureActive()
        if (generation != operation) return
        mutable.value = mutable.value.copy(calendarSources = read.sources)
        read.refreshAfterMillis?.let { rereadCalendar(CalendarRequest(remote, profile, state.calendarType, state.calendarWeek, window), it) }
    } catch (error: RemoteFailure) {
        if (error.status != 422) throw error
        currentCoroutineContext().ensureActive()
        if (generation == operation) mutable.value = mutable.value.copy(calendarEntries = emptyList(), calendarAvailable = false)
    }
}

/** The window a calendar read belongs to: a re-read publishes only while the viewer still looks at it. */
private data class CalendarRequest(val remote: RemoteConnection, val profile: String?, val type: String, val week: Int, val window: CalendarWindow)

/**
 * How long one visit to a window keeps re-reading while the server still reports
 * unfinished library lookups (its cold fetch stops at 12 s, an unfinished list is kept 15 s).
 */
internal const val CALENDAR_REREAD_LIMIT_MILLIS = 120_000L

/**
 * The server is still looking up library series, so the window can grow. Read it again
 * from the first page after [delayMillis], on the side: no busy cover, the shown rows stay
 * until the whole window has arrived, and a failure keeps them (an expired session still
 * goes to sign-in). It repeats while the server keeps sending the hint, for at most
 * [CALENDAR_REREAD_LIMIT_MILLIS]; another load, week, type, profile, screen or session ends it.
 */
private fun ApplicationController.rereadCalendar(request: CalendarRequest, delayMillis: Long) {
    fun current() = connection === request.remote && mutable.value.let { state ->
        state.screen == Screen.Calendar && state.selectedProfile == request.profile &&
            state.calendarType == request.type && state.calendarWeek == request.week && state.calendarAvailable
    }
    calendarReread?.cancel()
    calendarReread = scope.launch {
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        var wait = delayMillis
        while (true) {
            val pause = wait.coerceIn(1_000, 30_000)
            if (started.elapsedNow().inWholeMilliseconds + pause > CALENDAR_REREAD_LIMIT_MILLIS) return@launch
            delay(pause)
            if (!current() || job?.isActive == true) return@launch
            val read = try { request.remote.calendar(request.window, request.type) }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    if (error is RemoteFailure && error.status == 401 && current()) expireSession(request.remote, null)
                    return@launch
                }
            if (!current() || job?.isActive == true) return@launch
            mutable.value = mutable.value.copy(calendarEntries = read.entries, calendarSources = read.sources)
            wait = read.refreshAfterMillis ?: return@launch
        }
    }
}

/**
 * A Calendar row was chosen. A library row opens its series' Details through the
 * episode it names, and records that episode so Details can land on its season
 * (app#40). Other rows open their title, as before.
 */
fun ApplicationController.openCalendarEntry(entry: CalendarEntry) {
    val episode = entry.episodeCard.takeIf { entry.library }
    open(episode ?: entry.card)
    if (episode != null) mutable.value = mutable.value.copy(calendarEpisode = episode)
}

/** Schedule coordinates select only an actual host-returned episode, never a fabricated ID. */
fun ApplicationController.watchCalendar(entry: CalendarEntry) {
    // A library row already carries the playable episode: find its sources directly.
    // Back from them lands on the episode's season in Details, as opening the row does.
    entry.episodeCard?.takeIf { entry.library }?.let { episode ->
        playTitle(episode)
        mutable.value = mutable.value.copy(calendarEpisode = episode)
        return
    }
    beginDetails()
    run {
        val remote = requireNotNull(connection)
        val profile = mutable.value.selectedProfile
        val operation = generation
        fun current()=generation==operation&&connection===remote&&mutable.value.selectedProfile==profile&&mutable.value.screen==Screen.Details
        var loaded: DetailSnapshot? = null
        val target = withTimeoutOrNull(8_000) {
            val initial = remote.detail(entry.card, limit = 128)
            currentCoroutineContext().ensureActive()
            if(!current())return@withTimeoutOrNull null
            requireCalendarParent(entry.card, initial.item)
            loaded = initial
            mutable.value = mutable.value.copy(detail = initial)
            if (!entry.aired) return@withTimeoutOrNull null
            if (initial.item.identity.kind == "movie") return@withTimeoutOrNull initial.item
            val season = entry.season ?: return@withTimeoutOrNull null
            val episode = entry.episode?.takeIf { it > 0 } ?: return@withTimeoutOrNull null
            val firstMatches=calendarMatches(initial.episodes,initial.item,season,episode)
            if(firstMatches.size==1)return@withTimeoutOrNull firstMatches.single()
            if(firstMatches.size>1)return@withTimeoutOrNull null
            val index = initial.seasons.filter { it.season == season }.singleOrNull() ?: return@withTimeoutOrNull null
            if (index.offset !in 0..10_000 || index.episode_count !in 1..10_000) return@withTimeoutOrNull null
            val candidates = initial.episodes.toMutableList()
            var offset = index.offset.toInt()
            repeat(3) {
                val matches = calendarMatches(candidates, initial.item, season, episode)
                if (matches.size == 1) return@withTimeoutOrNull matches.single()
                if (matches.size > 1) return@withTimeoutOrNull null
                if (offset == 0 && candidates.isNotEmpty()) offset = initial.next_cursor?.toIntOrNull()?.takeIf { it in 1..10_000 } ?: return@withTimeoutOrNull null
                if (offset.toLong() >= index.offset + index.episode_count) return@withTimeoutOrNull null
                val page = remote.detail(initial.item, offset, 128)
                currentCoroutineContext().ensureActive()
                if(!current())return@withTimeoutOrNull null
                requireCalendarParent(initial.item, page.item)
                candidates += page.episodes
                loaded = initial.copy(episodes = candidates.distinctBy(::cardLookupKey), next_cursor = page.next_cursor)
                mutable.value = mutable.value.copy(detail = loaded)
                val found=calendarMatches(candidates,initial.item,season,episode)
                if(found.size==1)return@withTimeoutOrNull found.single()
                if(found.size>1)return@withTimeoutOrNull null
                offset=page.next_cursor?.toIntOrNull()?.takeIf{it>offset&&it<=10_000}?:return@withTimeoutOrNull null
                if (page.episodes.isEmpty()) return@withTimeoutOrNull null
            }
            calendarMatches(candidates, initial.item, season, episode).singleOrNull()
        }
        currentCoroutineContext().ensureActive()
        if (generation != operation || connection !== remote || mutable.value.selectedProfile != profile || mutable.value.screen != Screen.Details) return@run
        if (target != null) chooseSource(target)
        else mutable.value = mutable.value.copy(detail = loaded ?: mutable.value.detail,
            error = if(loaded==null)"Could not load this release’s details. Go back and try again." else "This scheduled episode could not be matched safely. Choose an episode from these details.")
    }
}

private fun requireCalendarParent(expected: Card, actual: Card) {
    if (expected.identity.id != actual.identity.id || expected.identity.kind != actual.identity.kind ||
        expected.metadata_origin != actual.metadata_origin) throw LocalFailure("This title’s integration changed. Open its details again.")
}

internal fun calendarMatches(cards: List<Card>, parent: Card, season: Long, episode: Long): List<Card> =
    cards.filter { card ->
        val at = card.identity.episode
        at?.order == "aired" && at.season == season && at.episode == episode &&
            card.identity.kind == "series" && card.parent_identity?.id == parent.identity.id &&
            card.parent_identity?.kind == parent.identity.kind && card.metadata_origin == parent.metadata_origin
    }.distinctBy(::cardLookupKey)
