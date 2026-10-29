package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.RowState

fun ApplicationController.refresh() = run {
    progressJob?.join()
    val profile = mutable.value.selectedProfile
    val device = local
    val result = device?.bootstrap(profile) ?: requireNotNull(connection).bootstrap()
    // Keep the last usable Home until the replacement finishes. Publishing a
    // prepared local plan first erased cached rows and cleared busy on failure.
    val refreshed = if (profile != null && result.selected == profile && device != null)
        result.copy(home = device.refreshHome(profile)) else result
    currentCoroutineContext().ensureActive()
    if (mutable.value.screen == Screen.Home || refreshed.selected != profile) accept(refreshed, busy = true)
    // A replacement snapshot has fresh cursors, so no row keeps a stale page state.
    else {
        mutable.value = mutable.value.copy(home = refreshed.home, homeRowBusy = emptySet(), homeRowErrors = emptyMap())
        adoptContinuation(refreshed.continuation)
        freshness.homeBuilt(refreshed.revisions)
    }
}

/** A row stops appending here, above the engine's 200-card catalog page cap. */
private const val HOME_ROW_LIMIT = 240

/**
 * Page one Home row through its own cursor. Cards append in place: the revision,
 * focus, scroll position and every other row are untouched.
 */
fun ApplicationController.homeRowPage(rowId: String) {
    val home = mutable.value.home ?: return
    val row = home.rows.find { it.id == rowId } ?: return
    val cursor = row.next_cursor ?: return
    if (row.state != RowState.READY || rowId in mutable.value.homeRowBusy) return
    val profile = mutable.value.selectedProfile ?: return
    val query = row.pageQuery() ?: return
    if (row.cards.size >= HOME_ROW_LIMIT) {
        mutable.value = mutable.value.copy(home = home.copy(rows = home.rows.map {
            if (it.id == rowId) it.copy(next_cursor = null) else it }))
        return
    }
    val remote = connection
    val device = local
    val homeScope = home.scope.owner()
    val operation = generation
    fun current() = generation == operation && mutable.value.screen == Screen.Home &&
        mutable.value.selectedProfile == profile && mutable.value.home?.scope?.owner() == homeScope &&
        connection === remote && local === device
    mutable.value = mutable.value.copy(homeRowBusy = mutable.value.homeRowBusy + rowId,
        homeRowErrors = mutable.value.homeRowErrors - rowId)
    scope.launch {
        try {
            val page = when {
                device != null -> device.browse(profile, query.browseQuery(cursor.toIntOrNull() ?: return@launch))
                remote != null -> remote.homeRowPage(row, cursor)
                else -> return@launch
            }
            if (!current()) return@launch
            val loaded = mutable.value.home ?: return@launch
            val target = loaded.rows.find { it.id == rowId } ?: return@launch
            val merged = (target.cards + page.cards).distinctBy(::cardLookupKey).take(HOME_ROW_LIMIT)
            // A page that repeats cards must not re-arm the same cursor forever.
            val next = if (merged.size > target.cards.size) advancedCursor(page.next_cursor, cursor) else null
            mutable.value = mutable.value.copy(home = loaded.copy(rows = loaded.rows.map {
                if (it.id == rowId) it.copy(cards = merged, next_cursor = next,
                    state = if (merged.isEmpty()) RowState.EMPTY else RowState.READY) else it }))
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (current()) {
                if (error is RemoteFailure && error.status == 401 && device == null) {
                    releaseRemotePlayback(); remote?.close(); connection = null
                    mutable.value = ApplicationState(error = "Session expired. Sign in again.")
                } else mutable.value = mutable.value.copy(
                    homeRowErrors = mutable.value.homeRowErrors + (rowId to "Could not load more titles. Try again."))
            }
        } finally {
            // Clear the busy flag whenever this snapshot is still the one on screen,
            // even if Home is no longer the visible screen.
            val state = mutable.value
            if (rowId in state.homeRowBusy && state.home?.scope?.owner() == homeScope)
                mutable.value = state.copy(homeRowBusy = state.homeRowBusy - rowId)
        }
    }
}

/** A cursor that does not move forward would page the same cards forever. */
private fun advancedCursor(next: String?, current: String): String? {
    next ?: return null
    if (next == current) return null
    val candidate = next.toIntOrNull()
    val from = current.toIntOrNull()
    return if (candidate != null && from != null && candidate <= from) null else next
}

fun ApplicationController.editSearch(edit: (String) -> String) = search(edit(mutable.value.searchTerm))

fun ApplicationController.search(term: String) {
    mutable.value = mutable.value.copy(searchTerm = term, results = emptyList(), searchRows = emptyList())
    run {
        val operation = generation
        val profile = mutable.value.selectedProfile
        val remote = connection
        val device = local
        delay(300)
        val grouped = when {
            term.isBlank() -> null
            remote != null -> remote.searchResults(term)
            device != null -> device.searchResults(requireNotNull(profile), term)
            else -> null
        }
        val cards = when {
            term.isBlank() -> emptyList()
            grouped != null -> grouped.rows.flatMap { it.cards }
            device != null -> device.search(requireNotNull(profile), term).cards
            else -> requireNotNull(remote).search(term).cards
        }
        if (generation == operation && mutable.value.searchTerm == term &&
            mutable.value.selectedProfile == profile && connection === remote && local === device) {
            mutable.value = mutable.value.copy(results = cards, searchRows = grouped?.rows.orEmpty())
        }
    }
}

/** Cards whose feed row is sparse get their billboard fields from one detail fetch. */
private val summaryRequested = mutableSetOf<String>()

/**
 * Continue Watching rows can arrive with neither a logo nor a real description;
 * the billboard then shows a bare title. Fetch the title once and remember the
 * enriched card; [ApplicationState.summaryCards] is what the billboard reads.
 */
fun ApplicationController.enrichSummary(card: Card) {
    val sparse = card.logo == null || card.description.isNullOrBlank()
    if (!sparse || !summaryRequested.add(card.identity.id)) return
    val profile = mutable.value.selectedProfile ?: return
    scope.launch {
        val detail = runCatching { local?.detail(profile, card) ?: connection?.detail(card) }.getOrNull() ?: return@launch
        val richer = detail.item
        val merged = card.copy(
            logo = card.logo ?: richer.logo,
            description = if ((richer.description?.length ?: 0) > (card.description?.length ?: 0)) richer.description else card.description,
            landscape = card.landscape ?: richer.landscape,
            poster = card.poster ?: richer.poster,
            metadata = card.metadata ?: richer.metadata,
        )
        if (merged != card) mutable.value = mutable.value.copy(summaryCards = mutable.value.summaryCards + (card.identity.id to merged))
    }
}

fun ApplicationController.open(card: Card) {
    beginDetails()
    val navigation = navigationGeneration
    runPage {
        val profile = requireNotNull(mutable.value.selectedProfile)
        val detail = readCurrentPage(navigation) { local?.detail(profile, card) ?: requireNotNull(connection).detail(card) } ?: return@runPage
        mutable.value = mutable.value.copy(detail = detail.withCardArt(card))
    }
}

/** A sparse meta (no art fields at all) must not wipe the art the catalog card
 * already had — the details hero/backdrop would render a black page. */
internal fun org.viptv.engine.wire.DetailSnapshot.withCardArt(card: Card): org.viptv.engine.wire.DetailSnapshot {
    val merged = item.copy(landscape = item.landscape ?: card.landscape, poster = item.poster ?: card.poster, logo = item.logo ?: card.logo)
    return if (merged == item) this else copy(item = merged)
}

/**
 * Play from a row without opening the title page (Continue Watching, a Home hero).
 * The source selector opens on the tap in its finding-sources state; the title's
 * Details never hold up that screen change (app#11). Back from it always goes to the
 * title's Details, whether or not they have arrived: the page loads from the tap's own
 * read and Back again leaves for the page the tap came from (app#140).
 *
 * An episode card that carries its metadata scope already says exactly what to look
 * for, so its discovery starts right away and the Details load beside it
 * ([loadSourceDetail]). Any other card needs its Details to pick the target: a movie
 * or series its title card, and an imported `library` episode the producer scope
 * (and ids) that the host's source lookup uses from Details. Its discovery starts
 * when the Details arrive, on the already open selector.
 */
fun ApplicationController.playTitle(card: Card) {
    if (card.unnumberedEpisode()) {
        mutable.value = mutable.value.copy(error = UNNUMBERED_EPISODE)
        return
    }
    val navigation = navigationGeneration
    val before = mutable.value
    // Keep the page that sources should return to, so Back does not use an older
    // visit's return target.
    val origin = before.screen.takeUnless { it in listOf(Screen.Details, Screen.Sources, Screen.Player) }
        ?: before.detailReturnScreen
    // Details already loaded for this title stay behind the selector until the fresh
    // copy replaces them; another title's Details must not.
    val keptDetail = before.detail?.takeIf { sameLookupIdentity(it.item.identity, card.parent_identity ?: card.identity) }
    releaseRemotePlayback()
    mutable.value = mutable.value.copy(detail = keptDetail, detailReturnScreen = origin, calendarEpisode = null)
    // Back from the selector opens this title's Details, arrived or not (app#140); a tap
    // that found them already loaded needs nothing read for it.
    openSources(card, returnTo = Screen.Details, playTap = card.takeIf { keptDetail == null })
    if (card.identity.episode != null && card.metadata_origin != null) {
        loadSourceDetail(card, navigation)
        run { discoverSources(card) }
        return
    }
    resolveSourceTarget(card, navigation)
}

/** The title's Details arrived for [card]: Back from sources returns to them, and sources are for [target]. */
private fun ApplicationController.showSourceDetail(detail: org.viptv.engine.wire.DetailSnapshot, card: Card, target: Card) {
    mutable.value = mutable.value.copy(detail = detail.withCardArt(card), sourceReturnScreen = Screen.Details, sourceTitle = target)
}

/**
 * The Details a play tap needs to pick its source target (a movie, a series, an imported
 * episode) are read beside the open selector, and they decide what to look for. Back while
 * they are still on their way opens the title's page, which this read fills in with no
 * second request (app#140); a failure shows the page's own error there. On the selector
 * they start the discovery in their scope.
 */
private fun ApplicationController.resolveSourceTarget(card: Card, navigation: Long) {
    cancelSourceDetail()
    val profile = mutable.value.selectedProfile ?: return
    val remote = connection
    val device = local
    val selection = cardLookupKey(card)
    fun current() = navigationGeneration == navigation && mutable.value.selectedProfile == profile &&
        connection === remote && local === device && mutable.value.screen in listOf(Screen.Sources, Screen.Details) &&
        mutable.value.sourceTitle?.let(::cardLookupKey) == selection
    sourceDetailJob = scope.launch {
        // A play tap retires the page request it interrupts, as [run] does, so work for the page
        // the viewer left cannot write over the selector before the tap's own read lands.
        job?.let { retired -> retired.cancel(); retired.join() }
        val detail = try { device?.detail(profile, card) ?: requireNotNull(remote).detail(card) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { if (current()) resolveFailed(error, remote, device, onSelector = true); return@launch }
        if (!current()) return@launch
        val target = try { sourceTarget(card, detail) } catch (error: LocalFailure) {
            if (current()) resolveFailed(error, remote, device, onSelector = true)
            return@launch
        }
        showSourceDetail(detail, card, target)
        // The viewer went Back to the title's page: filling it in was all that was left.
        if (mutable.value.screen != Screen.Sources) return@launch
        // The controller's run, not the scope function on this coroutine.
        this@resolveSourceTarget.run { discoverSources(target) }
    }
}

/** A pending Details read took too long to show the title it is for (app#140). */
private const val DETAILS_TIMEOUT = "Your server took too long to show this title. Try again."

/**
 * A play tap's Details read failed. The title's page, which Back may already have opened,
 * shows the failure and offers its Retry (app#140); the selector reports it only when its
 * sources were waiting on those Details ([onSelector]), and the player never does (app#10).
 */
private fun ApplicationController.resolveFailed(error: Exception, remote: RemoteConnection?, device: LocalConnection?, onSelector: Boolean) {
    if (error is RemoteFailure && error.status == 401 && device == null) { expireSession(remote, device); return }
    val state = mutable.value
    val message = failureMessage(error)
    when {
        state.screen == Screen.Details -> mutable.value = state.copy(error = message, findingSources = false, busy = false)
        onSelector && state.screen == Screen.Sources -> mutable.value = state.copy(error = message, findingSources = false, busy = false)
        // Only the title's page reports a read whose sources were found without it.
        state.playTapTitle != null -> mutable.value = state.copy(detailFailure = message)
    }
}

/**
 * Retry the title page a play tap opened when its Details failed to arrive (app#140). The
 * retry is the same read that Back kept, so a retried page lands where the first would have.
 */
fun ApplicationController.retryDetails() {
    val card = mutable.value.playTapTitle ?: return
    if (mutable.value.screen != Screen.Details) return
    val navigation = navigationGeneration
    mutable.value = mutable.value.copy(error = null, detailFailure = null, detailPageError = null)
    if (card.identity.episode != null && card.metadata_origin != null) loadSourceDetail(card, navigation)
    else resolveSourceTarget(card, navigation)
}

/**
 * The Details of a scoped episode whose sources are already being found. They fill
 * in without blocking the selector. When they arrive, Back from sources returns to
 * the title's Details, and the player gets the season and the next episode as it does
 * from Details (app#10). Discovery keeps running unless the episode's own metadata
 * scope turned out stale. A failed read changes nothing on the selector: sources do not
 * need it, and the discovery request itself reports an unreachable host or an expired
 * session. The one place it does change something is the title's page, which Back may
 * already have opened in its loading state (app#140): the read fills it in, or ends that
 * loading state with the page's own error.
 */
private fun ApplicationController.loadSourceDetail(card: Card, navigation: Long) {
    cancelSourceDetail()
    val profile = mutable.value.selectedProfile ?: return
    val remote = connection
    val device = local
    val selection = cardLookupKey(card)
    fun current() = navigationGeneration == navigation && mutable.value.selectedProfile == profile &&
        connection === remote && local === device && mutable.value.screen in listOf(Screen.Sources, Screen.Details, Screen.Player) &&
        mutable.value.sourceTitle?.let(::cardLookupKey) == selection
    sourceDetailJob = scope.launch {
        val detail = try {
            withTimeoutOrNull(12_000) { device?.detail(profile, card) ?: requireNotNull(remote).detail(card) }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { if (current()) resolveFailed(error, remote, device, onSelector = false); return@launch }
        if (detail == null) { if (current()) resolveFailed(LocalFailure(DETAILS_TIMEOUT), remote, device, onSelector = false); return@launch }
        if (!current()) return@launch
        val target = try { sourceTarget(card, detail) } catch (error: LocalFailure) {
            // Without the target, the selector cannot discover; on the player the host already
            // accepted this episode's scope for its sources.
            if (current()) resolveFailed(error, remote, device, onSelector = true)
            return@launch
        }
        showSourceDetail(detail, card, target)
        when (mutable.value.screen) {
            // The episode's integration moved on: find its sources again in the title's scope.
            Screen.Sources -> if (card.metadata_origin != detail.item.metadata_origin) {
                openSources(target, returnTo = Screen.Details, playTap = mutable.value.playTapTitle)
                // The controller's run, not the scope function on this coroutine.
                this@loadSourceDetail.run { discoverSources(target) }
            }
            // Playback that started first gets its season and next episode now.
            Screen.Player -> { loadNextEpisode(); loadPlaybackSeason() }
            else -> Unit
        }
    }
}

internal fun ApplicationController.cancelSourceDetail() {
    sourceDetailJob?.cancel(); sourceDetailJob = null
}

private const val UNNUMBERED_EPISODE = "Choose a numbered episode from this title’s details."

/** An episode card without a playable episode number. */
private fun Card.unnumberedEpisode() = identity.episode?.episode?.let { it <= 0L } == true

/** Start the next playback from position zero instead of resuming saved progress. */
fun ApplicationController.playTitleFromStart(card: Card) {
    restartRequested = true
    playTitle(card)
}

/** Resolve a linked identity through the server; presentation uses only its returned card. */

fun ApplicationController.openRemoteIdentity(kind: String, id: String) {
    beginDetails()
    val navigation = navigationGeneration
    runPage {
        val detail = readCurrentPage(navigation) { requireNotNull(connection).detail(kind, id) } ?: return@runPage
        mutable.value = mutable.value.copy(detail = detail)
    }
}

fun ApplicationController.chooseSource(card: Card) {
    if (card.unnumberedEpisode()) {
        mutable.value = mutable.value.copy(error = UNNUMBERED_EPISODE)
        return
    }
    // Detail heroes pass the series card, whereas episode shelves pass a concrete
    // episode. Never send a title-level series to stream providers as S0E0.
    if (card.identity.kind == "series" && card.identity.episode == null) {
        val detail = mutable.value.detail?.takeIf { it.item.identity == card.identity && it.item.provider_id == card.provider_id && it.item.metadata_origin == card.metadata_origin }
        if (detail == null) { playTitle(card); return }
        val target = try { sourceTarget(card, detail) } catch (error: LocalFailure) {
            mutable.value = mutable.value.copy(error = error.message); return
        }
        chooseSource(target); return
    }
    cancelSourceDetail()
    releaseRemotePlayback()
    openSources(card, returnTo = if (mutable.value.detail != null) Screen.Details else mutable.value.screen)
    run { discoverSources(card) }
}

/** Show the source selector for [card] in its finding-sources state; [playTap] is the title a play tap opened it for (app#140). */
private fun ApplicationController.openSources(card: Card, returnTo: Screen, playTap: Card? = null) {
    mutable.value = mutable.value.copy(screen = Screen.Sources, sourceReturnScreen = returnTo, sourceTitle = card,
        sources = emptyList(), sourceGroups = SourceGroups(), torrentOnlySources = 0, findingSources = true, navForward = emptyList(),
        playTapTitle = playTap, detailFailure = null)
}

/**
 * Find [card]'s sources inside the current [run] operation, ranking and grouping them
 * as providers answer ([ProgressiveSourceRanking]). Best match autoplay plays the first
 * Best matches row once discovery finishes; a failed ranking leaves the picker as on
 * Always ask.
 */
private suspend fun ApplicationController.discoverSources(card: Card) {
    val operation = generation
    try {
        if (mutable.value.preferences == null) loadPreferences()
        val profile = requireNotNull(mutable.value.selectedProfile)
        val device = local
        val remote = connection
        val deviceHeight = org.viptv.app.connection.deviceMaxVideoHeight()
        coroutineScope {
            val ranking = ProgressiveSourceRanking(this,
                rank = { rows -> device?.rankSources(profile, rows, deviceHeight) ?: requireNotNull(remote).rankSources(profile, rows, deviceHeight) },
                show = { ranked -> if (generation == operation) mutable.value = mutable.value.copy(sources = ranked.rows, sourceGroups = ranked.groups) })
            val outcome = try {
                device?.sources(profile, card, ranking::arrived) ?: SourcesOutcome(requireNotNull(remote).sources(card, ranking::arrived))
            } catch (deadline: TimeoutCancellationException) {
                // Rows found before the provider ceiling still take their ranked places.
                ranking.finish(); throw deadline
            }
            ranking.arrived(outcome.candidates)
            val ranked = ranking.finish()
            if (generation != operation) return@coroutineScope
            mutable.value = mutable.value.copy(torrentOnlySources = outcome.torrentOnlyProviders)
            if (mutable.value.preferences?.preferences?.source_selection == "best") ranked?.autoplayTarget()?.let { playSource(it) }
        }
    } catch (_: TimeoutCancellationException) {
        throw LocalFailure(if (mutable.value.sources.isNotEmpty())
            "Some providers did not finish within a minute. Available sources are ready to play."
        else "Providers did not respond within a minute. Try again.")
    } finally { if (generation == operation) mutable.value = mutable.value.copy(findingSources = false) }
}

/** Best match autoplay plays the first Best matches row; an empty Best matches group plays nothing (app#110). */
internal fun RankedSources.autoplayTarget(): org.viptv.engine.wire.SourceCandidate? =
    groups.bestMatches.firstOrNull()?.let { id -> rows.firstOrNull { it.id == id } }

internal fun sourceTarget(card: Card, detail: org.viptv.engine.wire.DetailSnapshot): Card {
    val delegated = card.metadata_origin != null && card.metadata_origin != detail.item.metadata_origin
    if (delegated && !publicMetadataTransition(card, detail.item))
        throw LocalFailure("This title’s integration changed. Open its details again.")
    return when {
    card.identity.episode != null -> (if (delegated) detail.episodes.find {
        it.identity.id == card.identity.id && it.identity.kind == card.identity.kind && it.identity.episode == card.identity.episode
    }?.also {
        if (it.metadata_origin != detail.item.metadata_origin || !compatiblePublicIdentity(card.identity, it.identity))
            throw LocalFailure("This episode’s metadata changed. Open its details again.")
    } else detail.episodes.find {
        it.identity == card.identity && (card.metadata_origin == null || it.metadata_origin == card.metadata_origin)
    }) ?: if (delegated || unscopedEpisodeOf(card, detail.item)) {
        // The host validates this exact episode against the complete metadata document
        // before returning a page. An off-page episode keeps its raw selector and uses
        // the actual producer, just like an episode present in the returned page.
        // A Continue Watching row names its episode under the library rather than a
        // metadata provider, and that episode is often past the first page. It takes
        // the scope of its own parent's details the same way, so the player gets that
        // title's episodes and next episode.
        card.copy(provider_id = detail.item.provider_id, metadata_origin = detail.item.metadata_origin,
            parent_identity = detail.item.identity)
    } else card
    detail.item.identity.kind == "series" -> detail.episodes.firstOrNull {
        val coordinates = it.identity.episode
        coordinates != null && (coordinates.season ?: 1L) > 0L && coordinates.episode > 0L
    } ?: throw LocalFailure("Choose an episode from this title’s details.")
    else -> detail.item
    }
}

/** An episode card without a metadata origin whose parent is exactly the title [item] describes. */
private fun unscopedEpisodeOf(card: Card, item: Card): Boolean {
    val parent = card.parent_identity ?: return false
    return card.metadata_origin == null && card.identity.kind == "series" && item.identity.episode == null &&
        parent.kind == item.identity.kind && parent.id == item.identity.id && parent.episode == null
}

/** Presentation may follow an already host-verified public lookup, never infer an alias. */
private fun publicMetadataTransition(expected: Card, actual: Card): Boolean {
    if (expected.metadata_origin?.provider_id != "stremio" || actual.metadata_origin?.provider_id != "stremio" ||
        actual.provider_id != actual.metadata_origin.connection_id) return false
    val identity = expected.identity
    val parent = expected.parent_identity
    if (!publicImdbIdentity(identity) || (parent != null && !publicImdbIdentity(parent))) return false
    if (identity.episode == null) return compatiblePublicIdentity(identity, actual.identity)
    if (actual.identity.episode != null || actual.identity.kind != "series" ||
        actual.identity.id != identity.id.substringBefore(':')) return false
    return parent == null || compatiblePublicIdentity(parent, actual.identity)
}

private fun publicImdbIdentity(identity: MediaIdentity): Boolean {
    val parts = identity.id.split(':')
    if (!Regex("tt[0-9]{1,16}").matches(parts.first()) || identity.kind !in listOf("movie", "series")) return false
    val episode = identity.episode ?: return parts.size == 1
    return parts.size == 3 && identity.kind == "series" && episode.order == "aired" &&
        episode.season != null && episode.season in 0..4294967295L && episode.episode in 1..4294967295L &&
        parts[1] == episode.season.toString() && parts[2] == episode.episode.toString()
}

private fun compatiblePublicIdentity(expected: MediaIdentity, actual: MediaIdentity): Boolean {
    if (!publicImdbIdentity(actual) || expected.id != actual.id || expected.kind != actual.kind ||
        expected.episode != actual.episode || (expected.edition != null && expected.edition != actual.edition)) return false
    fun claims(identity: MediaIdentity): Map<String, Set<String>> = identity.ids.mapNotNull { id ->
        val value = when (id.namespace) {
            "imdb" -> id.value.takeIf { Regex("tt[0-9]{1,16}").matches(it) }
            "tmdb", "tvdb", "simkl", "simkl_episode", "kitsu", "mal", "anilist" ->
                id.value.takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }
                    ?.toULongOrNull()?.takeIf { it > 0uL }?.toString()
            else -> null
        }
        value?.let { id.namespace to it }
    }.plus("imdb" to identity.id.substringBefore(':')).groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
    val actualClaims = claims(actual)
    return claims(expected).all { (namespace, values) -> values.size == 1 && actualClaims[namespace] == values }
}

internal fun ApplicationController.beginDetails() {
    // The page is the viewer's own now: a play tap's pending Details belong to the selector's page (app#140).
    cancelSourceDetail()
    val previous = mutable.value
    val origin = previous.screen.takeUnless { it in listOf(Screen.Details, Screen.Sources, Screen.Player) }
        ?: previous.detailReturnScreen
    val history = if (previous.screen == Screen.Details) previous.detailHistory + listOfNotNull(previous.detail) else emptyList()
    // Details is entered directly (not through navigate()), so record the
    // browser-style history here too; otherwise the desktop Back chevron has
    // nothing to pop on a first-level details page.
    val navBack = if (previous.screen in listOf(Screen.Details, Screen.Sources, Screen.Player)) previous.navBack
        else (previous.navBack + previous.screen).takeLast(32)
    mutable.value = previous.copy(detailReturnScreen = origin, detailHistory = history,
        navBack = navBack, navForward = emptyList(), playTapTitle = null, detailFailure = null,
        screen = Screen.Details, detail = null, calendarEpisode = null)
}
