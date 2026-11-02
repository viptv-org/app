package org.viptv.app.state

import androidx.compose.runtime.Immutable
import org.viptv.app.connection.LocalConnection
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.SourceGroups
import org.viptv.app.connection.cardLookupKey
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.Scope
import org.viptv.engine.wire.SourceCandidate

internal const val NEXT_EPISODE_PREPARE_WITHIN_MILLIS = 180_000L
internal const val PREPARED_NEXT_EPISODE_TTL_MILLIS = 15 * 60_000L

enum class NextEpisodePreparationStatus { Preparing, Ready, Unavailable }

@Immutable
data class NextEpisodePreparation(
    val card: Card,
    val profile: String,
    val status: NextEpisodePreparationStatus,
    val source: SourceCandidate? = null,
    val sources: List<SourceCandidate> = emptyList(),
    val groups: SourceGroups = SourceGroups(),
    val startedAtMillis: Long = 0,
    val readyAtMillis: Long? = null,
)

internal data class NextEpisodePreparationKey(
    val card: String,
    val profile: String,
    val preferenceRevision: Long,
    val sourceSelection: String,
    val remote: RemoteConnection?,
    val local: LocalConnection?,
    val homeScope: Scope?,
)

internal fun nextEpisodePreparationDue(positionMillis: Long, durationMillis: Long?): Boolean {
    val duration = durationMillis?.takeIf { it > 0 } ?: return false
    if (positionMillis <= 0 || positionMillis >= duration) return false
    return duration - positionMillis <= NEXT_EPISODE_PREPARE_WITHIN_MILLIS
}

internal fun ApplicationState.nextEpisodePreparationTarget(): Card? {
    if (screen != Screen.Player || nextEpisodeLoading) return null
    val playing = playback ?: return null
    val profile = selectedProfile ?: return null
    if (playing.live || playing.profileId != profile) return null
    val card = sourceTitle ?: return null
    if (card.identity.kind != "series" || card.identity.episode == null || card.parent_identity == null) return null
    if (preferences?.preferences?.source_selection != "best") return null
    return nextEpisode
}

internal fun ApplicationController.preparationKeyFor(card: Card, state: ApplicationState = mutable.value): NextEpisodePreparationKey {
    val snapshot = state.preferences
    return NextEpisodePreparationKey(cardLookupKey(card), state.selectedProfile.orEmpty(), snapshot?.revision ?: 0,
        snapshot?.preferences?.source_selection.orEmpty(), connection, local, state.home?.scope?.owner())
}