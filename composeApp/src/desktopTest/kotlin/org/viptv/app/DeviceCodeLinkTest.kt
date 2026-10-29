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
            assertEquals("Watch on TV", controller.state.value.settingsEntrySection)
            assertTrue(controller.deviceLink.value!!.delivered)
            controller.deliverDeviceCode()
            assertEquals(1, controller.state.value.navBack.count { it == Screen.Home }, "Delivery happens once")
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
