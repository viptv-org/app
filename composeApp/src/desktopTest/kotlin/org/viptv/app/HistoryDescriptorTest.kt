package org.viptv.app

import org.viptv.app.connection.HistoryDisplay
import org.viptv.app.features.history.descriptor
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.HistoryEntry
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryDescriptorTest {
    private val series = Card(MediaIdentity("tt570", "series", emptyList()), provider_id = "addon:21",
        title = "Fixture show", badges = emptyList())

    private fun display(episode: EpisodeCoordinates?) = HistoryDisplay(
        HistoryEntry(series.copy(identity = MediaIdentity("tt570:1:7", "series", emptyList(), episode)), 1L), "Today")

    @Test fun anAbsentSeasonNumberIsLabelledSeasonOne() {
        assertEquals("S1 · E7", display(EpisodeCoordinates("aired", null, 7)).descriptor())
        assertEquals("S2 · E7", display(EpisodeCoordinates("aired", 2, 7)).descriptor())
    }

    @Test fun aTitleWithoutCoordinatesFallsBackToItsKind() {
        assertEquals("Series", display(null).descriptor())
    }
}