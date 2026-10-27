package org.viptv.app.state

import android.os.Bundle
import androidx.activity.ComponentActivity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * app#75: Android's activity lifecycle drives the app's foreground hook. Start and
 * stop are appForeground(true/false), once per transition, and a return checks
 * the server for changes made elsewhere (app#2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidForegroundTest {
    /** Installs the hook in onCreate, as MainActivity does with its ApplicationModel's controller. */
    class HostActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            lifecycle.addObserver(controller!!.foregroundObserver(returned = { returns++ }))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val time = TestTimeSource()
    private val requests = mutableListOf<String>()
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val revisions = """{"library":"4","configuration":"2","addons":"a1","policy":"0","preferences":"1","profiles":"7"}"""
    private val client = HttpClient(MockEngine { request ->
        synchronized(requests) { requests += request.url.encodedPath }
        if (request.url.encodedPath != "/api/profiles/12/revisions") error("Unexpected ${request.url.encodedPath}")
        respond(revisions, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }) { configureTransport() }

    @After fun stop() {
        controller?.let { it.freshnessWatcher?.cancel(); it.connection?.close() }
        controller = null; returns = 0
        scope.cancel()
    }

    private fun signedInController() = ApplicationController(scope, store, freshness = Freshness(time)).also {
        it.connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
        it.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "12")
        it.freshness.homeBuilt(mapOf("library" to "4", "configuration" to "2", "addons" to "a1", "policy" to "0", "preferences" to "1", "profiles" to "7"))
    }

    private fun ApplicationController.settle() = runBlocking {
        while (true) { val running = quietJob ?: return@runBlocking; running.join(); if (quietJob === running) return@runBlocking }
    }

    private fun revisionChecks() = synchronized(requests) { requests.count { it == "/api/profiles/12/revisions" } }

    @Test fun startStopAndRestartDriveTheForegroundHookOncePerTransition() {
        val app = signedInController().also { controller = it }
        // Before the activity starts nothing has run: no watcher, no check.
        val activity = Robolectric.buildActivity(HostActivity::class.java)

        activity.create().start().resume()
        app.settle()
        val firstWatcher = assertNotNull(app.freshnessWatcher, "appForeground(true) on start runs the Home watcher")
        assertEquals(true, app.inFront.value, "lease renewal runs in front")
        assertEquals(1, returns, "one foreground return")
        // Start and resume of the same return make one revisions check (app#18).
        assertEquals(1, revisionChecks(), "a start checks the server for changes made elsewhere")

        time += 3.seconds
        activity.pause().stop().saveInstanceState(Bundle())
        app.settle()
        assertNull(app.freshnessWatcher, "appForeground(false) on stop ends the watcher")
        assertEquals(false, app.inFront.value, "behind: lease renewal waits while playback is paused")
        assertEquals(false, app.freshness.active())
        assertEquals(1, revisionChecks(), "going behind checks nothing")

        time += 3.seconds
        activity.restart().start().resume()
        app.settle()
        val secondWatcher: Job = assertNotNull(app.freshnessWatcher, "appForeground(true) again on restart")
        assertNotSame(firstWatcher, secondWatcher)
        assertEquals(true, app.inFront.value)
        assertEquals(2, returns, "exactly one return per start")
        assertEquals(2, revisionChecks(), "the return checks the server again")

        activity.pause().stop().destroy()
        assertNull(app.freshnessWatcher)
        assertEquals(false, app.inFront.value)
    }

    @Test fun aPauseWithoutStopFreshensButIsNoForegroundTransition() {
        val app = signedInController().also { controller = it }
        val activity = Robolectric.buildActivity(HostActivity::class.java).create().start().resume()
        app.settle()
        val watcher = app.freshnessWatcher

        // A translucent activity on top pauses and resumes without stop and start.
        time += 3.seconds
        activity.pause().resume()
        app.settle()
        assertEquals(1, returns, "no new foreground return")
        assertEquals(watcher, app.freshnessWatcher, "the watcher keeps running")
        assertEquals(2, revisionChecks(), "the resume still checks for changes")
        activity.pause().stop().destroy()
    }

    companion object {
        var controller: ApplicationController? = null
        var returns = 0
    }
}
