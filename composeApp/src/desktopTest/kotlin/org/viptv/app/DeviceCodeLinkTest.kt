package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class DeviceCodeLinkTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun controller(scope: CoroutineScope, screen: Screen, origin: String = "https://watch.example", local: Boolean = false): ApplicationController {
        val controller = ApplicationController(scope, store)
        controller.connection = RemoteConnection(origin, store, HttpClient(MockEngine { error("A link must not cause a request") }), browserMode = true)
        controller.mutable.value = ApplicationState(screen = screen, localMode = local)
        return controller
    }

    @Test fun incomingLinkPreservesThePortThroughSignInAndOnlyDeliversToThatOrigin() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = controller(scope, Screen.Connect, origin = "https://watch.example:18445")
            controller.offerDeviceCodeLink("https://watch.example:18445/device?code=00428701")
            controller.deliverDeviceCode()
            assertEquals("https://watch.example:18445", controller.deviceLink.value?.origin)
            assertEquals("00428701", controller.deviceLink.value?.code)
            assertEquals(Screen.Connect, controller.state.value.screen)
            controller.mutable.value = ApplicationState(screen = Screen.Home)
            controller.deliverDeviceCode()
            assertEquals(Screen.Settings, controller.state.value.screen)
            val otherPort = controller(scope, Screen.Home, origin = "https://watch.example")
            otherPort.offerDeviceCodeLink("https://watch.example:18445/device?code=00428701")
            otherPort.deliverDeviceCode()
            assertNull(otherPort.deviceLink.value)
            assertEquals(Screen.Home, otherPort.state.value.screen)
        } finally { scope.cancel() }
    }

    @Test fun incomingLinksRejectAmbiguousAuthoritiesPathsAndCodes() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = controller(scope, Screen.Connect)
            for (url in listOf("http://watch.example:18445/device?code=00428701",
                "viptv://watch.example/device?code=00428701", "https://user@watch.example:18445/device?code=00428701",
                "https://watch.example:0/device?code=00428701", "https://watch.example:65536/device?code=00428701",
                "https://watch.example:18445/other?code=00428701", "https://watch.example:18445/device/extra?code=00428701",
                "https://watch.example:18445/device?code=00428701&code=11111111",
                "https://watch.example:18445/device?code=00428701#Intent;package=other;end",
                "https://watch.example:18445/device?code=0042870x")) {
                controller.offerDeviceCodeLink(url)
                assertNull(controller.deviceLink.value, url)
            }
        } finally { scope.cancel() }
    }

    @Test fun onlyAnHttpsServerAndAnEightDigitCodeAreAccepted() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = controller(scope, Screen.Home)
            for ((origin, code) in listOf("http://watch.example" to "12345678", "https://watch.example/x" to "12345678",
                "https://watch.example" to "1234567", "https://watch.example" to "123456789", "https://watch.example" to "1234abcd", "nonsense" to "12345678"))
                controller.offerDeviceCode(origin, code)
            assertNull(controller.deviceLink.value)
            controller.offerDeviceCode("https://watch.example", "12345678")
            assertEquals("12345678", controller.deviceLink.value?.code)
            assertEquals("DeviceCodeLink(code=<redacted>)", controller.deviceLink.value.toString())
        } finally { scope.cancel() }
    }

    @Test fun theLinkWaitsThroughSignInAndProfileChoiceThenOpensAddADeviceOnce() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = controller(scope, Screen.Connect)
            controller.offerDeviceCode("https://watch.example", "12345678")
            for (screen in listOf(Screen.Connect, Screen.Profiles, Screen.ProfilePin)) {
                controller.mutable.value = controller.mutable.value.copy(screen = screen)
                controller.deliverDeviceCode()
                assertEquals(screen, controller.state.value.screen)
                assertFalse(controller.deviceLink.value!!.delivered)
            }
            controller.mutable.value = ApplicationState(screen = Screen.Home)
            controller.deliverDeviceCode()
            assertEquals(Screen.Settings, controller.state.value.screen)
            assertEquals("Add a device", controller.state.value.settingsEntrySection)
            assertTrue(controller.deviceLink.value!!.delivered)
            controller.deliverDeviceCode()
            assertEquals(1, controller.state.value.navBack.count { it.screen == Screen.Home }, "Delivery happens once")
            controller.consumeDeviceCode()
            assertNull(controller.deviceLink.value)
        } finally { scope.cancel() }
    }

    @Test fun aLinkForAnotherServerOrLocalModeIsDroppedWithoutNavigating() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val other = controller(scope, Screen.Home)
            other.offerDeviceCode("https://another.example", "12345678")
            other.deliverDeviceCode()
            assertNull(other.deviceLink.value); assertEquals(Screen.Home, other.state.value.screen)
            val local = controller(scope, Screen.Home, local = true)
            local.offerDeviceCode("https://watch.example", "12345678")
            local.deliverDeviceCode()
            assertNull(local.deviceLink.value); assertEquals(Screen.Home, local.state.value.screen)
            val sameServerAnyCase = controller(scope, Screen.Home)
            sameServerAnyCase.offerDeviceCode("https://WATCH.example", "12345678")
            sameServerAnyCase.deliverDeviceCode()
            assertEquals(Screen.Settings, sameServerAnyCase.state.value.screen)
        } finally { scope.cancel() }
    }
}
