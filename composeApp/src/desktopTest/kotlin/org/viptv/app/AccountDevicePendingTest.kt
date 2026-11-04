package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class AccountDevicePendingTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun devices(vararg ids: String) = AccountDevices(
        AccountIdentity("a", "owner", "Owner", "owner"),
        ids.map { AccountDevice(it, it, "phone", 1, current = false, can_sign_out = true) }, can_sign_out_all = true)

    private inner class Fixture(scope: CoroutineScope, scheduler: TestCoroutineScheduler, reply: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val client = HttpClient(MockEngine) {
            engine { dispatcher = StandardTestDispatcher(scheduler); addHandler(reply) }
            configureTransport()
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = true)
        val controller = ApplicationController(scope, store).also {
            it.connection = remote
            it.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1",
                accountDevices = devices("one", "two"))
        }
        fun close() { controller.mutationJobs.values.toList().forEach { it.cancel() }; remote.close() }
    }

    @Test fun signingOutADeviceLeavesTheRowAtOnceAndDropsTheRepeat() = runTest {
        val release = CompletableDeferred<Unit>()
        var writes = 0
        val f = Fixture(this, testScheduler) { _ -> writes++; release.await(); respond("{}", headers = json) }
        try {
            val target = MutationTarget(MutationKind.Account, signOutDeviceTarget("one"))
            f.controller.signOutDevice("one")
            runCurrent()
            assertEquals(listOf("two"), f.controller.state.value.accountDevices?.devices?.map { it.id },
                "the row leaves in the frame of the press")
            assertTrue(f.controller.state.value.mutations.isPending(target))
            f.controller.signOutDevice("one")
            runCurrent()
            assertEquals(1, writes, "a repeat press on the pending row is dropped")
            release.complete(Unit)
            advanceUntilIdle()
            assertFalse(f.controller.state.value.mutations.isPending(target))
        } finally { release.complete(Unit); f.close() }
    }

    @Test fun aRefusedSignOutPutsTheRowBackWithItsMessageAndRetries() = runTest {
        var writes = 0
        val f = Fixture(this, testScheduler) { _ ->
            writes++
            if (writes == 1) respond("nope", HttpStatusCode.InternalServerError) else respond("{}", headers = json)
        }
        try {
            val target = MutationTarget(MutationKind.Account, signOutDeviceTarget("one"))
            f.controller.signOutDevice("one")
            advanceUntilIdle()
            assertEquals(listOf("one", "two"), f.controller.state.value.accountDevices?.devices?.map { it.id })
            assertNotNull(f.controller.state.value.mutations.failure(target))
            f.controller.retryMutation(target)
            advanceUntilIdle()
            assertEquals(2, writes)
            assertEquals(listOf("two"), f.controller.state.value.accountDevices?.devices?.map { it.id })
            assertNull(f.controller.state.value.mutations.failure(target))
        } finally { f.close() }
    }
}
