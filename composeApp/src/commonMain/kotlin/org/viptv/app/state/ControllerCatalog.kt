package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.RowState

fun ApplicationController.refresh() = run {
    progressJob?.join()
    val profile = mutable.value.selectedProfile
    val device = local
    val result = device?.bootstrap(profile) ?: requireNotNull(connection).bootstrap()
    val refreshed = if (profile != null && result.selected == profile && device != null)
        result.copy(home = device.refreshHome(profile)) else result
    currentCoroutineContext().ensureActive()
    if (mutable.value.screen == Screen.Home || refreshed.selected != profile) accept(refreshed, busy = true)
    else {
        updateState { it.copy(home = refreshed.home, homeRowBusy = emptySet(), homeRowErrors = emptyMap()) }
        adoptContinuation(refreshed.continuation)
        freshness.homeBuilt(refreshed.revisions)
    }
}

private const val HOME_ROW_LIMIT = 240

fun ApplicationController.homeRowPage(rowId: String) {
    val home = mutable.value.home ?: return
    val row = home.rows.find { it.id == rowId } ?: return
    val cursor = row.next_cursor ?: return
    if (row.state != RowState.READY || rowId in mutable.value.homeRowBusy) return
    val profile = mutable.value.selectedProfile ?: return
    val query = row.pageQuery() ?: return
    if (row.cards.size >= HOME_ROW_LIMIT) {
        updateState { state ->
            val shown = state.home ?: return@updateState state
            state.copy(home = shown.copy(rows = shown.rows.map { row -> if (row.id == rowId) row.copy(next_cursor = null) else row }))
        }
        return
    }
    val remote = connection
    val device = local
    val homeScope = home.scope.owner()
    val operation = generation
    fun current() = generation == operation && mutable.value.screen == Screen.Home &&
        mutable.value.selectedProfile == profile && mutable.value.home?.scope?.owner() == homeScope &&
        connection === remote && local === device
    updateState { it.copy(homeRowBusy = it.homeRowBusy + rowId, homeRowErrors = it.homeRowErrors - rowId) }
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
            val next = if (merged.size > target.cards.size) advancedCursor(page.next_cursor, cursor) else null
            updateState { state ->
                val home = state.home ?: return@updateState state
                state.copy(home = home.copy(rows = home.rows.map { row ->
                    if (row.id == rowId) row.copy(cards = merged, next_cursor = next,
                        state = if (merged.isEmpty()) RowState.EMPTY else RowState.READY) else row }))
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (current()) {
                if (error is RemoteFailure && error.status == 401 && device == null) {
                    releaseRemotePlayback(); remote?.close(); connection = null
                    mutable.value = ApplicationState(error = "Session expired. Sign in again.")
                } else updateState { it.copy(
                    homeRowErrors = it.homeRowErrors + (rowId to "Could not load more titles. Try again.")) }
            }
        } finally {
            updateState { state ->
                if (rowId in state.homeRowBusy && state.home?.scope?.owner() == homeScope)
                    state.copy(homeRowBusy = state.homeRowBusy - rowId) else state
            }
        }
    }
}

private fun advancedCursor(next: String?, current: String): String? {
    next ?: return null
    if (next == current) return null
    val candidate = next.toIntOrNull()
    val from = current.toIntOrNull()
    return if (candidate != null && from != null && candidate <= from) null else next
}

fun ApplicationController.editSearch(edit: (String) -> String) = search(edit(mutable.value.searchTerm))

fun ApplicationController.search(term: String) {
    updateState { it.copy(searchTerm = term, results = emptyList(), searchRows = emptyList()) }
    rememberNavigation()
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
            updateState { it.copy(results = cards, searchRows = grouped?.rows.orEmpty()) }
        }
    }
}

private val summaryRequested = mutableSetOf<String>()

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
        if (merged != card) updateState { it.copy(summaryCards = it.summaryCards + (card.identity.id to merged)) }
    }
}

fun ApplicationController.open(card: Card) {
    beginDetails()
    val navigation = navigationGeneration
    // The tapped card already carries the hero: draw the shell from it while the meta read is in flight.
    updateState { it.copy(detail = DetailSnapshot(item = card, episodes = emptyList()), detailShell = true) }
    runPage { readDetail(card, navigation) }
}

private suspend fun ApplicationController.readDetail(card: Card, navigation: Long) {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val detail = readCurrentPage(navigation) { local?.detail(profile, card) ?: requireNotNull(connection).detail(card) } ?: return
    updateState { it.copy(detail = detail.withCardArt(card), detailShell = false) }
    rememberNavigation()
}

// A sparse meta must not wipe the art the card already has: the details hero would render a black page.
internal fun org.viptv.engine.wire.DetailSnapshot.withCardArt(card: Card): org.viptv.engine.wire.DetailSnapshot {
    val merged = item.copy(landscape = item.landscape ?: card.landscape, poster = item.poster ?: card.poster, logo = item.logo ?: card.logo)
    return if (merged == item) this else copy(item = merged)
}

fun ApplicationController.playTitle(card: Card) {
    if (card.unnumberedEpisode()) {
        updateState { it.copy(error = UNNUMBERED_EPISODE) }
        return
    }
    val navigation = navigationGeneration
    val before = mutable.value
    val origin = before.screen.takeUnless { it in listOf(Screen.Details, Screen.Sources, Screen.Player) }
        ?: before.detailReturnScreen
    val keptDetail = before.detail?.takeIf { !before.detailShell && sameLookupIdentity(it.item.identity, card.parent_identity ?: card.identity) }
    releaseRemotePlayback()
    updateState { it.copy(detail = keptDetail, detailReturnScreen = origin, calendarEpisode = null, detailShell = false) }
    openSources(card, returnTo = Screen.Details, playTap = card.takeIf { keptDetail == null })
    if (card.identity.episode != null && card.metadata_origin != null) {
        loadSourceDetail(card, navigation)
        run { discoverSources(card) }
        return
    }
    resolveSourceTarget(card, navigation)
}

private fun ApplicationController.showSourceDetail(detail: org.viptv.engine.wire.DetailSnapshot, card: Card, target: Card) {
    updateState { it.copy(detail = detail.withCardArt(card), sourceReturnScreen = Screen.Details, sourceTitle = target, detailShell = false) }
}

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
        if (mutable.value.screen != Screen.Sources) return@launch
        // The controller's run, not the scope function: it cancels the job and sets `busy`.
        this@resolveSourceTarget.run { discoverSources(target) }
    }
}

private const val DETAILS_TIMEOUT = "Your server took too long to show this title. Try again."

private fun ApplicationController.resolveFailed(error: Exception, remote: RemoteConnection?, device: LocalConnection?, onSelector: Boolean) {
    if (error is RemoteFailure && error.status == 401 && device == null) { expireSession(remote, device); return }
    val message = failureMessage(error)
    val retryable = error !is RemoteFailure || !error.metadataNotFound
    updateState { state ->
        when {
            state.screen == Screen.Details -> state.copy(error = message, findingSources = false, busy = false, detailRetryable = retryable)
            onSelector && state.screen == Screen.Sources -> state.copy(error = message, findingSources = false, busy = false, detailRetryable = retryable)
            state.playTapTitle != null -> state.copy(detailFailure = message, detailRetryable = retryable)
            else -> state
        }
    }
}

fun ApplicationController.retryDetails() {
    val state = mutable.value
    if (state.screen != Screen.Details) return
    val card = state.playTapTitle ?: state.detail?.item ?: return
    val navigation = navigationGeneration
    updateState { it.copy(error = null, detailFailure = null, detailPageError = null, detailRetryable = true) }
    if (state.playTapTitle != null) {
        if (card.identity.episode != null && card.metadata_origin != null) loadSourceDetail(card, navigation)
        else resolveSourceTarget(card, navigation)
    } else {
        updateState { it.copy(detailShell = true) }
        runPage { readDetail(card, navigation) }
    }
}

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
            if (current()) resolveFailed(error, remote, device, onSelector = true)
            return@launch
        }
        showSourceDetail(detail, card, target)
        when (mutable.value.screen) {
            Screen.Sources -> if (card.metadata_origin != detail.item.metadata_origin) {
                openSources(target, returnTo = Screen.Details, playTap = mutable.value.playTapTitle)
                // The controller's run, not the scope function: it cancels the job and sets `busy`.
                this@loadSourceDetail.run { discoverSources(target) }
            }
            Screen.Player -> { loadNextEpisode(); loadPlaybackSeason() }
            else -> Unit
        }
    }
}

internal fun ApplicationController.cancelSourceDetail() {
    sourceDetailJob?.cancel(); sourceDetailJob = null
}

private const val UNNUMBERED_EPISODE = "Choose a numbered episode from this title’s details."

private fun Card.unnumberedEpisode() = identity.episode?.episode?.let { it <= 0L } == true

fun ApplicationController.playTitleFromStart(card: Card) {
    restartRequested = true
    playTitle(card)
}

fun ApplicationController.openRemoteIdentity(kind: String, id: String) {
    beginDetails()
    val navigation = navigationGeneration
    runPage {
        val detail = readCurrentPage(navigation) { requireNotNull(connection).detail(kind, id) } ?: return@runPage
        updateState { it.copy(detail = detail) }
    }
}

fun ApplicationController.chooseSource(card: Card) {
    if (card.unnumberedEpisode()) {
        updateState { it.copy(error = UNNUMBERED_EPISODE) }
        return
    }
    // A title-level series must never reach stream providers as S0E0.
    if (card.identity.kind == "series" && card.identity.episode == null) {
        val detail = mutable.value.detail?.takeIf { !mutable.value.detailShell && it.item.identity == card.identity && it.item.provider_id == card.provider_id && it.item.metadata_origin == card.metadata_origin }
        if (detail == null) { playTitle(card); return }
        val target = try { sourceTarget(card, detail) } catch (error: LocalFailure) {
            updateState { it.copy(error = error.message) }; return
        }
        chooseSource(target); return
    }
    cancelSourceDetail()
    releaseRemotePlayback()
    openSources(card, returnTo = if (mutable.value.detail != null) Screen.Details else mutable.value.screen)
    run { discoverSources(card) }
}

private fun ApplicationController.openSources(card: Card, returnTo: Screen, playTap: Card? = null) {
    updateState { it.copy(screen = Screen.Sources, sourceReturnScreen = returnTo, sourceTitle = card,
        sources = emptyList(), sourceGroups = SourceGroups(), torrentOnlySources = 0, findingSources = true, navForward = emptyList(),
        playTapTitle = playTap, detailFailure = null, detailRetryable = true) }
}

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
                show = { ranked -> if (generation == operation) updateState { it.copy(sources = ranked.rows, sourceGroups = ranked.groups) } })
            val outcome = try {
                device?.sources(profile, card, ranking::arrived) ?: SourcesOutcome(requireNotNull(remote).sources(card, ranking::arrived))
            } catch (deadline: TimeoutCancellationException) {
                ranking.finish(); throw deadline
            }
            ranking.arrived(outcome.candidates)
            val ranked = ranking.finish()
            if (generation != operation) return@coroutineScope
            updateState { it.copy(torrentOnlySources = outcome.torrentOnlyProviders) }
            if (mutable.value.preferences?.preferences?.source_selection == "best") ranked?.autoplayTarget()?.let { playSource(it) }
        }
    } catch (_: TimeoutCancellationException) {
        throw LocalFailure(if (mutable.value.sources.isNotEmpty())
            "Some providers did not finish within a minute. Available sources are ready to play."
        else "Providers did not respond within a minute. Try again.")
    } finally { if (generation == operation) updateState { it.copy(findingSources = false) } }
}

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

private fun unscopedEpisodeOf(card: Card, item: Card): Boolean {
    val parent = card.parent_identity ?: return false
    return card.metadata_origin == null && card.identity.kind == "series" && item.identity.episode == null &&
        parent.kind == item.identity.kind && parent.id == item.identity.id && parent.episode == null
}

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
    cancelSourceDetail()
    updateState { previous ->
        val origin = previous.screen.takeUnless { it in listOf(Screen.Details, Screen.Sources, Screen.Player) }
            ?: previous.detailReturnScreen
        val history = if (previous.screen == Screen.Details) previous.detailHistory + listOfNotNull(previous.detail) else emptyList()
        val navBack = if (previous.screen in listOf(Screen.Details, Screen.Sources, Screen.Player)) previous.navBack
            else (previous.navBack + previous.screen).takeLast(32)
        previous.copy(detailReturnScreen = origin, detailHistory = history,
            navBack = navBack, navForward = emptyList(), playTapTitle = null, detailFailure = null, detailRetryable = true,
            screen = Screen.Details, detail = null, detailShell = false, calendarEpisode = null)
    }
}
