package org.viptv.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.viptv.app.features.settings.VizioNoAccess
import org.viptv.app.features.settings.VizioNoLocalNetwork
import org.viptv.app.features.settings.VizioPairResult
import org.viptv.app.features.settings.VizioSetupEngine
import org.viptv.app.features.settings.VizioSetupStep
import org.viptv.app.features.settings.VizioTv
import org.viptv.app.features.settings.vizioManualOrigin
import org.viptv.app.features.settings.WatchOnTvSetupController
import org.viptv.engine.wire.VizioPairingChallenge
import kotlin.test.*

class WatchOnTvSetupControllerTest {

    private class FakeEngine : VizioSetupEngine {
        val searchedLimits = mutableListOf<Int>()
        val probedOrigins = mutableListOf<String>()
        val beganTvs = mutableListOf<VizioTv>()
        val finishedPins = mutableListOf<String>()
        val cancelledOrigins = mutableListOf<String>()
        var searchResults = emptyList<VizioTv>()
        var probeFn: (String) -> VizioTv? = { null }
        var beginFn: (VizioTv) -> VizioPairingChallenge? = { VizioPairingChallenge(1, 42L) }
        var finishFn: (String) -> VizioPairResult = { VizioPairResult.Paired }
        var searchThrow: Throwable? = null
        var probeThrow: Throwable? = null
        var openedSettings = 0

        override suspend fun search(limit: Int, onResult: (VizioTv) -> Unit) {
            searchThrow?.let { throw it }
            searchedLimits += limit
            searchResults.take(limit).forEach(onResult)
        }

        override suspend fun probe(origin: String): VizioTv? {
            probeThrow?.let { throw it }
            probedOrigins += origin
            return probeFn(origin)
        }

        override suspend fun beginPair(tv: VizioTv): VizioPairingChallenge? {
            beganTvs += tv
            return beginFn(tv)
        }

        override suspend fun finishPair(tv: VizioTv, challenge: VizioPairingChallenge, pin: String): VizioPairResult {
            finishedPins += pin
            return finishFn(pin)
        }

        override suspend fun cancelPair(tv: VizioTv) { cancelledOrigins += tv.origin }

        override fun openLocalNetworkSettings() { openedSettings++ }
    }

    private val livingRoom = VizioTv("Living Room", "https://192.168.1.20:7345")
    private val den = VizioTv("Den", "https://10.0.0.8:9000")

    @Test fun searchPublishesResultsProgressivelyAndPassesTheBound() = runTest {
        val engine = FakeEngine().apply { searchResults = listOf(livingRoom, den) }
        val controller = controller(engine, this)
        controller.beginSearch()
        assertEquals(VizioSetupStep.Searching, controller.step)
        assertTrue(controller.searching)
        advanceUntilIdle()
        assertEquals(listOf(livingRoom, den), controller.results)
        assertEquals(16, engine.searchedLimits.single())
        assertFalse(controller.searching)
    }

    @Test fun anEmptySearchHasNoResultsAndNoError() = runTest {
        val engine = FakeEngine().apply { searchResults = emptyList() }
        val controller = controller(engine, this)
        controller.beginSearch(); advanceUntilIdle()
        assertEquals(VizioSetupStep.Searching, controller.step)
        assertEquals(listOf<VizioTv>(), controller.results)
        assertNull(controller.searchError)
    }

    @Test fun noLocalNetworkSaysToConnectToWifi() = runTest {
        val engine = FakeEngine().apply { searchThrow = VizioNoLocalNetwork() }
        val controller = controller(engine, this)
        controller.beginSearch(); advanceUntilIdle()
        assertEquals("Connect this phone to the same Wi-Fi as your TV.", controller.searchError)
    }

    @Test fun aDeniedLocalNetworkPermissionOpensTheRecoveryStep() = runTest {
        val engine = FakeEngine().apply { searchThrow = VizioNoAccess() }
        val controller = controller(engine, this)
        controller.beginSearch(); advanceUntilIdle()
        assertEquals(VizioSetupStep.NoAccess, controller.step)
        assertNull(controller.searchError)
    }

    @Test fun aDeniedLocalNetworkPermissionCannotBeBypassedByManualEntry() = runTest {
        val engine = FakeEngine().apply { probeThrow = VizioNoAccess() }
        val controller = controller(engine, this)
        controller.openManual()
        controller.manualChanged("192.168.1.20")
        controller.connectManual(); advanceUntilIdle()
        assertEquals(VizioSetupStep.NoAccess, controller.step)
        assertNull(controller.manualError)
    }

    @Test fun openLocalNetworkSettingsDelegatesToTheEngine() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine, this)
        controller.openLocalNetworkSettings()
        assertEquals(1, engine.openedSettings)
    }

    @Test fun choosingTvBeginsPairingWithAChallenge() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine, this)
        controller.choose(livingRoom); advanceUntilIdle()
        assertEquals(VizioSetupStep.Pin(livingRoom), controller.step)
        assertTrue(controller.pinReady)
        assertEquals(listOf(livingRoom), engine.beganTvs)
    }

    @Test fun aFourDigitPinSubmitsAutomaticallyAndPairs() = runTest {
        val engine = FakeEngine()
        val paired = mutableListOf<VizioTv>()
        val controller = controller(engine, this, paired)
        controller.choose(livingRoom); advanceUntilIdle()
        controller.pinChanged("1"); controller.pinChanged("12"); controller.pinChanged("123")
        assertEquals(emptyList(), engine.finishedPins)
        controller.pinChanged("1234"); advanceUntilIdle()
        assertEquals(listOf("1234"), engine.finishedPins)
        assertEquals(listOf(livingRoom), paired)
        assertEquals(VizioSetupStep.Done(livingRoom), controller.step)
    }

    @Test fun aRejectedPinStaysOnThePinStepWithEditableInput() = runTest {
        val engine = FakeEngine().apply { finishFn = { VizioPairResult.Rejected } }
        val paired = mutableListOf<VizioTv>()
        val controller = controller(engine, this, paired)
        controller.choose(livingRoom); advanceUntilIdle()
        controller.pinChanged("1"); controller.pinChanged("12"); controller.pinChanged("123"); controller.pinChanged("1234")
        advanceUntilIdle()
        assertEquals(VizioSetupStep.Pin(livingRoom), controller.step)
        assertEquals("Incorrect PIN. Try again.", controller.pinError)
        assertTrue(paired.isEmpty())
    }

    @Test fun newPinCancelsThePendingPairFirst() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine, this)
        controller.choose(livingRoom); advanceUntilIdle()
        controller.newPin(); advanceUntilIdle()
        assertEquals(listOf(livingRoom.origin), engine.cancelledOrigins)
        assertEquals(listOf(livingRoom, livingRoom), engine.beganTvs)
    }

    @Test fun manualConnectProbesTheAddressAndThenPairs() = runTest {
        val engine = FakeEngine().apply { probeFn = { livingRoom } }
        val controller = controller(engine, this)
        controller.openManual()
        controller.manualChanged("192.168.1.20")
        controller.connectManual(); advanceUntilIdle()
        assertEquals(listOf("https://192.168.1.20:7345"), engine.probedOrigins)
        assertEquals(VizioSetupStep.Pin(livingRoom), controller.step)
        assertEquals(listOf(livingRoom), engine.beganTvs)
    }

    @Test fun anUnreachableManualAddressReportsAnErrorAndStaysPut() = runTest {
        val engine = FakeEngine().apply { probeFn = { null } }
        val controller = controller(engine, this)
        controller.openManual()
        controller.manualChanged("192.168.1.99")
        controller.connectManual(); advanceUntilIdle()
        assertEquals(VizioSetupStep.Manual, controller.step)
        assertNotNull(controller.manualError)
        assertTrue(engine.beganTvs.isEmpty())
    }

    @Test fun cancelFromPinReturnsToSearch() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine, this)
        controller.choose(livingRoom); advanceUntilIdle()
        controller.cancelPin(); advanceUntilIdle()
        assertEquals(VizioSetupStep.Searching, controller.step)
        assertEquals(listOf(livingRoom.origin), engine.cancelledOrigins)
    }

    @Test fun pairAgainStartsThePinStepAndCancelLeavesSetup() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine, this)
        var left = 0
        controller.pairAgain(livingRoom) { left++ }
        assertEquals(VizioSetupStep.Pin(livingRoom), controller.step)
        advanceUntilIdle()
        assertTrue(controller.pinReady)
        assertEquals(listOf(livingRoom), engine.beganTvs)
        controller.cancelPin(); advanceUntilIdle()
        assertEquals(1, left)
    }

    @Test fun pairAgainSuccessReplacesThePairing() = runTest {
        val engine = FakeEngine()
        val paired = mutableListOf<VizioTv>()
        val controller = controller(engine, this, paired)
        controller.pairAgain(livingRoom) { }
        advanceUntilIdle()
        controller.pinChanged("1234"); advanceUntilIdle()
        assertEquals(listOf("1234"), engine.finishedPins)
        assertEquals(listOf(livingRoom), paired)
        assertEquals(VizioSetupStep.Done(livingRoom), controller.step)
    }

    @Test fun manualOriginAcceptsPrivateLocalIpv4Addresses() {
        assertEquals("https://10.0.0.8:7345", vizioManualOrigin("10.0.0.8"))
        assertEquals("https://192.168.1.20:7345", vizioManualOrigin("192.168.1.20"))
        assertEquals("https://192.168.1.20:9000", vizioManualOrigin("192.168.1.20:9000"))
        assertEquals("https://172.16.0.1:7345", vizioManualOrigin(" https://172.16.0.1 "))
        assertEquals("https://169.254.1.2:7345", vizioManualOrigin("169.254.1.2"))
    }

    @Test fun manualOriginRejectsNonLocalOrMalformedAddresses() {
        assertNull(vizioManualOrigin("8.8.8.8"))
        assertNull(vizioManualOrigin("192.168.1.999"))
        assertNull(vizioManualOrigin("192.168.1.20:80"))
        assertNull(vizioManualOrigin("not-an-ip"))
        assertNull(vizioManualOrigin("192.168.1.20:7345:9000"))
        assertNull(vizioManualOrigin(""))
    }

    private fun controller(engine: FakeEngine, scope: CoroutineScope, paired: MutableList<VizioTv> = mutableListOf()) =
        WatchOnTvSetupController(engine, scope) { paired += it }
}