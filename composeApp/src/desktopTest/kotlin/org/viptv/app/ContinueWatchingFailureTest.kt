@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.ContinueWatchingHost.Companion.card
import org.viptv.app.components.ListPageState
import org.viptv.app.components.PAGE_STATE_TAG
import org.viptv.app.features.continuewatching.CONTINUE_EMPTY_ACTION
import org.viptv.app.features.continuewatching.CONTINUE_EMPTY_MESSAGE
import org.viptv.app.features.continuewatching.CONTINUE_EMPTY_TITLE
import org.viptv.app.features.continuewatching.CONTINUE_FAILED_FALLBACK
import org.viptv.app.features.continuewatching.CONTINUE_FAILED_TITLE
import org.viptv.app.features.continuewatching.CONTINUE_RETRY_ACTION
import org.viptv.app.features.continuewatching.ContinueWatchingPage
import org.viptv.app.features.continuewatching.continueWatchingFailureMessage
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Continue Watching's failure is one block: the app-wide error slot never claims the page failed to load, the request's
// message appears once as the body, and the block is the same one every list page draws.
class ContinueWatchingFailureTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private data class Layout(val name: String, val width: Int, val height: Int, val television: Boolean = false)

    private val layouts = listOf(
        Layout("phone", 390, 844),
        Layout("tablet", 834, 1112),
        Layout("desktop", 1280, 800),
        Layout("web", 1440, 900),
        Layout("tv", 1920, 1080, television = true))

    private val message = "The connection dropped."

    private fun page(state: ContinueWatchingPage, layout: Layout): @Composable () -> Unit = {
        ViptvTheme(layout.television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                ContinueWatchingSegment(state, television = layout.television, play = {}, open = {}, more = null, retry = {}, home = {})
            }
        }
    }

    private class Frame(width: Int, height: Int, content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) { content() }
        suspend fun settle(frames: Int = 40) { repeat(frames) { scene.render(frame++ * 16_666_667L).close(); yield() } }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun texts(): List<String> = nodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        }
        fun block(): SemanticsNode? = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == PAGE_STATE_TAG }
        override fun close() = scene.close()
    }

    @Test fun theRequestBodyIsTheOnlyFailureLine() {
        assertEquals(CONTINUE_FAILED_FALLBACK, continueWatchingFailureMessage(null), "the fallback is copy.md's line")
        assertEquals(CONTINUE_FAILED_FALLBACK, continueWatchingFailureMessage("  "))
        assertEquals(message, continueWatchingFailureMessage(message))
        assertEquals(ListPageState.Failed, ContinueWatchingPage(emptyList(), loaded = false, error = message).state)
        assertEquals(ListPageState.Empty, ContinueWatchingPage(emptyList(), loaded = true).state)
    }

    @Test fun everyLayoutDrawsTheSharedBlockWithTheRequestMessageOnce() = kotlinx.coroutines.runBlocking {
        val failures = mutableListOf<String>()
        for (layout in layouts) for (failed in listOf(true, false)) {
            val page = if (failed) ContinueWatchingPage(emptyList(), loaded = false, error = message)
                else ContinueWatchingPage(emptyList(), loaded = true)
            val frame = Frame(layout.width, layout.height, page(page, layout))
            try {
                frame.settle()
                val label = "${layout.name}/${if (failed) "failed" else "empty"}"
                val title = if (failed) CONTINUE_FAILED_TITLE else CONTINUE_EMPTY_TITLE
                val body = if (failed) message else CONTINUE_EMPTY_MESSAGE
                val action = if (failed) CONTINUE_RETRY_ACTION else CONTINUE_EMPTY_ACTION
                if (!frame.texts().any { it.contains(title) }) failures += "$label: '$title' is not on the page"
                if (frame.texts().count { it.contains(body) } != 1) {
                    failures += "$label: '$body' reads ${frame.texts().count { it.contains(body) }} times"
                }
                if (!frame.texts().any { it.contains(action) }) failures += "$label: the '$action' action is not on the page"
                val block = frame.block()
                if (block == null) { failures += "$label: no shared page state block"; continue }
                val centre = block.boundsInRoot.left + block.boundsInRoot.width / 2
                if (abs(centre - layout.width / 2f) > 1f) failures += "$label: the block is off-centre at $centre of ${layout.width}"
            } finally { frame.close() }
        }
        assertEquals(emptyList(), failures, "Continue Watching's page states on every layout")
    }

    @Test fun anActionFailureNeverClaimsThePageFailedToLoad() = kotlinx.coroutines.runBlocking {
        val failures = mutableListOf<String>()
        for (layout in layouts) {
            val frame = Frame(layout.width, layout.height, page(ContinueWatchingPage((1..4).map(::card), loaded = true,
                error = message), layout))
            try {
                frame.settle()
                if (frame.texts().any { it.contains(CONTINUE_FAILED_TITLE) }) {
                    failures += "${layout.name}: an unrelated failure became the page state"
                }
                if (frame.texts().count { it.contains(message) } > 0) {
                    failures += "${layout.name}: an unrelated failure is printed above the page"
                }
                if (!frame.texts().any { it.contains("Show 01") }) failures += "${layout.name}: the cards left the page"
            } finally { frame.close() }
        }
        assertEquals(emptyList(), failures, "the page keeps its cards and stays quiet about an action's failure")
    }

    @Test fun render() = kotlinx.coroutines.runBlocking {
        val dir = System.getenv("VIPTV_1037_RENDER_DIR")?.let(::File)?.also { it.deleteRecursively(); it.mkdirs() }
            ?: return@runBlocking
        for (layout in layouts) for ((name, page) in listOf(
            "failed" to ContinueWatchingPage(emptyList(), loaded = false, error = message),
            "empty" to ContinueWatchingPage(emptyList(), loaded = true),
            "action-error" to ContinueWatchingPage((1..4).map(::card), loaded = true, error = message))) {
            val frame = Frame(layout.width, layout.height, page(page, layout))
            try {
                frame.settle()
                frame.scene.render(1_000_000_000L).use { image ->
                    File(dir, "${layout.name}-$name.png").writeBytes(image.encodeToData()!!.bytes)
                }
            } finally { frame.close() }
        }
    }
}