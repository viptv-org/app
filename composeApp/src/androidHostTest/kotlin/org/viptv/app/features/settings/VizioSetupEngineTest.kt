package org.viptv.app.features.settings

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.viptv.engine.wire.VizioControllerOutputKind
import org.viptv.engine.wire.VizioFailureKind
import org.viptv.engine.wire.VizioPairingChallenge

class VizioSetupEngineTest {
    @Test fun parsesAChallengeFromTheCompleteResult() {
        val challenge = vizioChallenge(buildJsonObject { put("challengeType", 1); put("token", 4294967295L) })
        assertEquals(VizioPairingChallenge(1, 4294967295L), challenge)
        assertNull(vizioChallenge(null))
        assertNull(vizioChallenge(buildJsonObject { put("challengeType", 1) }))
    }

    @Test fun parsesAChallengeFromDecimalNumbers() {
        val challenge = vizioChallenge(buildJsonObject { put("challengeType", 1.0); put("token", 4294967295.0) })
        assertEquals(VizioPairingChallenge(1, 4294967295L), challenge)
    }

    @Test fun rejectsANonIntegralOrOutOfRangeChallenge() {
        assertNull(vizioChallenge(buildJsonObject { put("challengeType", 1.5); put("token", 42.0) }))
        assertNull(vizioChallenge(buildJsonObject { put("challengeType", 65536.0); put("token", 42.0) }))
        assertNull(vizioChallenge(buildJsonObject { put("challengeType", 1.0); put("token", 4294967296.0) }))
        assertNull(vizioChallenge(buildJsonObject { put("challengeType", 1.0); put("token", -1.0) }))
    }

    @Test fun mapsPairingOutcomesToResults() {
        assertEquals(VizioPairResult.Paired, vizioFinishResult(VizioControllerOutputKind.COMPLETE, null))
        assertEquals(VizioPairResult.Rejected, vizioFinishResult(VizioControllerOutputKind.ERROR, VizioFailureKind.AUTHENTICATION))
        assertEquals(VizioPairResult.Rejected, vizioFinishResult(VizioControllerOutputKind.ERROR, VizioFailureKind.INVALIDPARAMETER))
        assertEquals(VizioPairResult.Failed, vizioFinishResult(VizioControllerOutputKind.ERROR, VizioFailureKind.TRANSPORT))
        assertEquals(VizioPairResult.Failed, vizioFinishResult(VizioControllerOutputKind.REQUEST, null))
    }
}