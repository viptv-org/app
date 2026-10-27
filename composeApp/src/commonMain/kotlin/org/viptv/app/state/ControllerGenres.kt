package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.viptv.app.connection.GenreChoice
import org.viptv.app.connection.discoveryControls
import org.viptv.app.connection.genreChoices
import org.viptv.engine.wire.Scope

/**
 * The Home Genres chip's genres for [profile], read while Home showed [home] at [homeRevision].
 * Empty hides the chip.
 */
data class HomeGenres(val profile: String, val home: Scope?, val homeRevision: Long?, val choices: List<GenreChoice>)

/**
 * Home's "All" tab browses as Discover does: movies first, with the type still changeable on
 * the Browse page.
 */
private const val HOME_GENRE_TYPE = "movie"

/**
 * Fetches the genres Browse offers, once per profile and Home revision: installing or removing
 * an add-on, or switching the browsing provider, gives Home a new revision and so new genres.
 * It runs beside the page's own work, so it never cancels a Home load or shows as busy. A
 * failure leaves the chip hidden, and the next visit to Home asks again.
 */
fun ApplicationController.loadHomeGenres() {
    val state = mutable.value
    val profile = state.selectedProfile ?: return
    val home = state.home?.scope
    val revision = state.home?.revision
    if (state.homeGenres.let { it != null && it.profile == profile && it.home == home && it.homeRevision == revision }) return
    val device = local
    val remote = connection
    scope.launch {
        val controls = try {
            device?.discoveryControls(profile, HOME_GENRE_TYPE) ?: remote?.discoveryControls(HOME_GENRE_TYPE) ?: return@launch
        } catch (error: CancellationException) { throw error } catch (_: Exception) { return@launch }
        // The session or profile changed while the request was out.
        if (local !== device || connection !== remote || mutable.value.selectedProfile != profile) return@launch
        mutable.value = mutable.value.copy(homeGenres = HomeGenres(profile, home, revision, controls.genreChoices(HOME_GENRE_TYPE)))
    }
}

/** Opens Browse filtered to [choice]. Back returns to the page the chip was on. */
fun ApplicationController.browseGenre(choice: GenreChoice) = navigate(Screen.Discover, browse = choice.query)
