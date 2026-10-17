package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*

internal suspend fun ApplicationController.loadLiveFavorites(context: LiveContext = liveContext()) {
    val version = liveFavoriteVersion
    try {
        val saved = context.device?.liveFavorites(context.profile) ?: requireNotNull(context.remote).liveFavorites(context.profile)
        if (ownsLive(context, false)) mutable.value = mutable.value.copy(
            liveFavorites = if (liveFavoriteVersion == version) saved else mutable.value.liveFavorites,
            liveFavoritesAvailable = true, liveFavoritesCanRetry = false, liveFavoriteError = null)
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) {
        if (error is RemoteFailure && error.status == 401) throw error
        val unsupported = error is UnsupportedOperationException || error is RemoteFailure && error.status == 404
        if (ownsLive(context, false)) mutable.value = mutable.value.copy(liveFavoritesAvailable = false,
            liveFavoritesCanRetry = !unsupported,
            liveFavoriteError = if (unsupported) "Channel favourites are unavailable for this connection." else "Could not load channel favourites.")
    }
}
fun ApplicationController.refreshLiveFavorites() = run { loadLiveFavorites() }

fun ApplicationController.toggleLiveFavorite(channel: String) {
    val before = mutable.value
    if (before.screen != Screen.Live || !before.liveFavoritesAvailable || channel in before.liveFavoritePending || before.livePage?.channels?.none { it.id == channel } != false) return
    val context = liveContext()
    val saved = channel !in before.liveFavorites
    mutable.value = before.copy(liveFavoritePending = before.liveFavoritePending + channel, liveFavoriteError = null)
    val task = scope.launch(start = CoroutineStart.LAZY) {
        try {
            val result = context.device?.setLiveFavorite(context.profile, channel, saved) ?: requireNotNull(context.remote).setLiveFavorite(context.profile, channel, saved)
            if (ownsLive(context, false)) {
                liveFavoriteVersion++
                val current = mutable.value.liveFavorites
                // Sibling writes can return snapshots out of order. Only this
                // mutation's channel is authoritative for this response.
                mutable.value = mutable.value.copy(liveFavorites = if (channel in result) current + channel else current - channel, liveFavoriteError = null)
                if (ownsLive(context) && mutable.value.liveFavoritesOnly && mutable.value.liveFavorites.isNotEmpty() &&
                    mutable.value.livePage?.channels?.none { it.id in mutable.value.liveFavorites } == true && mutable.value.livePage?.next_cursor != null)
                    moreLive()
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (ownsLive(context, false)) {
                if (error is RemoteFailure && error.status == 401) run { throw error }
                else mutable.value = mutable.value.copy(liveFavoriteError = "Could not save the channel favourite. Try again.", liveFavoritesCanRetry = true)
            }
        } finally {
            val current = currentCoroutineContext()[Job]
            if (liveFavoriteJobs[channel] === current) {
                if (mutable.value.selectedProfile == context.profile && connection === context.remote && local === context.device)
                    mutable.value = mutable.value.copy(liveFavoritePending = mutable.value.liveFavoritePending - channel)
                liveFavoriteJobs.remove(channel)
            }
        }
    }
    liveFavoriteJobs[channel] = task
    task.start()
}
