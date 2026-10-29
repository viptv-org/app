package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.viptv.app.connection.GenreChoice
import org.viptv.app.connection.discoveryControls
import org.viptv.app.connection.genreChoices
import org.viptv.engine.wire.Scope

data class HomeGenres(val profile: String, val home: Scope?, val homeRevision: Long?, val choices: List<GenreChoice>)

private const val HOME_GENRE_TYPE = "movie"

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
        if (local !== device || connection !== remote || mutable.value.selectedProfile != profile) return@launch
        mutable.value = mutable.value.copy(homeGenres = HomeGenres(profile, home, revision, controls.genreChoices(HOME_GENRE_TYPE)))
    }
}

fun ApplicationController.browseGenre(choice: GenreChoice) = navigate(Screen.Discover, browse = choice.query)
