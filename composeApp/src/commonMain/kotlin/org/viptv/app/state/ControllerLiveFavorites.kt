package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*

internal suspend fun ApplicationController.loadLiveFavorites(context: LiveContext = liveContext()) {
    val version = liveFavoriteVersion
    try {
        val saved = context.device?.liveFavorites(context.profile) ?: requireNotNull(context.remote).liveFavorites(context.profile)
        if (ownsLive(context, false)) updateState { next -> next.copy(
            liveFavorites = if (liveFavoriteVersion == version) saved else next.liveFavorites,
            liveFavoritesAvailable = true, liveFavoritesCanRetry = false, liveFavoriteError = null) }
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) {
        if (error is RemoteFailure && error.status == 401) throw error
        val unsupported = error is UnsupportedOperationException || error is RemoteFailure && error.status == 404
        if (ownsLive(context, false)) updateState { next -> next.copy(liveFavoritesAvailable = false,
            liveFavoritesCanRetry = !unsupported,
            liveFavoriteError = if (unsupported) "Channel favourites are unavailable for this connection." else "Could not load channel favourites.") }
    }
}
fun ApplicationController.refreshLiveFavorites() = run { loadLiveFavorites() }

fun ApplicationController.toggleLiveFavorite(channel: String) {
    val before = mutable.value
    if (before.screen != Screen.Live || !before.liveFavoritesAvailable || before.livePage?.channels?.none { it.id == channel } != false) return
    val context = liveContext()
    val saved = !before.isLiveFavorite(channel)
    mutate(liveFavoriteTarget(channel),
        optimistic = { it.withLiveFavorite(channel, saved) },
        revert = { it.withLiveFavorite(channel, !saved) }) {
        val result = context.device?.setLiveFavorite(context.profile, channel, saved) ?: requireNotNull(context.remote).setLiveFavorite(context.profile, channel, saved)
        if (ownsLive(context, false)) {
            liveFavoriteVersion++
            updateState { next -> next.copy(
                liveFavorites = next.liveFavorites.withLiveFavorite(channel, channel in result),
                liveFavoriteError = null) }
            if (ownsLive(context) && mutable.value.liveFavoritesOnly && mutable.value.liveFavorites.isNotEmpty() &&
                mutable.value.livePage?.channels?.none { it.id in mutable.value.liveFavorites } == true && mutable.value.livePage?.next_cursor != null)
                moreLive()
        }
    }
}

internal fun liveFavoriteTarget(channel: String) = MutationTarget(MutationKind.LiveFavorite, channel)

internal fun ApplicationState.isLiveFavorite(channel: String): Boolean = channel in liveFavorites

internal fun ApplicationState.withLiveFavorite(channel: String, favorite: Boolean): ApplicationState =
    copy(liveFavorites = liveFavorites.withLiveFavorite(channel, favorite))

private fun Set<String>.withLiveFavorite(channel: String, favorite: Boolean): Set<String> = if (favorite) this + channel else this - channel

// A profile or source change drops the writes it can no longer show.
internal fun ApplicationController.cancelLiveFavoriteMutations() {
    val targets = mutationJobs.keys.filter { it.kind == MutationKind.LiveFavorite }
    if (targets.isEmpty()) return
    targets.forEach { mutationJobs.remove(it)?.cancel() }
    updateState { state -> state.copy(mutations = PendingMutations(state.mutations.entries - targets.toSet())) }
}