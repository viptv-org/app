package org.viptv.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.viptv.app.connection.*
import org.viptv.app.player.PlayerFact
import org.viptv.app.player.PresentationProof
import org.viptv.app.state.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.SourceCandidate
import kotlin.test.*

class SeriesMemoryTest {
    private val series = MediaIdentity("show", "series", emptyList())
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun episode(number: Long) = Card(
        MediaIdentity("show", "series", emptyList(), episode = EpisodeCoordinates("s1e$number", 1, number)),
        provider_id = "stremio", title = "Episode $number", badges = emptyList(), parent_identity = series)

    private fun controller() = ApplicationController(
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), store, startDeadlineMillis = 60_000)

    @Test fun startedEpisodeIsRememberedForLaterEpisodesOfTheSameSeriesAndProfile() {
        val controller = controller()
        controller.rememberStartedSource("default", episode(1), SourceCandidate("s", "addon:1", "S", binge_group = "group-1"))
        assertEquals(SeriesMemory("addon:1", "group-1"), controller.seriesMemoryOf("default", episode(2)))
        assertNull(controller.seriesMemoryOf("other", episode(2)))
        assertNull(controller.seriesMemoryOf("default", episode(2).copy(parent_identity = null,
            identity = MediaIdentity("film", "movie", emptyList()))))
    }

    @Test fun onlyARowWithABingeGroupIsRememberedAndTheLastOneWins() {
        val controller = controller()
        controller.rememberStartedSource("default", episode(1), SourceCandidate("s", "addon:1", "S"))
        assertNull(controller.seriesMemoryOf("default", episode(2)))
        controller.rememberStartedSource("default", episode(1), SourceCandidate("s", "addon:1", "S", binge_group = "group-1"))
        controller.rememberStartedSource("default", episode(2), SourceCandidate("t", "addon:2", "T", binge_group = "group-2"))
        assertEquals(SeriesMemory("addon:2", "group-2"), controller.seriesMemoryOf("default", episode(3)))
    }

    @Test fun anEpisodeWithoutAParentIdentityStillKeysTheSeries() {
        val controller = controller()
        val unparented = episode(4).copy(parent_identity = null)
        controller.rememberStartedSource("default", unparented, SourceCandidate("s", "addon:1", "S", binge_group = "group-1"))
        assertEquals(SeriesMemory("addon:1", "group-1"), controller.seriesMemoryOf("default", episode(5)))
    }

    @Test fun theFirstFrameRemembersTheStartedRow() {
        val controller = controller()
        val source = SourceCandidate("s", "addon:1", "S", binge_group = "group-1")
        controller.mutable.value = ApplicationState(selectedProfile = "default", sourceTitle = episode(1), playingSource = source)
        controller.observePlayback(PlayerFact.Presentation(PresentationProof.DesktopDrawCompleted))
        assertEquals(SeriesMemory("addon:1", "group-1"), controller.seriesMemoryOf("default", episode(2)))
    }
}
