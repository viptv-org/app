package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteDecodeOffMainTest {
    private class Store : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private class RecordingDispatcher(private val threadName: String) : CoroutineDispatcher() {
        private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, threadName) }
        val runThreads = ConcurrentLinkedQueue<String>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            executor.execute { runThreads.add(Thread.currentThread().name); block.run() }
        }
        fun close() { executor.shutdownNow() }
    }

    @Test fun callParsesAndRewritesImagesOffTheMainThread() {
        val main = RecordingDispatcher("fixture-main")
        val worker = RecordingDispatcher("fixture-worker")
        Dispatchers.setMain(main)
        try {
            runBlocking {
                val client = HttpClient(MockEngine {
                    respond("""{"cards":[{"logo":"/api/images/simkl/posters/hash.webp"}],"subtitle":"ok"}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }) { configureTransport() }
                val remote = object : RemoteConnection("https://watch.example", Store(), client, browserMode = false) {
                    override val workerDispatcher = worker
                }
                try {
                    withContext(Dispatchers.Main) {
                        val result = remote.call("/api/example").jsonObject
                        assertEquals("https://watch.example/api/images/simkl/posters/hash.webp",
                            result.getValue("cards").jsonArray.single().jsonObject.getValue("logo").jsonPrimitive.content)
                    }
                } finally { remote.close() }
            }
        } finally {
            Dispatchers.resetMain()
            main.close()
            worker.close()
        }
        assertTrue(worker.runThreads.isNotEmpty(), "the parse and image rewrite did not reach the worker dispatcher")
        assertEquals(setOf("fixture-worker"), worker.runThreads.toSet(), "the parse and image rewrite ran off the main thread")
    }
}