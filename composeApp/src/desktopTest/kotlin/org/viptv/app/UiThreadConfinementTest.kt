package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Collections
import org.junit.Rule
import kotlin.test.*

/** app#27: scene tests must not race Compose's Swing-thread layout callbacks. */
@OptIn(ExperimentalComposeUiApi::class)
class UiThreadConfinementTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun layoutCallbacksOfASlowFrameNeverRunBesideTheTest() {
        val testThread = Thread.currentThread()
        val callbackThreads = Collections.synchronizedSet(mutableSetOf<Thread>())
        val uncaught = Collections.synchronizedList(mutableListOf<Throwable>())
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
        try {
            var tick by mutableStateOf(0)
            val scene = ImageComposeScene(200, 2000, Density(1f)) {
                Column {
                    Box(Modifier.offset(x = (tick % 7).dp).onLayoutRectChanged(0, 0) { callbackThreads += Thread.currentThread() }.size(1.dp))
                    repeat(300) { i -> if ((i + tick) % 3 != 0) Box(Modifier.offset(x = (tick % 7).dp).size(1.dp)) }
                    // Placement outlasts the 16 ms after which Compose posts its
                    // rect callbacks to the Swing event thread.
                    Box(Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { if (tick > 0) Thread.sleep(25); placeable.place(0, tick % 5) }
                    }.size(1.dp))
                    repeat(300) { i -> if ((i + tick) % 2 == 0) Box(Modifier.offset(y = (tick % 3).dp).size(1.dp)) }
                }
            }
            try {
                repeat(8) { frame -> tick++; scene.render(frame * 16_666_667L).close() }
            } finally { scene.close() }
        } finally { Thread.setDefaultUncaughtExceptionHandler(previous) }
        assertEquals(setOf(testThread), callbackThreads.toSet(), "rect callbacks ran beside the test thread")
        assertEquals(emptyList(), uncaught.map(Throwable::toString))
    }

    @Test fun everyDesktopSceneTestRunsOnTheSwingEventThread() {
        val root = File("src/desktopTest/kotlin")
        assertTrue(root.isDirectory, "run from composeApp: ${File(".").absolutePath}")
        val rule = "@get:Rule val uiThread = " + "UiThreadRule()"
        val unconfined = root.walkTopDown().filter { it.extension == "kt" }
            .filter { file -> file.readText().let { "ImageComposeScene(" in it && rule !in it } }
            .map { it.path }.sorted().toList()
        assertEquals(emptyList(), unconfined, "ImageComposeScene tests need $rule (app#27)")
    }
}
