package org.viptv.app

import org.viptv.app.player.*
import org.viptv.app.connection.requiredAudioIndex
import kotlinx.serialization.json.*
import kotlin.test.*

class RequiredAudioSelectionTest {
    @Test fun absentRequirementRemainsCompatibleButInvalidRequirementCannotBecomeDefaultAudio() {
        assertNull(buildJsonObject {}.requiredAudioIndex())
        for (value in listOf(-1L, Int.MAX_VALUE.toLong() + 1)) {
            assertFailsWith<IllegalArgumentException> { buildJsonObject { put("required_audio_index", value) }.requiredAudioIndex() }
        }
    }

    @Test fun resolvesAudioOrdinalsToPlayerIdsAndPreservesManualSelection() {
        val selection = RequiredAudioSelection(1)
        assertEquals(RequiredAudioChoice.Unchanged, selection.choose(emptyList()))
        val candidates = listOf(AudioCandidate(2), AudioCandidate(7), AudioCandidate(12))
        assertEquals(RequiredAudioChoice.Select(7), selection.choose(candidates))
        assertEquals(RequiredAudioChoice.Unchanged, selection.choose(candidates))
        selection.userSelected()
        assertEquals(RequiredAudioChoice.Unchanged, selection.choose(listOf(AudioCandidate(20), AudioCandidate(30))))
    }

    @Test fun unsupportedAndAbsentRequiredTracksNeverShiftToAnotherAudioStream() {
        val candidates = listOf(AudioCandidate(4, false), AudioCandidate(8), AudioCandidate(12))
        assertEquals(RequiredAudioChoice.Unavailable, RequiredAudioSelection(0).choose(candidates))
        assertEquals(RequiredAudioChoice.Select(8), RequiredAudioSelection(1).choose(candidates))
        assertEquals(RequiredAudioChoice.Unavailable, RequiredAudioSelection(3).choose(candidates))
        assertEquals(RequiredAudioChoice.Unavailable, RequiredAudioSelection(0).choose(emptyList(), tracksKnown = true))
        assertEquals(RequiredAudioChoice.Unchanged, RequiredAudioSelection(null).choose(candidates))
    }
}
