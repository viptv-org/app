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
    else mutable.value = mutable.value.copy(home = refreshed.home, homeRowBusy = emptySet(), homeRowErrors = emptyMap())
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
    val homeScope = home.scope
    val operation = generation
    fun current() = generation == operation && mutable.value.screen == Screen.Home &&
        mutable.value.selectedProfile == profile && mutable.value.home?.scope == homeScope &&
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
            if (rowId in state.homeRowBusy && state.home?.scope == homeScope)
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

fun ApplicationController.open(card: Card) {
    beginDetails()
    val navigation = navigationGeneration
    runPage {
        val profile = requireNotNull(mutable.value.selectedProfile)
        val detail = readCurrentPage(navigation) { local?.detail(profile, card) ?: requireNotNull(connection).detail(card) } ?: return@runPage
        mutable.value = mutable.value.copy(detail = detail)
    }
}
/** Resolve provider metadata before source discovery, including the native identity evidence cache. */
fun ApplicationController.playTitle(card: Card) {
    val navigation = navigationGeneration
    // Play can be pressed from a row without opening the title page. Keep the
    // page that sources should return to, so Back does not use an older visit's
    // return target.
    val origin = mutable.value.screen.takeUnless { it in listOf(Screen.Details, Screen.Sources, Screen.Player) }
        ?: mutable.value.detailReturnScreen
    runPage {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val detail = readCurrentPage(navigation) { local?.detail(profile, card) ?: requireNotNull(connection).detail(card) } ?: return@runPage
    mutable.value = mutable.value.copy(detail = detail, detailReturnScreen = origin)
    val target = sourceTarget(card, detail)
    chooseSource(target)
    }
}

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
    if (card.identity.episode?.episode?.let { it <= 0L } == true) {
        mutable.value = mutable.value.copy(error = "Choose a numbered episode from this title’s details.")
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
    releaseRemotePlayback()
    val origin = if (mutable.value.detail != null) Screen.Details else mutable.value.screen
    mutable.value = mutable.value.copy(screen = Screen.Sources, sourceReturnScreen = origin, sourceTitle = card,
        sources = emptyList(), torrentOnlySources = 0, findingSources = true, navForward = emptyList())
    run {
        val operation = generation
        try {
            if (mutable.value.preferences == null) loadPreferences()
            val publish: (List<org.viptv.engine.wire.SourceCandidate>) -> Unit = { partial ->
                if (generation == operation) mutable.value = mutable.value.copy(sources = partial)
            }
            val outcome = local?.sources(requireNotNull(mutable.value.selectedProfile), card, publish)
                ?: SourcesOutcome(requireNotNull(connection).sources(card, publish))
            val sources = outcome.candidates
            val profile = requireNotNull(mutable.value.selectedProfile)
            val ranked = local?.rankSources(profile, sources) ?: requireNotNull(connection).rankSources(profile, sources)
            mutable.value = mutable.value.copy(sources = ranked, torrentOnlySources = outcome.torrentOnlyProviders)
            if (mutable.value.preferences?.preferences?.source_selection == "best" && ranked.isNotEmpty()) playSource(ranked.first())
        } catch (_: TimeoutCancellationException) {
            throw LocalFailure(if (mutable.value.sources.isNotEmpty())
                "Some providers did not finish within a minute. Available sources are ready to play."
            else "Providers did not respond within a minute. Try again.")
        } finally { if (generation == operation) mutable.value = mutable.value.copy(findingSources = false) }
    }
}

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
    }) ?: if (delegated) {
        // The host validates this exact episode against the complete metadata document
        // before returning a page. An off-page episode keeps its raw selector and uses
        // the actual producer, just like an episode present in the returned page.
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
        navBack = navBack, navForward = emptyList(),
        screen = Screen.Details, detail = null)
}
