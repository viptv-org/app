package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class BrowserSettingsBudgetTest {
    @Test fun browserAccountEntryDoesNotLoadUnrelatedAddonOrPreferenceData(): Unit = runBlocking {
        val requests = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val remote = RemoteConnection("https://watch.example",store,HttpClient(MockEngine { request ->
            requests += request.url.encodedPath
            assertEquals("/api/account/devices",request.url.encodedPath)
            respond(accountFixture,headers = headersOf(HttpHeaders.ContentType,"application/json"))
        }),browserMode = true)
        try {
            val controller = ApplicationController(scope,store)
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Home,selectedProfile = "1")
            controller.navigate(Screen.Settings); controller.job?.join()
            assertTrue(requests.isEmpty())
            controller.refreshAccountDevices(); controller.job?.join()
            assertEquals(listOf("/api/account/devices"),requests)
        } finally { scope.cancel(); remote.close() }
    }
}
