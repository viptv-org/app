package org.viptv.app

import org.viptv.app.features.player.TvSkipKind
import org.viptv.app.features.player.TvSkipRange
import org.viptv.app.player.PlaybackInput
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.PlaybackSegments
import org.viptv.app.state.currentPlayback
import org.viptv.app.state.playbackKey
import org.viptv.engine.wire.*
import kotlin.test.*

class CurrentPlaybackTest {
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(number: Long) = Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        metadata = CardMetadata(), provider_id = "addon:7", title = "Episode $number", badges = emptyList(),
        parent_identity = parent, metadata_origin = origin)
    private fun source(id: String) = SourceCandidate(id, "addon:7", "Source $id", detail = null)
    private val credits = listOf(TvSkipRange(1_700_000, 1_790_000, TvSkipKind.Credits))
    private fun input(card: Card, source: SourceCandidate, position: Long = 1_750_000) =
        PlaybackInput("lease-${card.title}-${source.id}-$position", "https://media.invalid/v", positionMillis = position, owner = playbackKey(card, source))

    @Test fun theNextEpisodeNeverSeesThePreviousEpisodesInputOrSegments() {
        val first = input(episode(1), source("a"))
        val playing = ApplicationState(sourceTitle = episode(1), playingSource = source("a"), playback = first,
            playbackSegments = PlaybackSegments(first, credits))
        assertSame(first, playing.currentPlayback().input)
        assertEquals(credits, playing.currentPlayback().segments)

        val switching = playing.copy(sourceTitle = episode(2), playingSource = source("b"))
        val current = switching.currentPlayback()
        assertNotEquals(playing.currentPlayback().key, current.key)
        assertNull(current.input, "Episode 2's player must not be seeded from episode 1's input")
        assertTrue(current.segments.isEmpty())

        val sameSourceId = playing.copy(sourceTitle = episode(2))
        assertNull(sameSourceId.currentPlayback().input, "A source id shared across episodes is not the same playback")
    }

    @Test fun anotherSourceOfTheSameEpisodeStartsItsOwnPlayback() {
        val first = input(episode(1), source("a"))
        val state = ApplicationState(sourceTitle = episode(1), playingSource = source("b"), playback = first,
            playbackSegments = PlaybackSegments(first, credits))
        assertNull(state.currentPlayback().input)
        assertTrue(state.currentPlayback().segments.isEmpty())
    }

    @Test fun aSeekReopenOfTheSameEpisodeKeepsItsKeyAndUsesOnlyItsOwnSegments() {
        val first = input(episode(1), source("a"), position = 600_000)
        val before = ApplicationState(sourceTitle = episode(1), playingSource = source("a"), playback = first,
            playbackSegments = PlaybackSegments(first, credits))
        val reopened = input(episode(1), source("a"), position = 1_200_000)
        val after = before.copy(playback = reopened)
        assertEquals(before.currentPlayback().key, after.currentPlayback().key)
        assertSame(reopened, after.currentPlayback().input)
        assertTrue(after.currentPlayback().segments.isEmpty(), "Segments are reported per input")
        assertEquals(credits, after.copy(playbackSegments = PlaybackSegments(reopened, credits)).currentPlayback().segments)
    }
}
